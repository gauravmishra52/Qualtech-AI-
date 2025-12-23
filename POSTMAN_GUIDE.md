# Qualtech AI - Postman Verification Guide

Since you have updated your application to support mobile access, here are the correct URLs to use in Postman.

## 1. Laptop Testing (Local)
If you are running Postman on the **same laptop** as the code:

| Action | Method | URL |
| :--- | :--- | :--- |
| **Register** | `POST` | `http://localhost:8080/api/auth/register` |
| **Login** | `POST` | `http://localhost:8080/api/auth/login` |
| **Verify Email** | `GET` | `http://localhost:8080/api/auth/verify?token=YOUR_TOKEN` |

---

## 2. Mobile / Network Testing (IP)
If you want to test from **any device** on your Wi-Fi (using your IP `192.168.1.2`):

| Action | Method | URL |
| :--- | :--- | :--- |
| **Register** | `POST` | `http://192.168.1.2:8080/api/auth/register` |
| **Login** | `POST` | `http://192.168.1.2:8080/api/auth/login` |
| **Verify Email** | `GET` | `http://192.168.1.2:8080/api/auth/verify?token=YOUR_TOKEN` |

---

### 💡 Quick Tips for Postman:
1. **Body:** For `Register` and `Login`, use `Body` -> `raw` -> `JSON`.
2. **Token:** When you get the verification email, copy the token (the long string of letters and numbers) and paste it into the `Verify Email` URL.
3. **CORS:** Ensure your backend is running, otherwise you will get a "Connection Refused" error.

> [!IMPORTANT]
> Always make sure your **Spring Boot** application is started before sending requests in Postman!
