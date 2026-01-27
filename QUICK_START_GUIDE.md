# Qualtech-AI Quick Start Guide

## 🚀 5-Minute Setup

### Prerequisites Check

```bash
java -version      # Must be Java 21
mvn -version       # Must be Maven 3.8+
psql --version     # PostgreSQL installed
```

### Installation Steps

1. **Clone & Navigate**

   ```bash
   git clone http://10.1.4.100/gaurav.mishra/qualtech-ai.git
   cd qualtech-ai
   ```

2. **Database Setup**

   ```sql
   createdb qualtech_db
   ```

3. **Environment Configuration**
   Create `.env` file with minimum required:

   ```env
   DB_USERNAME=postgres
   DB_PASSWORD=yourpassword
   JWT_SECRET=your-minimum-32-character-secret-key-here
   EMAIL_USERNAME=your-email@gmail.com
   EMAIL_PASSWORD=your-app-password
   ```

4. **Download Face Models**

   ```bash
   download_face_models.bat
   ```

5. **Run Application**

   ```bash
   ./mvnw spring-boot:run
   ```

6. **Access Application**
   Open browser: <http://localhost:8080>

---

## 🎯 Common Tasks

### Register New User

```javascript
// Frontend: index.html
POST /api/auth/register
{
  "email": "user@example.com",
  "username": "johndoe",
  "password": "SecurePass123"
}
```

### Login

```javascript
POST /api/auth/login
{
  "email": "user@example.com",
  "password": "SecurePass123"
}
// Save token from response
```

### Register Face

```javascript
// Frontend: face-management.html
POST /api/face/register
FormData:
  - name: John Doe
  - email: john@example.com
  - department: Engineering
  - position: Developer
  - provider: LOCAL
  - image: [file]
```

### Verify Face

```javascript
// Frontend: face-comparison.html
POST /api/face/verify
FormData:
  - provider: AZURE
  - image: [file]
```

### Analyze Sentiment

```javascript
POST /api/sentiment/analyze
{
  "text": "This is amazing!",
  "provider": "AZURE"
}
```

---

## 🔑 Key Endpoints

| Feature | Endpoint | Method |
|---------|----------|--------|
| Register | `/api/auth/register` | POST |
| Login | `/api/auth/login` | POST |
| Register Face | `/api/face/register` | POST |
| Verify Face | `/api/face/verify` | POST |
| List Faces | `/api/face/list` | GET |
| Sentiment | `/api/sentiment/analyze` | POST |
| Video Upload | `/api/video/upload` | POST |

---

## 🔒 Authentication Header

All protected endpoints require:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

Store token in localStorage:

```javascript
localStorage.setItem('accessToken', token);
```

Include in requests:

```javascript
fetch('/api/face/list', {
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
  }
});
```

---

## 🏗️ Project Structure Overview

```
qualtech-ai/
├── src/main/java/com/qualtech_ai/
│   ├── controller/          # REST APIs
│   ├── service/             # Business logic
│   ├── repository/          # Database access
│   ├── entity/              # Database models
│   ├── security/            # JWT & auth
│   └── config/              # Configuration
├── src/main/resources/
│   ├── application.yml      # Main config
│   ├── db/migration/        # Database scripts
│   └── static/              # Frontend files
└── pom.xml                  # Dependencies
```

---

## 🐛 Quick Troubleshooting

| Problem | Solution |
|---------|----------|
| Port 8080 in use | Change port in `application.yml` or kill process |
| Database error | Check PostgreSQL running & credentials |
| Email not sending | Use Gmail App Password, not regular password |
| JWT invalid | Token expired - refresh or re-login |
| Face models missing | Run `download_face_models.bat` |
| CORS error | Check allowed origins in CORS config |

---

## 📦 Build Commands

```bash
# Clean build
./mvnw clean package

# Skip tests
./mvnw clean package -DskipTests

# Run tests
./mvnw test

# Run application
./mvnw spring-boot:run

# Run with profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## 🌐 Providers

### Local Provider (OpenCV)

- **Pros**: Free, offline, fast
- **Cons**: Lower accuracy
- **Use**: Development, testing

### Azure Provider

- **Pros**: High accuracy, multi-feature
- **Cons**: Requires API key, costs money
- **Use**: Production

### AWS Provider

- **Pros**: Scalable, reliable
- **Cons**: Requires credentials, costs
- **Use**: Enterprise scale

---

## 📊 Default Login

After registration, verify email to activate account.

**No default users** - you must register through:

1. Open <http://localhost:8080>
2. Click "Register"
3. Fill form
4. Check email for verification link
5. Click link to verify
6. Login with credentials

---

## 🔗 Useful Links

- **Application**: <http://localhost:8080>
- **API Base**: <http://localhost:8080/api>
- **Actuator**: <http://localhost:8080/actuator> (if enabled)
- **H2 Console**: <http://localhost:8080/h2-console> (test profile only)

---

## 📝 Frontend Pages

| Page | URL | Purpose |
|------|-----|---------|
| Login | `/index.html` | Authentication |
| Dashboard | `/dashboard.html` | Main hub |
| Face Management | `/face-management.html` | Register faces |
| Face Comparison | `/face-comparison.html` | Verify faces |
| Video Analysis | `/video-analysis.html` | Upload & analyze |

---

## 💡 Tips

1. **Use Environment Variables** for secrets (never hardcode)
2. **Enable HTTPS** in production
3. **Monitor API quotas** for Azure/AWS
4. **Backup database** regularly
5. **Test face recognition** in good lighting
6. **Use Gmail App Password** for email
7. **Keep dependencies updated** for security
8. **Log errors** for debugging
9. **Test in multiple browsers**
10. **Document API changes**

---

## 📞 Support

For detailed documentation, see `FULL_DOCUMENTATION.md`

**Author**: Gaurav Mishra  
**Email**: <gaurav.mishra.cs.2022@mitmeerut.ac.in>  
**Organization**: Qualtech

---

*Last Updated: 2026-01-27*
