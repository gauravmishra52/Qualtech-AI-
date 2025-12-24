# Qualtech-AI

A full-stack web application featuring secure authentication with JWT, email verification, and password reset functionality.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.5-green)
![React](https://img.shields.io/badge/React-18.2.0-blue)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Latest-blue)

---

## 🚀 Features

- **User Authentication** - Register, login, logout with JWT tokens
- **Email Verification** - Verify user emails before allowing login
- **Password Reset** - Forgot password flow with email-based reset
- **Token Refresh** - Automatic token refresh for seamless sessions
- **Role-Based Access** - User roles for authorization
- **Responsive UI** - Modern React frontend with Chakra UI

---

## 🛠️ Tech Stack

### Backend
| Technology | Purpose |
|------------|---------|
| **Java 21** | Core language |
| **Spring Boot 3.1.5** | Application framework |
| **Spring Security** | Authentication & authorization |
| **Spring Data JPA** | Database ORM |
| **PostgreSQL** | Database |
| **Flyway** | Database migrations |
| **JWT (jjwt 0.12.7)** | Token-based authentication |
| **Lombok** | Boilerplate code reduction |

### Frontend
| Technology | Purpose |
|------------|---------|
| **React 18** | UI framework |
| **Chakra UI** | Component library |
| **React Router 6** | Navigation |
| **Axios** | HTTP client |
| **Framer Motion** | Animations |

---

## 📁 Project Structure

```
qualtech-ai/
├── src/main/java/com/qualtech_ai/
│   ├── config/           # Security, CORS, JWT, Mail configs
│   ├── controller/       # REST API endpoints
│   ├── dto/              # Data Transfer Objects
│   ├── entity/           # JPA entities (User, Role, RefreshToken)
│   ├── exception/        # Custom exceptions & handlers
│   ├── repository/       # JPA repositories
│   ├── security/         # JWT filter, UserDetails
│   ├── service/          # Business logic
│   └── util/             # Utility classes
├── src/main/resources/
│   ├── application.yml   # Application configuration
│   └── db/migration/     # Flyway SQL migrations
├── frontend/
│   ├── src/
│   │   ├── api/          # API service calls
│   │   ├── components/   # Reusable React components
│   │   ├── hooks/        # Custom React hooks
│   │   ├── pages/        # Page components (Login, Register, etc.)
│   │   └── utils/        # Utility functions
│   └── package.json
└── pom.xml
```

---

## ⚙️ Prerequisites

- **Java 21** or higher
- **Maven 3.8+**
- **Node.js 18+** and npm
- **PostgreSQL** (running locally or via Docker)

---

## 🚀 Getting Started

### 1. Clone the Repository

```bash
git clone http://10.1.4.100/gaurav.mishra/qualtech-ai.git
cd qualtech-ai
```

### 2. Configure Database

Create a PostgreSQL database:

```sql
CREATE DATABASE qualtech_db;
```

Update `src/main/resources/application.yml` with your database credentials:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/qualtech_db
    username: your_username
    password: your_password
```

### 3. Configure Email (Optional)

Update email settings in `application.yml` for verification emails:

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your_email@gmail.com
    password: your_app_password
```

> **Note:** For Gmail, use an [App Password](https://support.google.com/accounts/answer/185833) instead of your regular password.

### 4. Run the Backend

```bash
# From project root
mvn spring-boot:run
```

The backend will start at `http://localhost:8080`

### 5. Run the Frontend

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
npm install

# Start development server
npm start
```

The frontend will start at `http://localhost:3000`

---

## 🔌 API Endpoints

### Authentication (`/api/auth`)

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `POST` | `/register` | Register new user | ❌ |
| `POST` | `/login` | Login user | ❌ |
| `POST` | `/logout` | Logout user | ✅ |
| `POST` | `/refresh-token` | Refresh JWT token | ❌ |
| `GET` | `/verify?token=` | Verify email | ❌ |
| `POST` | `/forgot-password?email=` | Request password reset | ❌ |
| `POST` | `/reset-password` | Reset password | ❌ |

### Request Examples

**Register:**
```json
POST /api/auth/register
{
  "email": "user@example.com",
  "password": "SecurePass123",
  "username": "johndoe"
}
```

**Login:**
```json
POST /api/auth/login
{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1...",
  "refreshToken": "abc123...",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "roles": ["ROLE_USER"]
}
```

---

## 📱 Mobile/Network Testing

To test from other devices on your network:

1. Find your computer's IP address:
   ```bash
   ipconfig  # Windows
   ifconfig  # macOS/Linux
   ```

2. Update `application.yml`:
   ```yaml
   app:
     frontend-url: http://YOUR_IP:3000
   ```

3. Access the app from any device at `http://YOUR_IP:3000`

> See [POSTMAN_GUIDE.md](./POSTMAN_GUIDE.md) for detailed API testing instructions.

---

## 🧪 Testing

### Backend Tests
```bash
mvn test
```

### Frontend Tests
```bash
cd frontend
npm test
```

---

## 📦 Build for Production

### Backend
```bash
mvn clean package -DskipTests
java -jar target/qualtech-ai-0.0.1-SNAPSHOT.jar
```

### Frontend
```bash
cd frontend
npm run build
```

The build output will be in `frontend/build/`.

---

## 🔒 Security Features

- **JWT Authentication** with access & refresh tokens
- **Password Encryption** using BCrypt
- **Email Verification** required before login
- **Token Expiration** - Access token: 1 hour, Refresh token: 24 hours
- **CORS Configuration** for secure cross-origin requests

---

## 📂 Database Migrations

Flyway manages database schema. Migrations are in `src/main/resources/db/migration/`:

| Version | Description |
|---------|-------------|
| V1 | Create users table |
| V2 | Create roles table |
| V3 | Password reset tokens |
| V4 | Add username to users |
| V5 | Add email verification fields |
| V6 | Add token expiry |
| V7 | Rename password reset token |

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is for internal use at Qualtech.

---

## 👨‍💻 Author

**Gaurav Mishra**

---

*Built with ❤️ using Spring Boot and React*
