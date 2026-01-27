# Qualtech-AI API Reference

## Overview

Base URL: `http://localhost:8080/api`  
Authentication: JWT Bearer Token (except public endpoints)

---

## Table of Contents

1. [Authentication APIs](#authentication-apis)
2. [Face Recognition APIs](#face-recognition-apis)
3. [Sentiment Analysis APIs](#sentiment-analysis-apis)
4. [Video Analysis APIs](#video-analysis-apis)
5. [User Management APIs](#user-management-apis)
6. [Sync APIs](#sync-apis)

---

## Authentication APIs

### 1. Register New User

**Endpoint**: `POST /api/auth/register`  
**Access**: Public  
**Description**: Create a new user account. Sends verification email.

**Request Body**:

```json
{
  "email": "user@example.com",
  "username": "johndoe",
  "password": "SecurePass123"
}
```

**Success Response (201 Created)**:

```json
{
  "message": "User registered successfully. Please check your email for verification.",
  "userId": 1
}
```

**Error Responses**:

400 Bad Request - Invalid input:

```json
{
  "error": "Bad Request",
  "message": "Validation failed",
  "details": [
    "Email is required",
    "Password must be at least 8 characters"
  ]
}
```

409 Conflict - Email already exists:

```json
{
  "error": "Conflict",
  "message": "Email already registered"
}
```

**cURL Example**:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "username": "johndoe",
    "password": "SecurePass123"
  }'
```

**JavaScript Example**:

```javascript
const response = await fetch('http://localhost:8080/api/auth/register', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    email: 'user@example.com',
    username: 'johndoe',
    password: 'SecurePass123'
  })
});

const data = await response.json();
console.log(data);
```

---

### 2. Login

**Endpoint**: `POST /api/auth/login`  
**Access**: Public  
**Description**: Authenticate user and receive JWT tokens

**Request Body**:

```json
{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```

**Success Response (200 OK)**:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "username": "johndoe",
  "roles": ["ROLE_USER"]
}
```

**Error Responses**:

401 Unauthorized - Invalid credentials:

```json
{
  "error": "Unauthorized",
  "message": "Invalid email or password"
}
```

403 Forbidden - Email not verified:

```json
{
  "error": "Forbidden",
  "message": "Please verify your email before logging in"
}
```

**cURL Example**:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePass123"
  }'
```

**JavaScript Example**:

```javascript
const response = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    email: 'user@example.com',
    password: 'SecurePass123'
  })
});

const data = await response.json();
// Store tokens
localStorage.setItem('accessToken', data.token);
localStorage.setItem('refreshToken', data.refreshToken);
```

---

### 3. Refresh Token

**Endpoint**: `POST /api/auth/refresh-token`  
**Access**: Public  
**Description**: Get new access token using refresh token

**Request Body**:

```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Success Response (200 OK)**:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440001",
  "tokenType": "Bearer"
}
```

**cURL Example**:

```bash
curl -X POST http://localhost:8080/api/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

---

### 4. Verify Email

**Endpoint**: `GET /api/auth/verify`  
**Access**: Public  
**Description**: Verify email address using token from email

**Query Parameters**:

- `token` (required): Verification token from email

**Success Response (200 OK)**:

```json
{
  "message": "Email verified successfully. You can now log in."
}
```

**Error Response (400 Bad Request)**:

```json
{
  "error": "Bad Request",
  "message": "Invalid or expired verification token"
}
```

**cURL Example**:

```bash
curl -X GET "http://localhost:8080/api/auth/verify?token=abc123def456"
```

---

### 5. Forgot Password

**Endpoint**: `POST /api/auth/forgot-password`  
**Access**: Public  
**Description**: Request password reset email

**Query Parameters**:

- `email` (required): User's email address

**Success Response (200 OK)**:

```json
{
  "message": "Password reset link sent to your email."
}
```

**cURL Example**:

```bash
curl -X POST "http://localhost:8080/api/auth/forgot-password?email=user@example.com"
```

---

### 6. Reset Password

**Endpoint**: `POST /api/auth/reset-password`  
**Access**: Public  
**Description**: Complete password reset with token

**Request Body**:

```json
{
  "token": "reset-token-from-email",
  "newPassword": "NewSecurePass456"
}
```

**Success Response (200 OK)**:

```json
{
  "message": "Password reset successfully."
}
```

**cURL Example**:

```bash
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "token": "reset-token-from-email",
    "newPassword": "NewSecurePass456"
  }'
```

---

### 7. Logout

**Endpoint**: `POST /api/auth/logout`  
**Access**: Authenticated  
**Description**: Invalidate refresh token

