class FaceAuth {
    constructor(provider = 'LOCAL') {
        this.provider = provider;
        this.video = document.getElementById('cameraFeed');
        this.canvas = document.getElementById('faceCanvas');
        this.ctx = this.canvas.getContext('2d');
        this.stream = null;
        this.isProcessing = false;
        this.faceDetected = false;
        this.livenessPassed = false;
        this.previousDetections = [];
        this.authorizedCount = 0;
        this.unauthorizedCount = 0;
        this.verificationFrames = 0; // Number of consecutive frames with successful auth
        this.requiredFrames = 5; // Require 5 stable frames for final auth
        this.authenticated = false;
    }

    async initialize() {
        try {
            this.stream = await navigator.mediaDevices.getUserMedia({
                video: {
                    width: 640,
                    height: 480,
                    facingMode: 'user'
                },
                audio: false
            });
            this.video.srcObject = this.stream;
            await this.video.play();
            this.startLivenessCheck();
            return true;
        } catch (err) {
            console.error('Error accessing camera:', err);
            return false;
        }
    }

    startLivenessCheck() {
        // CRITICAL FIX: Rate-limited verification (1 request per 2s, NOT 10/second!)
        // Prevents: Circuit breaker failures, AWS cost spikes, poor recognition
        this.livenessCheckInterval = setInterval(() => {
            if (!this.isProcessing && !this.authenticated) {
                this.processFrame();
            }
        }, 2000); // Changed from 100ms to 2000ms (2 seconds)
    }

    async processFrame() {
        if (!this.stream || this.isProcessing) return;

        this.isProcessing = true;

        // Ensure canvas matches video dimensions
        if (this.canvas.width !== this.video.videoWidth) {
            this.canvas.width = this.video.videoWidth;
            this.canvas.height = this.video.videoHeight;
        }

        // Draw current video frame to canvas
        this.ctx.drawImage(this.video, 0, 0, this.canvas.width, this.canvas.height);

        try {
            // Capture frame as blob
            const blob = await new Promise(resolve => this.canvas.toBlob(resolve, 'image/jpeg', 0.6));
            if (!blob) throw new Error("Could not capture frame");

            const formData = new FormData();
            formData.append('image', blob, 'frame.jpg');

            const token = localStorage.getItem('authToken');
            const response = await fetch(`/api/face/verify?provider=${this.provider}&live=true`, {
                method: 'POST',
                headers: {
                    'Authorization': 'Bearer ' + token
                },
                body: formData
            });

            if (response.status === 429) {
                console.debug('Server is processing another frame, skipping...');
                this.isProcessing = false;
                return;
            }

            if (response.ok) {
                const result = await response.json();
                this.handleVerificationResult(result);
            }
        } catch (error) {
            console.warn('Frame verification skipped:', error.message);
        }

        this.isProcessing = false;
    }

    handleVerificationResult(result) {
        // Clear previous drawing (keep the image)
        this.ctx.drawImage(this.video, 0, 0, this.canvas.width, this.canvas.height);

        // Root-level authorization check (Production contract)
        if (result.authorized && result.user) {
            this.faceDetected = true;
            this.updateUI('livenessPassed');
            this.completeAuthentication(result.user);
            return;
        }

        if (!result.detections || result.detections.length === 0) {
            this.updateUI('noFace');
            this.authorizedCount = 0;
            this.unauthorizedCount = 0;
            this.updateCounter();
            return;
        }

        this.faceDetected = true;
        this.updateUI('faceDetected');

        let currentAuthorized = 0;
        let currentUnauthorized = 0;

        result.detections.forEach(det => {
            // Motion Tracking Logic
            const prevDet = this.previousDetections.find(prev => {
                const dx = Math.abs(prev.x - det.x);
                const dy = Math.abs(prev.y - det.y);
                return dx < 50 && dy < 50; // Threshold for same person
            });

            if (prevDet) {
                const movement = Math.sqrt(Math.pow(det.x - prevDet.x, 2) + Math.pow(det.y - prevDet.y, 2));
                det.moving = movement > 5; // Move more than 5 pixels
            } else {
                det.moving = false;
            }

            const isAuthorized = det.authorized && det.isLive;

            if (isAuthorized) currentAuthorized++;
            else currentUnauthorized++;

            // Draw bounding box
            this.ctx.strokeStyle = isAuthorized ? '#28a745' : '#dc3545';
            this.ctx.lineWidth = 4;
            this.ctx.setLineDash(det.isLive ? [] : [10, 5]);
            this.ctx.strokeRect(det.x, det.y, det.width, det.height);
            this.ctx.setLineDash([]);

            // Drawing pulse for movement
            if (det.moving) {
                this.ctx.strokeStyle = '#007bff';
                this.ctx.lineWidth = 2;
                this.ctx.strokeRect(det.x - 5, det.y - 5, det.width + 10, det.height + 10);
            }

            // SECURE MASKING for unauthorized persons
            if (!isAuthorized) {
                this.ctx.fillStyle = 'rgba(0, 0, 0, 0.85)';
                this.ctx.fillRect(det.x, det.y, det.width, det.height);

                this.ctx.fillStyle = '#ffc107';
                this.ctx.font = 'bold 16px "Inter", sans-serif';
                this.ctx.textAlign = 'center';
                this.ctx.fillText("UNAUTHORIZED", det.x + det.width / 2, det.y + det.height / 2);
                this.ctx.textAlign = 'start';
            }

            // Draw analytics labels
            const name = det.authorized ? (det.user.name || 'User') : 'Unknown';
            const emotion = det.emotion || 'Neutral';
            const liveness = det.isLive ? 'LIVE' : 'SPOOF?';
            const moveTxt = det.moving ? ' | ACTIVE' : '';
            const label = `${name} | ${emotion}${moveTxt}`;

            this.ctx.font = '700 14px "Inter", sans-serif';
            const textWidth = this.ctx.measureText(label).width;

            this.ctx.fillStyle = isAuthorized ? 'rgba(40, 167, 69, 0.95)' : 'rgba(220, 53, 69, 0.95)';
            this.ctx.fillRect(det.x, det.y - 45, textWidth + 20, 30);

            // Draw text
            this.ctx.fillStyle = '#ffffff';
            this.ctx.fillText(label, det.x + 10, det.y - 25);

            // Liveness - REAL feedback from backend
            if (isAuthorized && det.isLive) {
                this.verificationFrames++;
                if (this.verificationFrames >= this.requiredFrames && !this.authenticated) {
                    this.authenticated = true;
                    this.livenessPassed = true;
                    this.updateUI('livenessPassed');
                    this.completeAuthentication(det.user);
                }
            } else if (isAuthorized && !det.isLive) {
                this.verificationFrames = 0; // Reset if liveness fails
            }
        });

        this.previousDetections = result.detections;
        this.authorizedCount = currentAuthorized;
        this.unauthorizedCount = currentUnauthorized;
        this.updateCounter();
    }

