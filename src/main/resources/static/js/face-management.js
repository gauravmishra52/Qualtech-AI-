document.addEventListener('DOMContentLoaded', () => {
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

    tableBody.innerHTML = users.map(user => `
        <tr>
            <td>
                <div class="d-flex align-items-center">
                    <img src="${user.imageUrl || '/images/default-avatar.png'}" class="user-avatar me-3" alt="${user.name}">
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
    `).join('');
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

    options.headers = {
        ...options.headers,
        'Authorization': 'Bearer ' + token
    };

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
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type} alert-dismissible fade show shadow`;
    alertDiv.role = 'alert';
    alertDiv.innerHTML = `
        ${message}
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    `;

    document.getElementById('alertContainer').appendChild(alertDiv);

    setTimeout(() => {
        alertDiv.classList.remove('show');
        setTimeout(() => alertDiv.remove(), 500);
    }, 5000);
}

function logout() {
    localStorage.clear();
    window.location.href = '/';
}