**Request Body**:

```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Success Response (200 OK)**:

```json
{
  "message": "Logged out successfully"
}
```

**cURL Example**:

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

---

## Face Recognition APIs

### 1. Register Face

**Endpoint**: `POST /api/face/register`  
**Access**: Authenticated  
**Description**: Register a new face in the system

**Request**: Multipart Form Data

- `name` (required): Person's full name
- `email` (required): Email address
- `department` (optional): Department name
- `position` (optional): Job position
- `provider` (required): LOCAL, AZURE, or AWS
- `image` (required): Image file (JPEG/PNG)

**Success Response (200 OK)**:

```json
{
  "id": 1,
  "name": "John Doe",
  "email": "john@example.com",
  "department": "Engineering",
  "position": "Software Engineer",
  "provider": "LOCAL",
  "registeredAt": "2026-01-27T10:30:00",
  "message": "Face registered successfully"
}
```

**Error Responses**:

400 Bad Request - Invalid image:

```json
{
  "error": "Bad Request",
  "message": "No face detected in image"
}
```

409 Conflict - Face already registered:

```json
{
  "error": "Conflict",
  "message": "Face already registered for this person"
}
```

**cURL Example**:

```bash
curl -X POST http://localhost:8080/api/face/register \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -F "name=John Doe" \
  -F "email=john@example.com" \
  -F "department=Engineering" \
  -F "position=Software Engineer" \
  -F "provider=LOCAL" \
  -F "image=@/path/to/photo.jpg"
```

**JavaScript Example**:

```javascript
const formData = new FormData();
formData.append('name', 'John Doe');
formData.append('email', 'john@example.com');
formData.append('department', 'Engineering');
formData.append('position', 'Software Engineer');
formData.append('provider', 'LOCAL');
formData.append('image', fileInput.files[0]);

const response = await fetch('http://localhost:8080/api/face/register', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
  },
  body: formData
});

const data = await response.json();
console.log(data);
```

---

### 2. Verify Face

**Endpoint**: `POST /api/face/verify`  
**Access**: Authenticated  
**Description**: Verify/identify a face

**Request**: Multipart Form Data

- `provider` (required): LOCAL, AZURE, or AWS
- `image` (required): Image file to verify

**Success Response (200 OK)**:

```json
{
  "matched": true,
  "confidence": 0.95,
  "faceUser": {
    "id": 1,
    "name": "John Doe",
    "email": "john@example.com",
    "department": "Engineering",
    "position": "Software Engineer"
  },
  "provider": "AZURE",
  "timestamp": "2026-01-27T10:35:00"
}
```

**No Match Response (200 OK)**:

```json
{
  "matched": false,
  "confidence": 0.45,
  "message": "No matching face found",
  "provider": "AZURE",
  "timestamp": "2026-01-27T10:35:00"
}
```

**cURL Example**:

```bash
curl -X POST http://localhost:8080/api/face/verify \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -F "provider=AZURE" \
  -F "image=@/path/to/verify.jpg"
```

---

### 3. List All Faces

**Endpoint**: `GET /api/face/list`  
**Access**: Authenticated  
**Description**: Get all registered faces

**Success Response (200 OK)**:

```json
{
  "faces": [
    {
      "id": 1,
      "name": "John Doe",
      "email": "john@example.com",
      "department": "Engineering",
      "position": "Software Engineer",
      "registeredAt": "2026-01-15T09:00:00"
    },
    {
      "id": 2,
      "name": "Jane Smith",
      "email": "jane@example.com",
      "department": "Marketing",
      "position": "Manager",
      "registeredAt": "2026-01-16T11:30:00"
    }
  ],
  "total": 2
}
```

**cURL Example**:

```bash
curl -X GET http://localhost:8080/api/face/list \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

---

### 4. Delete Face

**Endpoint**: `DELETE /api/face/delete/{id}`  
**Access**: Authenticated (Admin only)  
**Description**: Remove a face record

**Path Parameters**:

- `id`: Face user ID

**Success Response (200 OK)**:

```json
{
  "message": "Face deleted successfully"
}
```

**cURL Example**:

