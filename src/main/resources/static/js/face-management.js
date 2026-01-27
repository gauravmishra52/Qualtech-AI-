document.addEventListener('DOMContentLoaded', () => {
    // Initial load
    checkAuth();
    loadUsers();
    setupImagePreview();
    setupFormSubmission();

    // Set user name in top bar
    const userName = localStorage.getItem('userName');
    if (userName) {
        document.getElementById('userNameTop').textContent = userName;
    }
});

let stream = null;
let analysisInterval = null;
let isAnalyzing = false;

function showSection(sectionId) {
    // Hide all sections
    document.getElementById('selectionSection').style.display = 'none';
    document.getElementById('managementSection').style.display = 'none';
    document.getElementById('liveSection').style.display = 'none';

    // Stop camera if leaving live section
    if (sectionId !== 'live') {
        stopCamera();
    }

    // Show requested section
    if (sectionId === 'selection') {
        document.getElementById('selectionSection').style.display = 'flex';
    } else if (sectionId === 'management') {
        document.getElementById('managementSection').style.display = 'block';
    } else if (sectionId === 'live') {
        document.getElementById('liveSection').style.display = 'block';
    }
}

// --- Live Analysis Functions ---

async function startCamera() {
    const video = document.getElementById('cameraFeed');
    const placeholder = document.getElementById('cameraPlaceholder');
    const liveIndicator = document.getElementById('liveIndicator');

    try {
        stream = await navigator.mediaDevices.getUserMedia({ video: true });
        video.srcObject = stream;
        video.onloadedmetadata = () => {
            placeholder.style.display = 'none';
            liveIndicator.style.display = 'inline-block';
            startAnalysisLoop();
        };
    } catch (err) {
        console.error("Error accessing camera:", err);
        showAlert("Could not access camera. Please check permissions.", "danger");
    }
}

function stopCamera() {
    if (stream) {
        stream.getTracks().forEach(track => track.stop());
        stream = null;
    }
    if (analysisInterval) {
        clearInterval(analysisInterval);
        analysisInterval = null;
    }

    document.getElementById('cameraFeed').srcObject = null;
    document.getElementById('cameraPlaceholder').style.display = 'block';
    document.getElementById('liveIndicator').style.display = 'none';

    // Clear canvas
    const canvas = document.getElementById('overlayCanvas');
    if (canvas) {
        const ctx = canvas.getContext('2d');
        ctx.clearRect(0, 0, canvas.width, canvas.height);
    }

    // Reset Stats
    resetStats('aws');
    resetStats('azure');
}

function startAnalysisLoop() {
    const video = document.getElementById('cameraFeed');
    const canvas = document.getElementById('overlayCanvas');
    const ctx = canvas.getContext('2d');

    // Set canvas size to match video
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;

    // Analyze every 1000ms (1 FPS) for comparing two services
    analysisInterval = setInterval(async () => {
        if (video.paused || video.ended || isAnalyzing) return;

        isAnalyzing = true;
        // Draw current frame to a temporary canvas to send to backend
        const tempCanvas = document.createElement('canvas');
        tempCanvas.width = video.videoWidth;
        tempCanvas.height = video.videoHeight;
        tempCanvas.getContext('2d').drawImage(video, 0, 0);

        // Convert to blob
        tempCanvas.toBlob(async (blob) => {
            if (!blob) return;

            const useAws = document.getElementById('useAws').checked;
            const useAzure = document.getElementById('useAzure').checked;

            // Clear canvas for new frame
            ctx.clearRect(0, 0, canvas.width, canvas.height);

            const promises = [];

            if (useAws) {
                document.getElementById('awsStatus').className = 'badge bg-warning text-dark';
                document.getElementById('awsStatus').innerText = 'Processing';
                const p = analyzeFrame(blob, 'AWS', '#ffc107');
                p.provider = 'AWS';
                promises.push(p);
            } else {
                document.getElementById('awsStatus').className = 'badge bg-secondary';
                document.getElementById('awsStatus').innerText = 'Idle';
                resetStats('aws');
            }

            if (useAzure) {
                document.getElementById('azureStatus').className = 'badge bg-primary';
                document.getElementById('azureStatus').innerText = 'Processing';
                const p = analyzeFrame(blob, 'AZURE', '#0d6efd');
                p.provider = 'AZURE';
                promises.push(p);
            } else {
                document.getElementById('azureStatus').className = 'badge bg-secondary';
                document.getElementById('azureStatus').innerText = 'Idle';
                resetStats('azure');
            }

            try {
                // Wait for all active providers to complete
                const results = await Promise.allSettled(promises);

                results.forEach((result, index) => {
                    const provider = promises[index].provider;
                    const providerLower = provider.toLowerCase();
                    const statusEl = document.getElementById(`${providerLower}Status`);

                    if (result.status === 'fulfilled' && result.value) {
                        const { detections, color, success, message } = result.value;

                        if (success) {
                            statusEl.className = 'badge bg-success';
                            statusEl.innerText = 'Active';
                            drawDetections(ctx, detections, color, provider, video.videoWidth);
                            updateStats(detections, providerLower);
                        } else {
                            statusEl.className = 'badge bg-danger';
                            statusEl.innerText = 'Error';
                            console.error(`${provider} API Error:`, message);
                            resetStats(providerLower);
                        }
                    } else {
                        statusEl.className = 'badge bg-danger';
                        statusEl.innerText = 'Offline';
                        console.error(`${provider} Request failed:`, result.reason || 'Unknown error');
                        resetStats(providerLower);
                    }
                });
                isAnalyzing = false;
            } catch (err) {
                console.error("Loop Error", err);
                isAnalyzing = false;
            }

        }, 'image/jpeg', 0.8);

    }, 1000);
}

