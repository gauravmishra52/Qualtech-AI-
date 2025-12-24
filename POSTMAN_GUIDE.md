# Qualtech AI - Postman API Guide

This guide provides detailed documentation for testing the Qualtech AI API endpoints using Postman. You can test the API either locally or from any device on your network.

## Base URLs

### 1. Local Development
```
http://localhost:8080/api
```

### 2. Network Testing (Replace with your IP)
```
http://YOUR_IP:8080/api
```

## Authentication Endpoints

### 1. Register New User
- **Endpoint:** `/auth/register`
- **Method:** `POST`
- **Description:** Register a new user account
- **Request Body (JSON):**
  ```json
  {
    "username": "testuser",
    "email": "test@example.com",
    "password": "SecurePass123!",
    "confirmPassword": "SecurePass123!"
  }
  ```
- **Success Response (201 Created):**
  ```json
  {
    "message": "User registered successfully. Please check your email to verify your account.",
    "userId": "123e4567-e89b-12d3-a456-426614174000"
  }
  ```
- **Error Responses:**
  - 400: Invalid input data
  - 409: Email already exists

### 2. User Login
- **Endpoint:** `/auth/login`
- **Method:** `POST`
- **Description:** Authenticate user and get access token
- **Request Body (JSON):**
  ```json
  {
    "email": "test@example.com",
    "password": "SecurePass123!"
  }
  ```
- **Success Response (200 OK):**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "email": "test@example.com",
    "username": "testuser",
    "expiresIn": 3600
  }
  ```
- **Error Responses:**
  - 400: Invalid credentials
  - 401: Email not verified
  - 404: User not found

### 3. Verify Email
- **Endpoint:** `/auth/verify`
- **Method:** `GET`
- **Description:** Verify user's email address using the token sent to their email
- **Query Parameters:**
  - `token`: Verification token (required)
- **Example URL:**
  ```
  http://localhost:8080/api/auth/verify?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
  ```
- **Success Response (200 OK):**
  ```json
  {
    "message": "Email verified successfully"
  }
  ```
- **Error Responses:**
  - 400: Invalid or expired token
  - 404: User not found

## Testing in Postman

1. **Import the Collection**
   - Click "Import" in Postman
   - Import the following collection:
     ```json
     {
       "info": {
         "name": "Qualtech AI API",
         "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
       },
       "item": [
         {
           "name": "Register",
           "request": {
             "method": "POST",
             "header": [{"key": "Content-Type", "value": "application/json"}],
             "body": {
               "mode": "raw",
               "raw": "{\n                \"username\": \"testuser\",\n                \"email\": \"test@example.com\",\n                \"password\": \"SecurePass123!\",\n                \"confirmPassword\": \"SecurePass123!\"\n              }"
             },
             "url": {
               "raw": "{{base_url}}/auth/register",
               "host": ["{{base_url}}"],
               "path": ["auth", "register"]
             }
           }
         },
         {
           "name": "Login",
           "request": {
             "method": "POST",
             "header": [{"key": "Content-Type", "value": "application/json"}],
             "body": {
               "mode": "raw",
               "raw": "{
                \"email\": \"test@example.com\",
                \"password\": \"SecurePass123!\"
              }"
             },
             "url": {
               "raw": "{{base_url}}/auth/login",
               "host": ["{{base_url}}"],
               "path": ["auth", "login"]
             }
           }
         }
       ]
     }
     ```

2. **Set Up Environment**
   - Create a new environment
   - Add a variable `base_url` with value `http://localhost:8080/api` or your network IP
   - Save the environment and select it

3. **Test the Endpoints**
   - Start with the Register endpoint to create a new user
   - Check your email for the verification link
   - Use the Login endpoint to get an authentication token
   - Use the token in the `Authorization` header for protected endpoints

## Troubleshooting

- **Connection Refused:** Ensure your backend server is running
- **401 Unauthorized:** Verify your token is valid and not expired
- **404 Not Found:** Check if the endpoint URL is correct
- **500 Internal Server Error:** Check server logs for detailed error information
### 💡 Quick Tips for Postman:
1. **Body:** For `Register` and `Login`, use `Body` -> `raw` -> `JSON`.
2. **Token:** When you get the verification email, copy the token (the long string of letters and numbers) and paste it into the `Verify Email` URL.
3. **CORS:** Ensure your backend is running, otherwise you will get a "Connection Refused" error.

> [!IMPORTANT]
> Always make sure your **Spring Boot** application is started before sending requests in Postman!