    updateCounter() {
        const counterEl = document.getElementById('personCounter');
        if (!counterEl) {
            // Create counter element if it doesn't exist
            const authStatus = document.getElementById('faceAuthStatus');
            const newCounter = document.createElement('div');
            newCounter.id = 'personCounter';
            newCounter.className = 'mt-2 small font-weight-bold';
            authStatus.parentNode.insertBefore(newCounter, authStatus.nextSibling);
        }

        const el = document.getElementById('personCounter');
        const total = this.authorizedCount + this.unauthorizedCount;
        el.innerHTML = `
            <span class="text-primary">Total: ${total}</span> | 
            <span class="text-success">Auth: ${this.authorizedCount}</span> | 
            <span class="text-danger">Alerts: ${this.unauthorizedCount}</span>
        `;
    }

    // simulateLivenessForUser removed - now using real backend data

    updateUI(state) {
        const statusEl = document.getElementById('faceAuthStatus');
        const instructionEl = document.getElementById('faceAuthInstruction');

        switch (state) {
            case 'noFace':
                statusEl.textContent = 'Scanning...';
                statusEl.className = 'text-muted';
                instructionEl.textContent = 'Position your face in the camera frame';
                break;
            case 'faceDetected':
                statusEl.textContent = 'Analyzing...';
                statusEl.className = 'text-primary';
                instructionEl.textContent = 'Keep your face steady for verification';
                break;
            case 'livenessPassed':
                statusEl.textContent = 'Identity Verified';
                statusEl.className = 'text-success';
                instructionEl.textContent = 'Access Granted. Welcome!';
                break;
        }
    }

    async completeAuthentication(user) {
        // Stop the video stream
        if (this.stream) {
            this.stream.getTracks().forEach(track => track.stop());
        }

        if (this.livenessCheckInterval) {
            clearInterval(this.livenessCheckInterval);
        }

        // Here you would send the face data to your backend for verification
        // For now, we'll simulate a successful authentication
        setTimeout(() => {
            const modalElement = document.getElementById('faceAuthModal');
            const modalInstance = bootstrap.Modal.getInstance(modalElement);
            if (modalInstance) {
                modalInstance.hide();
            } else {
                modalElement.classList.remove('show', 'd-block');
                document.querySelectorAll('.modal-backdrop').forEach(el => el.remove());
                document.body.classList.remove('modal-open');
            }

            // Show success message
            showAlert('Face authentication successful! Welcome back.', 'success');

            // Note: In a real app, you'd handle the login success here
            // e.g., update UI state or redirect to dashboard

        }, 3000); // 3-second delay to show the "success" state
    }

    stop() {
        if (this.stream) {
            this.stream.getTracks().forEach(track => track.stop());
        }

        if (this.livenessCheckInterval) {
            clearInterval(this.livenessCheckInterval);
        }
    }
}

// Initialize face auth when the page loads
let faceAuth;

document.addEventListener('DOMContentLoaded', () => {
    const faceAuthBtn = document.getElementById('startFaceAuth');
    if (faceAuthBtn) {
        faceAuthBtn.addEventListener('click', startFaceAuthentication);
    }

    const verifyFaceDropdown = document.getElementById('verifyFaceDropdown');
    if (verifyFaceDropdown) {
        verifyFaceDropdown.addEventListener('click', (e) => {
            e.preventDefault();
            startFaceAuthentication();
        });
    }

    // Close button handler
    const closeButtons = document.querySelectorAll('[data-bs-dismiss="modal"]');
    closeButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            if (faceAuth) {
                faceAuth.stop();
            }
        });
    });
});

async function startFaceAuthentication() {
    // Show the face auth modal
    const modal = new bootstrap.Modal(document.getElementById('faceAuthModal'));
    modal.show();

    // Initialize face auth
    // Initialize face auth with selected provider if available
    const provider = window.selectedProvider || 'LOCAL';
    faceAuth = new FaceAuth(provider);
    const initialized = await faceAuth.initialize();

    if (!initialized) {
        showAlert('Could not access camera. Please ensure you have granted camera permissions.', 'danger');
        modal.hide();
    }
}

// Add this to your existing showAlert function
function showAlert(message, type = 'success') {
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type} alert-dismissible fade show`;
    alertDiv.role = 'alert';
    alertDiv.innerHTML = `
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    `;

    const container = document.getElementById('alertsContainer') || document.body;
    container.prepend(alertDiv);

    // Auto-remove after 5 seconds
    setTimeout(() => {
        alertDiv.remove();
    }, 5000);
}