```bash
curl -X DELETE http://localhost:8080/api/face/delete/1 \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

---

### 5. Real-time Stream Verification

**Endpoint**: `POST /api/face/verify-stream`  
**Access**: Authenticated  
**Description**: Verify face from video stream frame

**Request**: Multipart Form Data

- `provider` (required): LOCAL, AZURE, or AWS
- `frame` (required): Video frame as image

**Response**: Same as `/api/face/verify`

---

## Sentiment Analysis APIs

### 1. Analyze Text Sentiment

**Endpoint**: `POST /api/sentiment/analyze`  
**Access**: Authenticated  
**Description**: Analyze sentiment of text

**Request Body**:

```json
{
  "text": "I absolutely love this product! It works perfectly and exceeded my expectations.",
  "provider": "AZURE"
}
```

**Success Response (200 OK)**:

```json
{
  "sentiment": "Positive",
  "confidenceScores": {
    "positive": 0.98,
    "neutral": 0.01,
    "negative": 0.01
  },
  "provider": "AZURE",
  "processedAt": "2026-01-27T10:40:00"
}
```

**Example - Negative Sentiment**:

```json
{
  "text": "This is terrible! Very disappointed with the service.",
  "provider": "AZURE"
}
```

Response:

```json
{
  "sentiment": "Negative",
  "confidenceScores": {
    "positive": 0.02,
    "neutral": 0.08,
    "negative": 0.90
  },
  "provider": "AZURE",
  "processedAt": "2026-01-27T10:41:00"
}
```

**cURL Example**:

```bash
curl -X POST http://localhost:8080/api/sentiment/analyze \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "I love this application!",
    "provider": "AZURE"
  }'
```

**JavaScript Example**:

```javascript
const response = await fetch('http://localhost:8080/api/sentiment/analyze', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    text: 'I love this application!',
    provider: 'AZURE'
  })
});

const data = await response.json();
console.log(`Sentiment: ${data.sentiment}`);
console.log(`Confidence: ${data.confidenceScores.positive}`);
```

---

### 2. Batch Sentiment Analysis

**Endpoint**: `POST /api/sentiment/batch`  
**Access**: Authenticated  
**Description**: Analyze multiple texts at once

**Request Body**:

```json
{
  "texts": [
    "This is amazing!",
    "Not happy with this.",
    "It's okay, nothing special."
  ],
  "provider": "AZURE"
}
```

**Success Response (200 OK)**:

```json
{
  "results": [
    {
      "text": "This is amazing!",
      "sentiment": "Positive",
      "confidence": 0.98
    },
    {
      "text": "Not happy with this.",
      "sentiment": "Negative",
      "confidence": 0.92
    },
    {
      "text": "It's okay, nothing special.",
      "sentiment": "Neutral",
      "confidence": 0.87
    }
  ],
  "totalProcessed": 3
}
```

---

## Video Analysis APIs

### 1. Upload Video

**Endpoint**: `POST /api/video/upload`  
**Access**: Authenticated  
**Description**: Upload video for transcription and sentiment analysis

**Request**: Multipart Form Data

- `video` (required): Video file (MP4, AVI, MOV, etc.)

**Success Response (202 Accepted)**:

```json
{
  "videoId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "Processing",
  "message": "Video uploaded successfully. Processing started.",
  "uploadedAt": "2026-01-27T10:45:00"
}
```

**cURL Example**:

```bash
curl -X POST http://localhost:8080/api/video/upload \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -F "video=@/path/to/video.mp4"
```

**JavaScript Example**:

```javascript
const formData = new FormData();
formData.append('video', videoFile);

const response = await fetch('http://localhost:8080/api/video/upload', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
  },
  body: formData
});

const data = await response.json();
console.log(`Video ID: ${data.videoId}`);
// Poll for status
checkVideoStatus(data.videoId);
```

---

### 2. Check Processing Status

**Endpoint**: `GET /api/video/status/{videoId}`  
**Access**: Authenticated  
**Description**: Get video processing status

**Path Parameters**:

- `videoId`: UUID of the video

**Success Response (200 OK)**:

```json
{
  "videoId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "Completed",
  "progress": 100,
  "transcriptionReady": true,
  "sentimentReady": true,
  "startedAt": "2026-01-27T10:45:00",
  "completedAt": "2026-01-27T10:46:30"
}
```

**Processing Response**:

```json
{
  "videoId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "Processing",
  "progress": 45,
  "currentStep": "Transcribing audio",
  "estimatedTimeRemaining": "30 seconds"
}
```

**cURL Example**:

```bash
curl -X GET http://localhost:8080/api/video/status/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

---

### 3. Get Video Results

**Endpoint**: `GET /api/video/results/{videoId}`  
**Access**: Authenticated  
**Description**: Get transcription and sentiment analysis results

**Path Parameters**:

- `videoId`: UUID of the video

**Success Response (200 OK)**:

