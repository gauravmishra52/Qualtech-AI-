// Token management
const TOKEN_KEY = 'token';
const REFRESH_TOKEN_KEY = 'refreshToken';
const REMEMBER_ME_KEY = 'rememberMe';

// Get token from localStorage
export const getToken = () => localStorage.getItem(TOKEN_KEY);

// Get refresh token from localStorage
export const getRefreshToken = () => localStorage.getItem(REFRESH_TOKEN_KEY);

// Save tokens to storage
export const setAuthTokens = ({ token, refreshToken, rememberMe = false }) => {
  const storage = rememberMe ? localStorage : sessionStorage;
  
  if (token) {
    storage.setItem(TOKEN_KEY, token);
    localStorage.setItem(REMEMBER_ME_KEY, rememberMe);
  }
  
  if (refreshToken) {
    storage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }
};

// Remove auth tokens
export const removeAuthTokens = () => {
  // Clear from both storages to be safe
  [localStorage, sessionStorage].forEach(storage => {
    storage.removeItem(TOKEN_KEY);
    storage.removeItem(REFRESH_TOKEN_KEY);
  });
  localStorage.removeItem(REMEMBER_ME_KEY);
};

// Check if user is authenticated
export const isAuthenticated = () => {
  // Check token in both storages
  const token = localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY);
  
  if (!token) return false;
  
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp * 1000 > Date.now();
  } catch (e) {
    console.error('Error decoding token:', e);
    return false;
  }
};

// Get current user info from token
export const getCurrentUser = () => {
  const token = getToken();
  if (!token) return null;
  
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return {
      id: payload.sub,
      email: payload.email,
      roles: payload.roles || [],
      ...payload
    };
  } catch (e) {
    console.error('Error getting user from token:', e);
    return null;
  }
};

// Check if user has required role
export const hasRole = (requiredRole) => {
  const user = getCurrentUser();
  if (!user) return false;
  
  return user.roles?.includes(requiredRole) || false;
};

// Logout user
export const logout = () => {
  // Clear all auth data
  removeAuthTokens();
  
  // Clear any API authorization headers
  if (window.api?.defaults?.headers?.common?.['Authorization']) {
    delete window.api.defaults.headers.common['Authorization'];
  }
  
  // Redirect to login with a return URL
  const currentPath = window.location.pathname + window.location.search;
  const loginPath = currentPath !== '/' ? `/login?returnUrl=${encodeURIComponent(currentPath)}` : '/login';
  
  // Use window.location.replace to prevent back button from returning to protected pages
  window.location.href = loginPath;
};

// Get remember me preference
export const getRememberMe = () => {
  return localStorage.getItem(REMEMBER_ME_KEY) === 'true';
};

// Set remember me preference
export const setRememberMe = (remember) => {
  localStorage.setItem(REMEMBER_ME_KEY, remember);
  
  // If turning off remember me, move token to session storage
  if (!remember) {
    const token = localStorage.getItem(TOKEN_KEY);
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    
    if (token) {
      sessionStorage.setItem(TOKEN_KEY, token);
      localStorage.removeItem(TOKEN_KEY);
    }
    
    if (refreshToken) {
      sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
      localStorage.removeItem(REFRESH_TOKEN_KEY);
    }
  }
};