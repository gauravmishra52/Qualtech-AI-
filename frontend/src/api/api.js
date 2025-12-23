import axios from 'axios';
import { getToken, removeAuthTokens } from '../utils/auth';
import { refreshToken } from './authService';

// Create axios instance with base URL
const api = axios.create({
  // To test on your PHONE, replace 'localhost' with your computer's IP address (e.g., http://192.168.1.10:8080/api)
  baseURL: process.env.REACT_APP_API_URL || 'http://192.168.1.2:8080/api',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

// Add request interceptor
api.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Add response interceptor for handling token refresh
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // If error is 401 and we haven't tried to refresh yet
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const { token } = await refreshToken();
        localStorage.setItem('token', token);

        // Update the authorization header
        originalRequest.headers.Authorization = `Bearer ${token}`;

        // Retry the original request
        return api(originalRequest);
      } catch (refreshError) {
        // If refresh token fails, log the user out
        removeAuthTokens();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default api;
