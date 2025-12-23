import api from './api';

export const login = async (credentials) => {
  const response = await api.post('/auth/login', credentials);
  return response.data;
};

export const register = async (userData) => {
  const response = await api.post('/auth/register', userData);
  return response.data;
};

export const logout = async () => {
  // Clear token from localStorage
  localStorage.removeItem('token');
  // Optionally call backend to invalidate token
  // await api.post('/auth/logout');
};

export const requestPasswordReset = async (email) => {
  const response = await api.post(`/auth/forgot-password?email=${email}`);
  return response.data;
};

export const resetPassword = async (token, newPassword) => {
  const response = await api.post('/auth/reset-password', { token, newPassword });
  return response.data;
};

export const verifyEmailToken = async (token) => {
  const response = await api.get(`/auth/verify?token=${token}`);
  return response.data;
};

export const refreshToken = async () => {
  const response = await api.post('/auth/refresh-token');
  return response.data;
};