async function analyzeFrame(blob, provider, color) {
    const formData = new FormData();
    formData.append('image', blob, 'frame.jpg');

    try {
        const response = await authenticatedFetch(`/api/face/verify-stream?provider=${provider.toUpperCase()}&live=true`, {
            method: 'POST',
            body: formData
        });

        if (response.ok) {
            const data = await response.json();
            return {
                detections: data.detections || [],
                success: data.success,
                message: data.message,
                provider: provider,
                color: color
            };
        }
    } catch (err) {
        console.error(`${provider} Analysis error:`, err);
    }
    return null;
}

function drawDetections(ctx, detections, color, labelPrefix, totalWidth) {
    detections.forEach(det => {
        const x = det.x || 0;
        const y = det.y || 0;
        const w = det.width || 0;
        const h = det.height || 0;

        if (w > 0 && h > 0) {
            ctx.save();

            // Draw Box with color based on authorization
            const isAuthorized = det.user && det.authorized;
            ctx.strokeStyle = isAuthorized ? '#00ff00' : '#ff0000'; // Green for authorized, Red for unauthorized
            ctx.lineWidth = 3;
            if (labelPrefix === 'AWS') {
                ctx.setLineDash([]); // Solid
            } else {
                ctx.setLineDash([5, 5]); // Dashed
            }
            ctx.strokeRect(x, y, w, h);
            ctx.setLineDash([]); // Reset

            // Draw TOP label (Provider + Emotion + Liveness)
            let topLabelY = y - 30;
            if (labelPrefix === 'AZURE') {
                topLabelY = y - 30; // Keep at top for both
            }

            // Un-flip context locally for readable text
            ctx.translate(x, topLabelY);
            ctx.scale(-1, 1);
            ctx.translate(-w, 0);

            const emotion = det.emotion || 'Neutral';
            const age = det.age || 'N/A';
            const liveness = det.isLive ? '✓ Live' : '⚠ Spoof';
            const topText = `${labelPrefix}: ${emotion} | Age: ${age} | ${liveness}`;

            ctx.font = 'bold 13px Inter, system-ui, sans-serif';
            const topMetrics = ctx.measureText(topText);
            const topWidth = topMetrics.width;

            // Draw Top Label Background
            ctx.fillStyle = color;
            ctx.fillRect(0, 0, topWidth + 12, 24);

            // Draw Top Text
            ctx.fillStyle = labelPrefix === 'AWS' ? '#000' : '#fff';
            ctx.fillText(topText, 6, 17);

            ctx.restore();

            // Draw BOTTOM label (Authorization Status + Name)
            ctx.save();
            const bottomLabelY = y + h + 10;

            ctx.translate(x, bottomLabelY);
            ctx.scale(-1, 1);
            ctx.translate(-w, 0);

            let statusText;
            let statusBgColor;
            let statusTextColor;

            if (isAuthorized) {
                statusText = `✓ AUTHORISED: ${det.user.name}`;
                statusBgColor = '#00c853'; // Bright green
                statusTextColor = '#ffffff';
            } else {
                statusText = `✗ UNAUTHORISED`;
                statusBgColor = '#d32f2f'; // Bright red
                statusTextColor = '#ffffff';
            }

            ctx.font = 'bold 14px Inter, system-ui, sans-serif';
            const statusMetrics = ctx.measureText(statusText);
            const statusWidth = statusMetrics.width;

            // Draw Status Background
            ctx.fillStyle = statusBgColor;
            ctx.fillRect(0, 0, statusWidth + 16, 28);

            // Draw Status Text
            ctx.fillStyle = statusTextColor;
            ctx.fillText(statusText, 8, 19);

            ctx.restore();
        }
    });
}