```json
{
  "videoId": "550e8400-e29b-41d4-a716-446655440000",
  "transcript": "Hello everyone, welcome to this presentation. Today we'll discuss...",
  "segments": [
    {
      "text": "Hello everyone, welcome to this presentation.",
      "sentiment": "Positive",
      "confidence": 0.85,
      "startTime": "00:00:01",
      "endTime": "00:00:05"
    },
    {
      "text": "Today we'll discuss some challenges we faced.",
      "sentiment": "Neutral",
      "confidence": 0.78,
      "startTime": "00:00:06",
      "endTime": "00:00:10"
    }
  ],
  "overallSentiment": "Positive",
  "sentimentDistribution": {
    "positive": 0.65,
    "neutral": 0.30,
    "negative": 0.05
  },
  "duration": "00:05:30",
  "wordCount": 750,
  "processingTime": "45 seconds"
}
```

**cURL Example**:

```bash
curl -X GET http://localhost:8080/api/video/results/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

---

### 4. Download Transcript

**Endpoint**: `GET /api/video/transcript/{videoId}`  
**Access**: Authenticated  
**Description**: Download transcript as text file

**Path Parameters**:

- `videoId`: UUID of the video

**Success Response (200 OK)**:
Returns plain text file with transcript.

**cURL Example**:

```bash
curl -X GET http://localhost:8080/api/video/transcript/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -o transcript.txt
```

---

## User Management APIs

### 1. Get Current User Profile

**Endpoint**: `GET /api/user/profile`  
**Access**: Authenticated  
**Description**: Get logged-in user's profile

**Success Response (200 OK)**:

```json
{
  "id": 1,
  "email": "user@example.com",
  "username": "johndoe",
  "roles": ["ROLE_USER"],
  "verified": true,
  "enabled": true,
  "createdAt": "2026-01-15T09:00:00"
}
```

**cURL Example**:

```bash
curl -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

---

## Sync APIs

### 1. Sync AWS to Local

**Endpoint**: `POST /api/sync/aws-to-local`  
**Access**: Authenticated (Admin)  
**Description**: Sync registered faces from AWS Rekognition to local database

**Success Response (200 OK)**:

```json
{
  "message": "Sync completed successfully",
  "syncedFaces": 15,
  "duration": "3.2 seconds"
}
```

---

### 2. Sync Local to AWS

**Endpoint**: `POST /api/sync/local-to-aws`  
**Access**: Authenticated (Admin)  
**Description**: Sync local faces to AWS Rekognition

**Success Response (200 OK)**:

```json
{
  "message": "Sync completed successfully",
  "syncedFaces": 20,
  "duration": "5.8 seconds"
}
```

---

### 3. Sync Azure to Local

**Endpoint**: `POST /api/sync/azure-to-local`  
**Access**: Authenticated (Admin)  
**Description**: Sync Azure Face API data to local

**Success Response (200 OK)**:

```json
{
  "message": "Sync completed successfully",
  "syncedFaces": 18,
  "duration": "4.1 seconds"
}
```

---

## Error Codes Reference

| Status Code | Meaning | Common Causes |
|-------------|---------|---------------|
| 400 | Bad Request | Invalid input, validation failed |
| 401 | Unauthorized | Missing/invalid token, expired token |
| 403 | Forbidden | Insufficient permissions, unverified email |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate data (email, username) |
| 500 | Internal Server Error | Server error, external API failure |

---

## Rate Limiting

Currently no rate limiting implemented. Consider adding for production:

- Authentication: 5 requests per minute per IP
- Face verification: 10 requests per minute per user
- Video upload: 3 requests per hour per user

---

## Testing with Postman

### Import Collection

1. Create new collection "Qualtech-AI"
2. Set collection variable `baseUrl` = `http://localhost:8080/api`
3. Set collection variable `accessToken` = (get from login response)
4. Use `{{baseUrl}}` and `{{accessToken}}` in requests

### Example Workflow

1. **Register** → `POST {{baseUrl}}/auth/register`
2. **Verify Email** → Click link in email
3. **Login** → `POST {{baseUrl}}/auth/login` → Save token
4. **Set Auth** → In collection, add header:

   ```
   Authorization: Bearer {{accessToken}}
   ```

5. **Test APIs** → All authenticated endpoints now work

---

## Response Time Benchmarks

Typical response times (local development):

| Endpoint | Average Time |
|----------|-------------|
| Login | 50-100ms |
| Face Register (Local) | 200-500ms |
| Face Verify (Local) | 150-300ms |
| Face Verify (Azure) | 800-1500ms |
| Face Verify (AWS) | 700-1200ms |
| Sentiment (Azure) | 500-800ms |
| Video Upload | 2-10s (depends on size) |
| Video Processing | 30s-5min (depends on length) |

---

*For complete documentation, see FULL_DOCUMENTATION.md*

**Last Updated**: 2026-01-27
