# Qualtech-AI - Complete Application Documentation

## 📋 Table of Contents

1. [Project Overview](#project-overview)
2. [System Architecture](#system-architecture)
3. [Technology Stack](#technology-stack)
4. [Features & Modules](#features--modules)
5. [Backend Architecture](#backend-architecture)
6. [Frontend Architecture](#frontend-architecture)
7. [Database Schema](#database-schema)
8. [Security Implementation](#security-implementation)
9. [API Documentation](#api-documentation)
10. [Email Integration](#email-integration)
11. [Face Recognition System](#face-recognition-system)
12. [Sentiment Analysis System](#sentiment-analysis-system)
13. [Video Analysis](#video-analysis)
14. [Cloud Integration](#cloud-integration)
15. [Configuration & Setup](#configuration--setup)
16. [Deployment Guide](#deployment-guide)
17. [Troubleshooting](#troubleshooting)

---

## 📖 Project Overview

**Qualtech-AI** is a comprehensive enterprise-grade full-stack application that combines traditional web application features with advanced AI/ML capabilities. The application provides a robust authentication system, face recognition, sentiment analysis, and video processing capabilities.

### Key Highlights

- **Full-Stack Application**: Java Spring Boot backend with HTML/JavaScript frontend
- **AI/ML Integration**: Face recognition and sentiment analysis
- **Multi-Cloud Support**: AWS and Azure integration
- **Enterprise Security**: JWT-based authentication with role-based access control
- **Scalable Architecture**: Microservice-ready design patterns

---

## 🏗 System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend Layer                        │
│  (HTML5 + JavaScript + Bootstrap 5)                         │
│  - index.html (Login/Register)                              │
│  - dashboard.html                                           │
│  - face-management.html                                     │
│  - face-comparison.html                                     │
│  - video-analysis.html                                      │
└────────────────────┬────────────────────────────────────────┘
                     │ HTTP/REST API
┌────────────────────▼────────────────────────────────────────┐
│                     Spring Boot Backend                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │            Security Layer (JWT + Spring Security)     │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                 Controller Layer                      │  │
│  │  - AuthController                                     │  │
│  │  - FaceRecognitionController                          │  │
│  │  - SentimentController                                │  │
│  │  - VideoController                                    │  │
│  │  - UserController                                     │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                  Service Layer                        │  │
│  │  - AuthService                                        │  │
│  │  - FaceRecognitionService                             │  │
│  │  - SentimentAnalysisService                           │  │
│  │  - VideoAnalysisService                               │  │
│  │  - EmailService                                       │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Repository Layer (JPA)                   │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│                    PostgreSQL Database                       │
│  - users, roles, refresh_tokens                             │
│  - face_users, face_verification_logs                       │
│  - password_reset_tokens                                    │
└──────────────────────────────────────────────────────────────┘

External Services:
├─ AWS Services (Rekognition, S3, Comprehend, Transcribe)
├─ Azure Services (Face API, Text Analytics, Blob Storage, Speech)
└─ SMTP Email Server (Gmail)
```

### Component Interaction Flow

1. **User Request** → Frontend (HTML/JS)
2. **API Call** → Spring Boot Controllers
3. **Authentication** → JWT Filter validates token
4. **Business Logic** → Service Layer processes request
5. **Data Access** → Repository Layer (JPA) interacts with PostgreSQL
6. **External AI** → Azure/AWS APIs for AI operations
7. **Response** → JSON data back to frontend

---

## 💻 Technology Stack

### Backend Technologies

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 21 | Core programming language |
| **Spring Boot** | 3.5.10 | Application framework |
| **Spring Security** | (via Spring Boot) | Authentication & Authorization |
| **Spring Data JPA** | (via Spring Boot) | ORM and database operations |
| **Hibernate** | (via JPA) | Object-relational mapping |
| **PostgreSQL** | 42.7.3 | Primary database |
| **Flyway** | 9.22.3 | Database migration management |
| **JWT (JJWT)** | 0.12.7 | JSON Web Token implementation |
| **Lombok** | 1.18.32 | Code generation (reduce boilerplate) |
| **JavaCV** | 1.5.10 | Computer vision library wrapper |
| **OpenCV** | 4.9.0 | Face detection and image processing |
| **FFmpeg** | 6.1.1 | Video/audio processing |
| **Maven** | 3.8+ | Build and dependency management |

### Cloud & External Services

| Service | Provider | Purpose |
|---------|----------|---------|
| **AWS Rekognition** | Amazon | Face recognition and comparison |
| **AWS S3** | Amazon | File storage for face images |
| **AWS Comprehend** | Amazon | Sentiment analysis |
| **AWS Transcribe** | Amazon | Speech-to-text conversion |
| **Azure Face API** | Microsoft | Face detection and verification |
| **Azure Text Analytics** | Microsoft | Sentiment analysis |
| **Azure Blob Storage** | Microsoft | Video file storage |
| **Azure Speech Service** | Microsoft | Speech recognition |
| **Gmail SMTP** | Google | Email delivery service |

### Frontend Technologies

| Technology | Purpose |
|------------|---------|
| **HTML5** | Structure and markup |
| **Vanilla JavaScript** | Client-side logic and interactivity |
| **Bootstrap 5** | Responsive UI framework |
| **Fetch API** | REST API communication |
| **LocalStorage** | JWT token storage |

### Development Tools

- **Spring Boot DevTools** - Hot reload during development
- **Spring Boot Actuator** - Application monitoring
- **H2 Database** - In-memory database for testing
- **JUnit 5** - Unit testing framework
- **Spring Security Test** - Security testing utilities

---

## 🚀 Features & Modules

### 1. Authentication & Authorization

#### Overview

Robust JWT-based authentication system with email verification and password recovery.

#### Features

- ✅ User Registration with email verification
- ✅ Secure Login with JWT tokens
- ✅ Refresh Token mechanism for persistent sessions
- ✅ Password Reset via email
- ✅ Role-Based Access Control (RBAC)
- ✅ Account verification before login
- ✅ Token expiration and renewal
- ✅ Secure logout

#### Endpoints

- `POST /api/auth/register` - Create new user account
- `POST /api/auth/login` - Authenticate and receive tokens
- `POST /api/auth/logout` - Invalidate refresh token
- `POST /api/auth/refresh-token` - Get new access token
- `GET /api/auth/verify?token={token}` - Verify email address
- `POST /api/auth/forgot-password?email={email}` - Request password reset
- `POST /api/auth/reset-password` - Complete password reset

#### Security Features

- BCrypt password hashing (strength: 12)
- JWT token signing with HMAC-SHA256
- Access token expiration: 1 hour
- Refresh token expiration: 24 hours
- CORS configuration for cross-origin requests
- CSRF protection disabled (stateless API)

---

### 2. Face Recognition System

#### Overview

Multi-provider face recognition system supporting local OpenCV models, Azure Face API, and AWS Rekognition.

#### Architecture

```
Face Recognition System
├─ Local Provider (OpenCV + DL4J)
│  ├─ Face Detection (Caffe Model)
│  ├─ Face Embedding Generation
│  └─ Similarity Calculation
├─ Azure Provider
│  ├─ Azure Face API
│  └─ Face Comparison Service
└─ AWS Provider
   ├─ AWS Rekognition
   └─ Face Collection Management
```

#### Key Components

**1. Face Registration**

- Captures and stores face data in database
- Generates embeddings for future comparison
- Stores metadata (name, email, department, position)
- Validates image quality before registration
- Supports multiple provider backends

**2. Face Verification**

- 1:1 face matching (verify identity)
- 1:N face search (identify from database)
- Real-time video stream processing
- Multi-frame verification for accuracy
- Adaptive threshold based on lighting conditions

**3. Face Management**

- List all registered faces
- Update face information
- Delete face records
- Sync between providers (local ↔ AWS ↔ Azure)
- Bulk import/export capabilities

#### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/face/register` | Register a new face |
| `POST` | `/api/face/verify` | Verify a face (1:1 or 1:N) |
| `POST` | `/api/face/verify-stream` | Real-time stream verification |
| `GET` | `/api/face/list` | Get all registered faces |
| `DELETE` | `/api/face/delete/{id}` | Remove a face record |
| `POST` | `/api/sync/aws-to-local` | Sync AWS faces to local |
| `POST` | `/api/sync/local-to-aws` | Sync local faces to AWS |
| `POST` | `/api/sync/azure-to-local` | Sync Azure faces to local |

#### Provider Comparison

| Feature | Local (OpenCV) | Azure Face API | AWS Rekognition |
|---------|----------------|----------------|-----------------|
| **Cost** | Free | Pay-per-use | Pay-per-use |
| **Privacy** | High (on-premise) | Medium | Medium |
| **Accuracy** | Good (85-90%) | Excellent (95-98%) | Excellent (95-98%) |
| **Speed** | Fast | Medium (API latency) | Medium (API latency) |
| **Offline** | ✅ Yes | ❌ No | ❌ No |
| **Scalability** | Limited | High | High |

#### Advanced Features

**Adaptive Thresholding**

- Automatically adjusts matching threshold based on lighting
- Normal light: 0.8
- Low light: 0.7
- Very low light: 0.6

**Multi-Frame Verification**

- Captures 3 frames over 2 seconds
- Requires 60% majority consensus
- Reduces false positives from single-frame errors

**Silent Self-Improvement**

- Learns from high-confidence matches (>0.95)
- Automatically improves model over time
- Minimum 5 attempts before updating

**Startup Integrity Check**

- Validates model files on startup
- Checks database connectivity
- Verifies external API credentials

---

### 3. Sentiment Analysis

#### Overview

Advanced text sentiment analysis using Azure Text Analytics and AWS Comprehend.

#### Supported Sentiments

- **Positive** - Happy, satisfied, enthusiastic
- **Negative** - Angry, frustrated, disappointed
- **Neutral** - Objective, informational
- **Mixed** - Combination of sentiments (AWS only)

#### Features

- Real-time text analysis
- Confidence scores for each sentiment
- Multi-language support (via Azure)
- Batch processing capability
- Integration with video transcription

#### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/sentiment/analyze` | Analyze text sentiment |
| `POST` | `/api/sentiment/batch` | Batch analysis |

#### Request Format

```json
{
  "text": "I absolutely love this product! It's amazing.",
  "provider": "AZURE"  // or "AWS"
}
```

#### Response Format

```json
{
  "sentiment": "Positive",
  "confidenceScores": {
    "positive": 0.98,
    "neutral": 0.01,
    "negative": 0.01
  },
  "provider": "AZURE"
}
```

#### Use Cases

- Customer feedback analysis
- Social media monitoring
- Product review analysis
- Support ticket prioritization
- Employee satisfaction surveys

---

### 4. Video Analysis

#### Overview

Comprehensive video processing with speech-to-text and sentiment analysis.

#### Workflow

```
Video Upload → Azure Blob Storage → Speech Recognition 
→ Transcription → Sentiment Analysis → Results Dashboard
```

#### Features

- Video upload to Azure Blob Storage
- Real-time speech-to-text conversion
- Automatic sentiment analysis of transcripts
- Speaker diarization (who said what)
- Timestamp synchronization
- Download transcripts and analysis results

#### Supported Video Formats

- MP4
- AVI
- MOV
- WebM
- MKV

#### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/video/upload` | Upload video for analysis |
| `GET` | `/api/video/status/{id}` | Check processing status |
| `GET` | `/api/video/results/{id}` | Get analysis results |
| `GET` | `/api/video/transcript/{id}` | Download transcript |

#### Processing Pipeline

1. **Upload** - Client uploads video file
2. **Storage** - Video stored in Azure Blob Storage
3. **Transcription** - Azure Speech Service converts audio to text
4. **Analysis** - Text sent to sentiment analysis
5. **Results** - Combined results stored and returned

---

### 5. Email Service

#### Overview

Transactional email service using Gmail SMTP for all application notifications.

#### Email Types

**1. Account Verification Email**

- Sent immediately after registration
- Contains unique verification link
- Token expires after 24 hours
- Resend option available

**2. Password Reset Email**

- Triggered by forgot password request
- Contains secure reset link
- Token expires after 1 hour
- One-time use only

**3. Welcome Email**

- Sent after successful email verification
- Contains getting started guide
- Account information summary

#### Configuration

Located in `application.yml`:

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${EMAIL_USERNAME}
    password: ${EMAIL_PASSWORD}  # Use App Password for Gmail
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

#### Email Templates

Email templates use HTML for rich formatting:

- Responsive design
- Company branding
- Call-to-action buttons
- Professional layout

#### Best Practices

- Use Gmail App Password (not regular password)
- Enable 2FA on Gmail account
- Monitor send quotas (2000 emails/day for free Gmail)
- Use environment variables for credentials
- Test emails in development

---

## 🏛 Backend Architecture

### Package Structure

```
com.qualtech_ai
├── config/                    # Configuration classes
│   ├── SecurityConfig.java    # Spring Security configuration
│   ├── JwtConfig.java          # JWT settings
│   ├── CorsConfig.java         # CORS configuration
│   ├── MailConfig.java         # Email configuration
│   ├── AzureConfig.java        # Azure services configuration
│   └── AwsConfig.java          # AWS services configuration
├── controller/                 # REST API controllers
│   ├── AuthController.java     # Authentication endpoints
│   ├── FaceRecognitionController.java
│   ├── SentimentController.java
│   ├── VideoController.java
│   ├── UserController.java
│   ├── SyncController.java     # Provider sync endpoints
│   └── HomeController.java     # Static content serving
├── dto/                        # Data Transfer Objects
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   ├── AuthResponse.java
│   ├── FaceRegistrationRequest.java
│   ├── FaceVerificationRequest.java
│   ├── SentimentRequest.java
│   └── VideoAnalysisResponse.java
├── entity/                     # JPA entities
│   ├── User.java               # User account entity
│   ├── Role.java               # User roles
│   ├── RefreshToken.java       # Refresh token storage
│   ├── PasswordResetToken.java # Password reset tokens
│   ├── FaceUser.java           # Face registration data
│   └── FaceVerificationLog.java # Audit logs
├── enums/                      # Enumeration types
│   └── FaceProvider.java       # LOCAL, AZURE, AWS
├── exception/                  # Custom exceptions
│   ├── GlobalExceptionHandler.java
│   ├── ResourceNotFoundException.java
│   ├── InvalidTokenException.java
│   ├── EmailAlreadyExistsException.java
│   └── UnauthorizedException.java
├── repository/                 # JPA repositories
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   ├── RefreshTokenRepository.java
│   ├── PasswordResetTokenRepository.java
│   ├── FaceUserRepository.java
│   └── FaceVerificationLogRepository.java
├── security/                   # Security components
│   ├── JwtAuthenticationFilter.java  # JWT token validation
│   ├── JwtTokenProvider.java         # Token generation
│   ├── UserDetailsServiceImpl.java   # User loading
│   ├── AuthEntryPointJwt.java        # 401 handler
│   └── CustomAccessDeniedHandler.java # 403 handler
├── service/                    # Business logic services
│   ├── impl/                   # Service implementations
│   ├── AuthService.java
│   ├── UserService.java
│   ├── EmailService.java
│   ├── FaceRecognitionService.java
│   ├── AwsFaceService.java
│   ├── AzureFaceService.java
│   ├── SentimentAnalysisService.java
│   ├── AzureSentimentService.java
│   ├── VideoAnalysisService.java
│   ├── AzureSpeechService.java
│   ├── AzureBlobService.java
│   ├── S3Service.java
│   ├── RefreshTokenService.java
│   ├── FaceSyncService.java
│   ├── MultiFrameVerificationService.java
│   ├── AdaptiveThresholdService.java
│   ├── SilentSelfImprovementService.java
│   └── StartupIntegrityService.java
└── util/                       # Utility classes
    ├── ImageUtil.java          # Image processing utilities
    ├── FileUtil.java           # File operations
    ├── DateUtil.java           # Date formatting
    └── ValidationUtil.java     # Input validation
```

### Service Layer Design

#### Interface-Based Design

All services follow interface-implementation pattern for:

- **Testability** - Easy to mock in unit tests
- **Flexibility** - Swap implementations without changing code
- **Clean Architecture** - Depend on abstractions, not concretions

#### Dependency Injection

All components use Spring's dependency injection:

```java
@Service
@RequiredArgsConstructor  // Lombok generates constructor
public class FaceRecognitionServiceImpl implements FaceRecognitionService {
    private final FaceUserRepository faceUserRepository;
    private final AwsFaceService awsFaceService;
    private final AzureFaceService azureFaceService;
    // Dependencies injected automatically
}
```

---

## 🎨 Frontend Architecture

### File Structure

```
src/main/resources/static/
├── index.html                 # Landing page / Login
├── dashboard.html             # Main user dashboard
├── face-management.html       # Face registration & management
├── face-comparison.html       # Face verification interface
├── video-analysis.html        # Video upload & analysis
├── css/
│   └── styles.css             # Custom styles
└── js/
    ├── auth.js                # Authentication logic
    ├── face.js                # Face recognition logic
    └── video.js               # Video analysis logic
```

### Page Descriptions

#### 1. index.html - Login & Registration

**Purpose**: Entry point for unauthenticated users

**Features**:

- Login form with email/password
- Registration form with validation
- Email verification notice
- Password reset link
- Responsive design

**Key Functions**:

- `handleLogin()` - Authenticate user
- `handleRegister()` - Create new account
- `storeTokens()` - Save JWT to localStorage
- `redirectToDashboard()` - Navigate after login

#### 2. dashboard.html - Main Hub

**Purpose**: Central navigation after login

**Features**:

- User profile display
- Quick stats overview
- Navigation cards to main features
- Recent activity feed
- Logout button

**Key Functions**:

- `loadUserProfile()` - Fetch user data
- `loadRecentActivity()` - Display recent actions
- `handleLogout()` - Clear tokens and redirect

#### 3. face-management.html - Face Administration

**Purpose**: Register and manage face data

**Features**:

- Face registration form
- Live camera preview
- Capture photo or upload file
- View all registered faces
- Edit/delete face records
- Provider selection (Local/Azure/AWS)
- Bulk operations

**Key Functions**:

- `initCamera()` - Start webcam
- `capturePhoto()` - Take snapshot
- `registerFace()` - Submit face data
- `loadFaceList()` - Display all faces
- `deleteFace()` - Remove record

#### 4. face-comparison.html - Face Verification

**Purpose**: Test face recognition accuracy

**Features**:

- Live camera feed
- Single image upload
- Provider selection
- Real-time verification results
- Confidence score display
- Match/no-match indicators
- Verification history

**Key Functions**:

- `startVerification()` - Begin face check
- `processFrame()` - Analyze camera frame
- `displayResults()` - Show match results
- `toggleProvider()` - Switch between providers

#### 5. video-analysis.html - Video Processing

**Purpose**: Upload and analyze videos

**Features**:

- Drag-and-drop video upload
- Upload progress bar
- Processing status indicator
- Transcript display
- Sentiment analysis results
- Timeline view
- Download transcript
- Export results

**Key Functions**:

- `uploadVideo()` - Send file to server
- `checkStatus()` - Poll processing status
- `displayTranscript()` - Show text results
- `displaySentiments()` - Visualize sentiment data

### Frontend JavaScript Architecture

#### Authentication Flow

```javascript
// 1. Login/Register
const authResponse = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password })
});

// 2. Store tokens
const data = await authResponse.json();
localStorage.setItem('accessToken', data.token);
localStorage.setItem('refreshToken', data.refreshToken);

// 3. Include token in subsequent requests
const protectedResponse = await fetch('/api/face/list', {
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
  }
});

// 4. Handle token expiration
if (response.status === 401) {
  await refreshAccessToken();
  // Retry request
}
```

#### State Management

Frontend uses localStorage for:

- JWT access token
- Refresh token
- User profile data
- UI preferences

---

## 🗄 Database Schema

### Entity Relationship Diagram

```
┌─────────────────┐         ┌─────────────────┐
│     User        │◄───────┤│      Role       │
├─────────────────┤         ├─────────────────┤
│ id (PK)         │         │ id (PK)         │
│ email           │         │ name            │
│ username        │         └─────────────────┘
│ password        │
│ enabled         │         ┌─────────────────┐
│ verified        │◄───────┤│ RefreshToken    │
│ created_at      │         ├─────────────────┤
│ updated_at      │         │ id (PK)         │
└─────────────────┘         │ token           │
        │                   │ user_id (FK)    │
        │                   │ expiry_date     │
        ▼                   └─────────────────┘
┌─────────────────┐
│PasswordResetToken│         ┌──────────────────────┐
├─────────────────┤         │    FaceUser          │
│ id (PK)         │         ├──────────────────────┤
│ token           │         │ id (PK)              │
│ user_id (FK)    │         │ name                 │
│ expiry_date     │         │ email                │
└─────────────────┘         │ department           │
                            │ position             │
                            │ face_encoding (BYTEA)│
                            │ aws_face_id          │
                            │ azure_face_id        │
                            │ created_at           │
                            └──────────────────────┘
                                     │
                                     ▼
                            ┌──────────────────────┐
                            │ FaceVerificationLog  │
                            ├──────────────────────┤
                            │ id (PK)              │
                            │ face_user_id (FK)    │
                            │ verification_result  │
                            │ confidence_score     │
                            │ provider             │
                            │ verified_at          │
                            └──────────────────────┘
```

### Table Definitions

#### 1. `users` Table

Stores user account information.

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT false,
    verified BOOLEAN DEFAULT false,
    verification_token VARCHAR(255),
    token_expiry TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Indexes**:

- Primary key on `id`
- Unique index on `email`
- Unique index on `username`
- Index on `verification_token`

#### 2. `roles` Table

Defines user roles for RBAC.

```sql
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE user_roles (
    user_id BIGINT REFERENCES users(id),
    role_id BIGINT REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);
```

**Default Roles**:

- `ROLE_USER` - Standard user
- `ROLE_ADMIN` - Administrator

#### 3. `refresh_tokens` Table

Stores refresh tokens for session management.

```sql
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 4. `password_reset_tokens` Table

One-time tokens for password reset.

```sql
CREATE TABLE password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 5. `face_users` Table

Face recognition registration data.

```sql
CREATE TABLE face_users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    department VARCHAR(255),
    position VARCHAR(255),
    face_encoding BYTEA,  -- Serialized face embedding
    aws_face_id VARCHAR(255),  -- AWS Rekognition ID
    azure_face_id VARCHAR(255),  -- Azure Face ID
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Indexes**:

- Primary key on `id`
- Index on `email`
- Index on `aws_face_id`
- Index on `azure_face_id`

#### 6. `face_verification_logs` Table

Audit trail for face verification attempts.

```sql
CREATE TABLE face_verification_logs (
    id BIGSERIAL PRIMARY KEY,
    face_user_id BIGINT REFERENCES face_users(id),
    verification_result BOOLEAN NOT NULL,
    confidence_score DOUBLE PRECISION,
    provider VARCHAR(50),  -- LOCAL, AZURE, AWS
    verified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Database Migrations (Flyway)

Migrations located in: `src/main/resources/db/migration/`

| Version | File | Description |
|---------|------|-------------|
| V1 | `V1__create_users_table.sql` | Initial users table |
| V2 | `V2__create_face_users_table.sql` | Face recognition tables |
| V3 | `V3__create_roles_table.sql` | RBAC implementation |
| V4 | `V4__add_username_to_users.sql` | Add username column |
| V6 | `V6__add_token_expiry_to_users.sql` | Email verification tokens |
| V7 | `V7__password_reset_token.sql` | Password reset feature |
| V8 | `V8__rename_password_reset_token.sql` | Token table refactoring |

---

## 🔒 Security Implementation

### Security Layer Architecture

```
Request → CORS Filter → JWT Filter → Spring Security → Controller
           ↓             ↓              ↓
        OPTIONS     Token Valid?    Authorities?
        Headers     Extract User    Grant Access
```

### Spring Security Configuration

Located in: `SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .csrf().disable()  // Disabled for stateless API
            .cors()  // Enable CORS
            .and()
            .sessionManagement()
                .sessionCreationPolicy(STATELESS)  // No sessions
            .and()
            .authorizeHttpRequests()
                .requestMatchers("/api/auth/**").permitAll()  // Public
                .requestMatchers("/api/face/**").authenticated()  // Protected
                .anyRequest().authenticated()
            .and()
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

### JWT Token Structure

#### Access Token Payload

```json
{
  "sub": "user@example.com",
  "userId": 123,
  "roles": ["ROLE_USER"],
  "iat": 1640000000,
  "exp": 1640003600
}
```

#### Token Generation

```java
public String generateToken(UserDetails userDetails) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("userId", user.getId());
    claims.put("roles", user.getRoles());
    
    return Jwts.builder()
        .setClaims(claims)
        .setSubject(userDetails.getUsername())
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
        .signWith(getSigningKey(), HS256)
        .compact();
}
```

### Authentication Flow

```
1. User submits login credentials
   ↓
2. AuthController validates credentials
   ↓
3. UserDetailsService loads user from database
   ↓
4. PasswordEncoder verifies password (BCrypt)
   ↓
5. JwtTokenProvider generates access + refresh tokens
   ↓
6. Tokens returned to client
   ↓
7. Client stores tokens in localStorage
   ↓
8. Client includes token in Authorization header
   ↓
9. JwtAuthenticationFilter validates token
   ↓
10. SecurityContextHolder sets authentication
    ↓
11. Request proceeds to controller
```

### Password Security

- **Hashing Algorithm**: BCrypt
- **Strength Factor**: 12 (2^12 = 4,096 iterations)
- **Salt**: Automatically generated per password
- **Storage**: Hashed password stored in database

Example:

```
Plain: MyPassword123
Hashed: $2a$12$KIXQvJQfXhKEJRGHYPSlHOqW6ht5L1qCLpz.nL3oVZEJM9Qp0qGfO
```

### CORS Configuration

Allows frontend to make cross-origin requests:

```yaml
allowed-origins: http://localhost:8080
allowed-methods: GET, POST, PUT, DELETE, OPTIONS
allowed-headers: Authorization, Content-Type
expose-headers: Authorization
allow-credentials: true
max-age: 3600
```

### Security Best Practices Implemented

✅ **Password Policy**

- Minimum 8 characters
- Complexity requirements (uppercase, lowercase, numbers)

✅ **Account Security**

- Email verification required
- Account lockout after failed attempts (future)
- Password reset with time-limited tokens

✅ **Token Security**

- Short-lived access tokens (1 hour)
- Refresh token rotation
- Token revocation on logout

✅ **API Security**

- No sensitive data in URLs
- HTTPS required in production
- Input validation on all endpoints

✅ **Database Security**

- Prepared statements (SQL injection prevention)
- Connection pooling with HikariCP
- Encrypted credentials via environment variables

---

## 📡 API Documentation

### Base URL

```
http://localhost:8080/api
```

### Authentication Endpoints

#### Register New User

```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "username": "johndoe",
  "password": "SecurePass123"
}
```

**Success Response (201)**:

```json
{
  "message": "User registered successfully. Please check your email for verification."
}
```

---

#### Login

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```

**Success Response (200)**:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "550e8400-e29b-41d4-a716...",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "username": "johndoe",
  "roles": ["ROLE_USER"]
}
```

---

#### Refresh Token

```http
POST /api/auth/refresh-token
Content-Type: application/json

{
  "refreshToken": "550e8400-e29b-41d4-a716..."
}
```

**Success Response (200)**:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "550e8400-e29b-41d4-a716...",
  "tokenType": "Bearer"
}
```

---

#### Verify Email

```http
GET /api/auth/verify?token={verificationToken}
```

**Success Response (200)**:

```json
{
  "message": "Email verified successfully. You can now log in."
}
```

---

#### Forgot Password

```http
POST /api/auth/forgot-password?email=user@example.com
```

**Success Response (200)**:

```json
{
  "message": "Password reset link sent to your email."
}
```

---

#### Reset Password

```http
POST /api/auth/reset-password
Content-Type: application/json

{
  "token": "password-reset-token",
  "newPassword": "NewSecurePass456"
}
```

**Success Response (200)**:

```json
{
  "message": "Password reset successfully."
}
```

---

### Face Recognition Endpoints

#### Register Face

```http
POST /api/face/register
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data

name: John Doe
email: john@example.com
department: Engineering
position: Software Engineer
provider: LOCAL
image: [binary file]
```

**Success Response (200)**:

```json
{
  "id": 1,
  "name": "John Doe",
  "email": "john@example.com",
  "department": "Engineering",
  "position": "Software Engineer",
  "message": "Face registered successfully"
}
```

---

#### Verify Face

```http
POST /api/face/verify
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data

provider: AZURE
image: [binary file]
```

**Success Response (200)**:

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
  "timestamp": "2026-01-27T10:30:00"
}
```

---

#### List All Faces

```http
GET /api/face/list
Authorization: Bearer {accessToken}
```

**Success Response (200)**:

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
    // ... more faces
  ],
  "total": 10
}
```

---

### Sentiment Analysis Endpoints

#### Analyze Text

```http
POST /api/sentiment/analyze
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "text": "I absolutely love this product! It works perfectly.",
  "provider": "AZURE"
}
```

**Success Response (200)**:

```json
{
  "sentiment": "Positive",
  "confidenceScores": {
    "positive": 0.98,
    "neutral": 0.01,
    "negative": 0.01
  },
  "provider": "AZURE",
  "processedAt": "2026-01-27T10:35:00"
}
```

---

### Video Analysis Endpoints

#### Upload Video

```http
POST /api/video/upload
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data

video: [binary file]
```

**Success Response (202)**:

```json
{
  "videoId": "uuid-1234-5678",
  "status": "Processing",
  "message": "Video uploaded successfully. Processing started."
}
```

---

#### Get Video Analysis Status

```http
GET /api/video/status/{videoId}
Authorization: Bearer {accessToken}
```

**Success Response (200)**:

```json
{
  "videoId": "uuid-1234-5678",
  "status": "Completed",
  "progress": 100,
  "transcriptionReady": true,
  "sentimentReady": true
}
```

---

#### Get Video Results

```http
GET /api/video/results/{videoId}
Authorization: Bearer {accessToken}
```

**Success Response (200)**:

```json
{
  "videoId": "uuid-1234-5678",
  "transcript": "Hello everyone, welcome to this amazing presentation...",
  "sentiments": [
    {
      "text": "Hello everyone, welcome to this amazing presentation",
      "sentiment": "Positive",
      "confidence": 0.92,
      "timestamp": "00:00:01"
    },
    // ... more segments
  ],
  "overallSentiment": "Positive",
  "processingTime": "45 seconds"
}
```

---

### Error Responses

All endpoints follow consistent error format:

**400 Bad Request**:

```json
{
  "error": "Bad Request",
  "message": "Invalid input data",
  "details": ["Email is required", "Password must be at least 8 characters"],
  "timestamp": "2026-01-27T10:40:00"
}
```

**401 Unauthorized**:

```json
{
  "error": "Unauthorized",
  "message": "Invalid or expired token",
  "timestamp": "2026-01-27T10:40:00"
}
```

**403 Forbidden**:

```json
{
  "error": "Forbidden",
  "message": "Insufficient permissions",
  "timestamp": "2026-01-27T10:40:00"
}
```

**404 Not Found**:

```json
{
  "error": "Not Found",
  "message": "Resource not found",
  "timestamp": "2026-01-27T10:40:00"
}
```

**500 Internal Server Error**:

```json
{
  "error": "Internal Server Error",
  "message": "An unexpected error occurred",
  "timestamp": "2026-01-27T10:40:00"
}
```

---

## ☁️ Cloud Integration

### AWS Services Integration

#### AWS Rekognition (Face Recognition)

- **Purpose**: Cloud-based face detection and comparison
- **Collection**: `qualtech-faces`
- **Min Confidence**: 90%
- **API Version**: AWS SDK 2.21.1

**Configuration**:

```yaml
aws:
  accessKeyId: ${AWS_ACCESS_KEY_ID}
  secretKey: ${AWS_SECRET_ACCESS_KEY}
  region: us-east-1
  rekognition:
    collection-id: qualtech-faces
    min-confidence: 90
```

**Key Methods**:

- `IndexFaces` - Register face in collection
- `SearchFacesByImage` - Find matching face
- `DeleteFaces` - Remove face from collection

#### AWS S3 (File Storage)

- **Purpose**: Store face images
- **Bucket**: `qualtech-ai-faces`
- **Prefix**: `faces/`
- **Max File Size**: 10MB

**Usage**:

```java
PutObjectRequest putRequest = PutObjectRequest.builder()
    .bucket("qualtech-ai-faces")
    .key("faces/user_123.jpg")
    .contentType("image/jpeg")
    .build();
s3Client.putObject(putRequest, RequestBody.fromBytes(imageBytes));
```

#### AWS Comprehend (Sentiment Analysis)

- **Purpose**: Text sentiment analysis
- **Language**: Auto-detect or specify
- **Supported**: 12+ languages

**Usage**:

```java
DetectSentimentRequest request = DetectSentimentRequest.builder()
    .text(textToAnalyze)
    .languageCode("en")
    .build();
DetectSentimentResponse response = comprehendClient.detectSentiment(request);
```

#### AWS Transcribe (Speech-to-Text)

- **Purpose**: Convert video audio to text
- **Format**: MP4, WAV, FLAC
- **Languages**: 30+ languages

---

### Azure Services Integration

#### Azure Face API

- **Purpose**: Face detection and verification
- **Endpoint**: Configured per region
- **Version**: 1.0-beta.2

**Configuration**:

```yaml
azure:
  face:
    endpoint: ${AZURE_FACE_ENDPOINT}
    key: ${AZURE_FACE_KEY}
    enabled: true
```

**Features**:

- Detect faces in image
- Verify two faces match
- Find similar faces
- Face landmarks detection

#### Azure Text Analytics (Sentiment)

- **Purpose**: Advanced sentiment analysis
- **Languages**: 90+ languages
- **Features**: Key phrase extraction, entity recognition

**Configuration**:

```yaml
azure:
  language:
    endpoint: ${AZURE_LANGUAGE_ENDPOINT}
    key: ${AZURE_LANGUAGE_KEY}
```

#### Azure Blob Storage

- **Purpose**: Store video files
- **Container**: `videos`
- **SAS Tokens**: Used for secure access

**Configuration**:

```yaml
azure:
  storage:
    connection-string: ${AZURE_STORAGE_CONNECTION_STRING}
    container-name: videos
```

#### Azure Speech Service

- **Purpose**: Video transcription
- **Real-time**: Supported
- **Continuous Recognition**: For long videos

**Configuration**:

```yaml
azure:
  speech:
    key: ${AZURE_SPEECH_KEY}
    region: ${AZURE_SPEECH_REGION}
```

---

### Provider Selection Strategy

The application supports multiple providers for redundancy and cost optimization:

1. **Local Provider** (Default for development)
   - No API costs
   - No internet required
   - Good for testing

2. **Azure Provider** (Default for production)
   - High accuracy
   - Good documentation
   - Competitive pricing

3. **AWS Provider** (Alternative/backup)
   - Proven reliability
   - Integration with S3
   - Scalability

**Switch Providers**:

```java
@Service
public class FaceRecognitionServiceImpl {
    
    public FaceVerificationResult verify(MultipartFile image, FaceProvider provider) {
        return switch (provider) {
            case LOCAL -> localFaceService.verify(image);
            case AZURE -> azureFaceService.verify(image);
            case AWS -> awsFaceService.verify(image);
        };
    }
}
```

---

## ⚙️ Configuration & Setup

### Environment Variables

Create `.env` file in project root:

```env
# Database
DB_URL=jdbc:postgresql://localhost:5432/qualtech_db
DB_USERNAME=postgres
DB_PASSWORD=yourpassword

# JWT
JWT_SECRET=your-very-long-secret-key-minimum-32-characters-for-security
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=86400000

# Email (Gmail)
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USERNAME=your-email@gmail.com
EMAIL_PASSWORD=your-app-password
EMAIL_FROM=your-email@gmail.com

# AWS
AWS_ACCESS_KEY_ID=your-aws-access-key
AWS_SECRET_ACCESS_KEY=your-aws-secret-key
AWS_REGION=us-east-1
AWS_S3_BUCKET_NAME=qualtech-ai-faces

# Azure
AZURE_FACE_ENDPOINT=https://your-region.api.cognitive.microsoft.com/
AZURE_FACE_KEY=your-azure-face-key
AZURE_LANGUAGE_ENDPOINT=https://your-region.cognitiveservices.azure.com/
AZURE_LANGUAGE_KEY=your-azure-language-key
AZURE_SPEECH_KEY=your-azure-speech-key
AZURE_SPEECH_REGION=your-region
AZURE_STORAGE_CONNECTION_STRING=your-connection-string
AZURE_CONTAINER_NAME=videos

# Application
APP_FRONTEND_URL=http://localhost:8080
```

### Local Development Setup

#### Prerequisites

1. **Java 21 JDK**

   ```bash
   java -version  # Should show Java 21
   ```

2. **PostgreSQL**

   ```bash
   # Install PostgreSQL
   # Create database
   createdb qualtech_db
   ```

3. **Maven**

   ```bash
   mvn -version  # Should show Maven 3.8+
   ```

#### Steps

1. **Clone Repository**

   ```bash
   git clone http://10.1.4.100/gaurav.mishra/qualtech-ai.git
   cd qualtech-ai
   ```

2. **Configure Database**

   ```sql
   CREATE DATABASE qualtech_db;
   ```

3. **Set Environment Variables**
   - Copy `.env.example` to `.env`
   - Update with your credentials

4. **Download Face Models** (for local provider)

   ```bash
   download_face_models.bat
   ```

   This downloads:
   - `deploy.prototxt` - Network architecture
   - `res10_300x300_ssd_iter_140000.caffemodel` - Pre-trained weights

5. **Build Application**

   ```bash
   ./mvnw clean package -DskipTests
   ```

6. **Run Application**

   ```bash
   ./mvnw spring-boot:run
   ```

   Or:

   ```bash
   java -jar target/qualtech-ai.jar
   ```

7. **Access Application**
   - Open browser: `http://localhost:8080`
   - Register new account
   - Verify email
   - Login and explore features

---

## 🚀 Deployment Guide

### Production Deployment Checklist

#### 1. Security

- [ ] Change default JWT secret
- [ ] Use strong database passwords
- [ ] Enable HTTPS/SSL
- [ ] Configure CORS for production domain
- [ ] Use environment-specific configurations
- [ ] Enable database SSL connections
- [ ] Set up firewall rules
- [ ] Disable debug logging

#### 2. Database

- [ ] Create production database
- [ ] Run Flyway migrations
- [ ] Set up database backups
- [ ] Configure connection pooling
- [ ] Enable query logging for monitoring
- [ ] Set up read replicas (if needed)

#### 3. Application

- [ ] Build production JAR: `mvn clean package -DskipTests`
- [ ] Configure external properties file
- [ ] Set up logging (file appenders)
- [ ] Configure actuator endpoints
- [ ] Set up health checks
- [ ] Configure graceful shutdown

#### 4. Cloud Services

- [ ] Create AWS S3 bucket (production)
- [ ] Set up Azure resources (production)
- [ ] Configure service quotas
- [ ] Set up monitoring and alerts
- [ ] Enable cost tracking

#### 5. Monitoring

- [ ] Set up Spring Boot Actuator endpoints
- [ ] Configure application metrics
- [ ] Set up log aggregation
- [ ] Configure error tracking (Sentry, etc.)
- [ ] Set up uptime monitoring

### Deployment Options

#### Option 1: Traditional Server (Linux)

1. **Install Java 21**

   ```bash
   sudo apt update
   sudo apt install openjdk-21-jdk
   ```

2. **Install PostgreSQL**

   ```bash
   sudo apt install postgresql postgresql-contrib
   sudo -u postgres createdb qualtech_db
   ```

3. **Transfer Application**

   ```bash
   scp target/qualtech-ai.jar user@server:/opt/qualtech-ai/
   scp .env user@server:/opt/qualtech-ai/
   ```

4. **Create Systemd Service**
   `/etc/systemd/system/qualtech-ai.service`:

   ```ini
   [Unit]
   Description=Qualtech AI Application
   After=network.target postgresql.service
   
   [Service]
   Type=simple
   User=qualtech
   WorkingDirectory=/opt/qualtech-ai
   ExecStart=/usr/bin/java -jar qualtech-ai.jar
   EnvironmentFile=/opt/qualtech-ai/.env
   Restart=always
   RestartSec=10
   
   [Install]
   WantedBy=multi-user.target
   ```

5. **Start Service**

   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable qualtech-ai
   sudo systemctl start qualtech-ai
   sudo systemctl status qualtech-ai
   ```

6. **Set Up Nginx Reverse Proxy**
   `/etc/nginx/sites-available/qualtech-ai`:

   ```nginx
   server {
       listen 80;
       server_name your-domain.com;
       
       location / {
           proxy_pass http://localhost:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }
   ```

7. **Enable SSL with Let's Encrypt**

   ```bash
   sudo apt install certbot python3-certbot-nginx
   sudo certbot --nginx -d your-domain.com
   ```

---

#### Option 2: Docker Deployment

1. **Create Dockerfile**

   ```dockerfile
   FROM eclipse-temurin:21-jdk-alpine
   
   WORKDIR /app
   
   COPY target/qualtech-ai.jar app.jar
   COPY face_models/ face_models/
   
   EXPOSE 8080
   
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```

2. **Create docker-compose.yml**

   ```yaml
   version: '3.8'
   
   services:
     postgres:
       image: postgres:15-alpine
       environment:
         POSTGRES_DB: qualtech_db
         POSTGRES_USER: ${DB_USERNAME}
         POSTGRES_PASSWORD: ${DB_PASSWORD}
       ports:
         - "5432:5432"
       volumes:
         - postgres_data:/var/lib/postgresql/data
     
     app:
       build: .
       ports:
         - "8080:8080"
       environment:
         DB_URL: jdbc:postgresql://postgres:5432/qualtech_db
         DB_USERNAME: ${DB_USERNAME}
         DB_PASSWORD: ${DB_PASSWORD}
         JWT_SECRET: ${JWT_SECRET}
       depends_on:
         - postgres
       env_file:
         - .env
   
   volumes:
     postgres_data:
   ```

3. **Deploy**

   ```bash
   docker-compose up -d
   ```

---

#### Option 3: Cloud Platform (AWS Elastic Beanstalk)

1. **Install EB CLI**

   ```bash
   pip install awsebcli
   ```

2. **Initialize**

   ```bash
   eb init -p java-17 qualtech-ai
   ```

3. **Create Environment**

   ```bash
   eb create production-env
   ```

4. **Deploy**

   ```bash
   eb deploy
   ```

5. **Set Environment Variables**

   ```bash
   eb setenv DB_URL=... JWT_SECRET=... (etc.)
   ```

---

## 🔧 Troubleshooting

### Common Issues

#### 1. Application Won't Start

**Problem**: `Port 8080 already in use`

```
Error starting ApplicationContext. To display the conditions report re-run your application with 'debug' enabled.
Web server failed to start. Port 8080 was already in use.
```

**Solution**:

```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/Mac
lsof -ti:8080 | xargs kill -9

# Or change port in application.yml
server:
  port: 8081
```

---

#### 2. Database Connection Failed

**Problem**: `Connection to localhost:5432 refused`

**Solution**:

1. Verify PostgreSQL is running:

   ```bash
   # Windows
   sc query postgresql-x64-15
   
   # Linux
   sudo systemctl status postgresql
   ```

2. Check credentials in `.env` or `application.yml`

3. Verify database exists:

   ```sql
   psql -U postgres -l
   ```

4. Check PostgreSQL logs:

   ```bash
   # Linux
   sudo tail -f /var/log/postgresql/postgresql-15-main.log
   ```

---

#### 3. JWT Token Invalid

**Problem**: `401 Unauthorized - Invalid or expired token`

**Causes**:

- Token expired (1 hour lifetime)
- JWT secret changed
- Token malformed

**Solution**:

1. Refresh token:

   ```javascript
   const response = await fetch('/api/auth/refresh-token', {
     method: 'POST',
     body: JSON.stringify({ refreshToken: localStorage.getItem('refreshToken') })
   });
   ```

2. Re-login

3. Check JWT secret is consistent

---

#### 4. Email Not Sending

**Problem**: `Mail server connection failed`

**Solutions**:

1. **For Gmail**:
   - Enable 2FA
   - Generate App Password
   - Use app password in configuration

2. **Check SMTP settings**:

   ```yaml
   spring:
     mail:
       host: smtp.gmail.com
       port: 587
       username: your-email@gmail.com
       password: app-password  # Not regular password!
       properties:
         mail.smtp.auth: true
         mail.smtp.starttls.enable: true
   ```

3. **Test SMTP connection**:

   ```bash
   telnet smtp.gmail.com 587
   ```

---

#### 5. Face Recognition Not Working

**Problem**: `Face models not found`

**Solution**:

1. Download models:

   ```bash
   download_face_models.bat
   ```

2. Verify files exist:

   ```
   src/main/resources/face_models/
   ├── deploy.prototxt
   └── res10_300x300_ssd_iter_140000.caffemodel
   ```

**Problem**: `Azure Face API disabled`

**Solution**:

1. Check Azure credentials:

   ```yaml
   azure:
     face:
       endpoint: https://your-region.api.cognitive.microsoft.com/
       key: your-key
       enabled: true
   ```

2. Verify API endpoint is accessible:

   ```bash
   curl -I https://your-region.api.cognitive.microsoft.com/
   ```

---

#### 6. File Upload Too Large

**Problem**: `Maximum upload size exceeded`

**Solution**:
Update `application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB

server:
  tomcat:
    max-swallow-size: -1
```

---

#### 7. CORS Errors

**Problem**: `Access to fetch at 'http://localhost:8080/api' from origin 'http://localhost:3000' has been blocked by CORS policy`

**Solution**:
Add CORS configuration:

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

---

### Performance Optimization

#### 1. Database Connection Pooling

Already configured with HikariCP:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

#### 2. JPA Performance

Batch operations enabled:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 25
          order_inserts: true
          order_updates: true
```

#### 3. Caching (Optional Enhancement)

Add Spring Cache:

```java
@EnableCaching
@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("faces", "users");
    }
}

// Usage
@Cacheable(value = "faces", key = "#id")
public FaceUser findById(Long id) {
    return faceUserRepository.findById(id).orElseThrow();
}
```

---

## 📞 Support & Contact

### Documentation

- Full Documentation: `FULL_DOCUMENTATION.md`
- README: `README.md`
- API Examples: Postman collection

### Author

**Gaurav Mishra**  
Email: <gaurav.mishra.cs.2022@mitmeerut.ac.in>  
Organization: Qualtech

### Version History

- **v0.0.1-SNAPSHOT** - Initial release
- Current: Active development

---

## 📄 License

This project is proprietary software developed for **Qualtech**.  
For internal use only. All rights reserved.

---

**Built with ❤️ using Spring Boot, Java 21, and AI/ML technologies**