function updateStats(detections, providerPrefix) {
    const total = detections.length;
    const authorized = detections.filter(d => d.user).length;

    // Extract emotions safely
    const emotions = detections.map(d => d.emotion).filter(e => e);

    let mode = 'N/A';
    if (emotions.length > 0) {
        mode = emotions.sort((a, b) =>
            emotions.filter(v => v === a).length
            - emotions.filter(v => v === b).length
        ).pop();
    }

    const countEl = document.getElementById(`${providerPrefix}FaceCount`);
    const authEl = document.getElementById(`${providerPrefix}IdentifiedCount`);
    const emoEl = document.getElementById(`${providerPrefix}Emotion`);

    if (countEl) countEl.innerText = total;
    if (authEl) authEl.innerText = authorized;
    if (emoEl) emoEl.innerText = mode || 'N/A';
}

function resetStats(providerPrefix) {
    const countEl = document.getElementById(`${providerPrefix}FaceCount`);
    const authEl = document.getElementById(`${providerPrefix}IdentifiedCount`);
    const emoEl = document.getElementById(`${providerPrefix}Emotion`);

    if (countEl) countEl.innerText = '0';
    if (authEl) authEl.innerText = '0';
    if (emoEl) emoEl.innerText = 'N/A';
}

function checkAuth() {
    const token = localStorage.getItem('authToken');
    if (!token) {
        window.location.href = '/?error=unauthorized';
    }
}

function setupImagePreview() {
    const fileInput = document.getElementById('faceImage');
    const preview = document.getElementById('imagePreview');
    const placeholder = document.getElementById('placeholderIcon');

    if (!fileInput) return; // Guard clause

    fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file) {
            const reader = new FileReader();
            reader.onload = (event) => {
                preview.src = event.target.result;
                preview.style.display = 'block';
                placeholder.style.display = 'none';
            };
            reader.readAsDataURL(file);
        }
    });
}

function setupFormSubmission() {
    const form = document.getElementById('faceRegistrationForm');
    if (!form) return; // Guard clause

    const submitBtn = document.getElementById('submitBtn');
    const spinner = submitBtn.querySelector('.spinner-border');

    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        const fileInput = document.getElementById('faceImage');
        if (!fileInput.files[0]) {
            showAlert('Please select an image', 'warning');
            return;
        }

        const formData = new FormData();
        formData.append('name', document.getElementById('name').value);
        formData.append('email', document.getElementById('email').value);
        formData.append('department', document.getElementById('department').value);
        formData.append('position', document.getElementById('position').value);
        formData.append('image', fileInput.files[0]);

        setLoading(true);

        try {
            const response = await authenticatedFetch('/api/face/register', {
                method: 'POST',
                body: formData
            });

            if (response.ok) {
                showAlert('User registered successfully!', 'success');
                form.reset();
                document.getElementById('imagePreview').style.display = 'none';
                document.getElementById('placeholderIcon').style.display = 'block';
                loadUsers();
            }
        } catch (error) {
            console.error('Detailed Registration Error:', error);
            showAlert('Registration Error: ' + error.message, 'danger');
        } finally {
            setLoading(false);
        }
    });

    function setLoading(isLoading) {
        submitBtn.disabled = isLoading;
        if (isLoading) {
            spinner.classList.remove('d-none');
        } else {
            spinner.classList.add('d-none');
        }
    }
}

async function loadUsers() {
    const tableBody = document.getElementById('userTableBody');
    if (!tableBody) return;

    try {
        const response = await authenticatedFetch('/api/face/users');
        if (response.ok) {
            const users = await response.json();
            renderUsers(users);
        } else {
            tableBody.innerHTML = '<tr><td colspan="5" class="text-center text-danger">Failed to load users</td></tr>';
        }
    } catch (error) {
        console.error('Error loading users:', error);
        tableBody.innerHTML = '<tr><td colspan="5" class="text-center text-danger">Network error loading users</td></tr>';
    }
}

function renderUsers(users) {
    const tableBody = document.getElementById('userTableBody');
    if (users.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="5" class="text-center py-4 text-muted">No authorized users found</td></tr>';
        return;
    }

    tableBody.innerHTML = users.map(user => {
        // Use imageData (Base64) if available, otherwise create initials avatar
        let userAvatar;
        if (user.imageData) {
            userAvatar = `<img src="${user.imageData}" class="user-avatar me-3" alt="${user.name}">`;
        } else {
            // Fallback: Create an initials avatar
            const initials = user.name.split(' ').map(n => n[0]).join('').toUpperCase().substring(0, 2);
            const colors = ['#0d6efd', '#6610f2', '#6f42c1', '#d63384', '#dc3545', '#fd7e14', '#ffc107', '#198754', '#20c997', '#0dcaf0'];
            const bgColor = colors[user.name.charCodeAt(0) % colors.length];

            userAvatar = `<div class="user-avatar me-3 d-flex align-items-center justify-content-center text-white fw-bold" 
                               style="background: ${bgColor}; font-size: 0.9rem;">${initials}</div>`;
        }

        return `
        <tr>
            <td>
                <div class="d-flex align-items-center">
                    ${userAvatar}
                    <div>
                        <div class="fw-bold">${user.name}</div>
                        <div class="small text-muted">${user.position || 'Employee'}</div>
                    </div>
                </div>
            </td>
            <td>
                <div class="small">${user.email}</div>
            </td>
            <td>
                <span class="badge bg-light text-dark">${user.department || 'N/A'}</span>
            </td>
            <td>
                <span class="status-badge bg-success bg-opacity-10 text-success">Active</span>
            </td>
            <td>
                <button class="btn btn-sm btn-outline-danger rounded-circle" onclick="deleteUser('${user.id}')" title="Delete">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `;
    }).join('');
}

async function deleteUser(id) {
    if (!confirm('Are you sure you want to remove this authorized user?')) return;

    try {
        const response = await authenticatedFetch(`/api/face/users/${id}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            showAlert('User removed successfully', 'info');
            loadUsers();
        } else {
            showAlert('Failed to delete user', 'danger');
        }
    } catch (error) {
        console.error('Error deleting user:', error);
        showAlert('Network error', 'danger');
    }
}

async function authenticatedFetch(url, options = {}) {
    let token = localStorage.getItem('authToken');

    if (!options.headers) {
        options.headers = {};
    }

    options.headers['Authorization'] = 'Bearer ' + token;

    let response = await fetch(url, options);

    if (response.status === 401) {
        window.location.href = '/?error=session_expired';
    }
    if (!response.ok) {
        const errorText = await response.text().catch(() => "Unknown error");
        throw new Error(`Server Error (${response.status}): ${errorText || response.statusText}`);
    }
    return response;
}

function showAlert(message, type = 'success') {
    const container = document.getElementById('alertContainer');
    if (!container) return;

    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type} alert-dismissible fade show shadow`;
    alertDiv.role = 'alert';
    alertDiv.innerHTML = `
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    `;

    container.appendChild(alertDiv);

    setTimeout(() => {
        alertDiv.classList.remove('show');
        setTimeout(() => alertDiv.remove(), 500);
    }, 5000);
}

function logout() {
    localStorage.clear();
    window.location.href = '/';
}
