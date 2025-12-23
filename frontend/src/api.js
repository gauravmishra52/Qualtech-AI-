import axios from "axios";

const api = axios.create({
  baseURL: "http://localhost:8080/api",
  headers: {
    "Content-Type": "application/json"
  },
  withCredentials: true
});




// ========================
// REQUEST INTERCEPTOR
// ========================
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ========================
// RESPONSE INTERCEPTOR (REFRESH TOKEN)
// ========================
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshToken = localStorage.getItem("refreshToken");
        if (!refreshToken) {
          logout();
          return Promise.reject(error);
        }

        const response = await axios.post(
          "http://localhost:8080/api/auth/refresh-token",
          { refreshToken }
        );

        const { token, refreshToken: newRefreshToken } = response.data;

        localStorage.setItem("token", token);
        localStorage.setItem("refreshToken", newRefreshToken);

        originalRequest.headers.Authorization = `Bearer ${token}`;
        return api(originalRequest);
      } catch (refreshError) {
        logout();
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

// ========================
// AUTH FUNCTIONS
// ========================
const login = async (credentials) => {
  const response = await api.post("/auth/login", credentials);
  const { token, refreshToken } = response.data;

  localStorage.setItem("token", token);
  localStorage.setItem("refreshToken", refreshToken);

  return response.data;
};

const logout = () => {
  localStorage.removeItem("token");
  localStorage.removeItem("refreshToken");
  window.location.href = "/login";
};

const isAuthenticated = () => {
  return !!localStorage.getItem("token");
};

// ========================
// EXPORTS
// ========================
export { api, login, logout, isAuthenticated };
export default api;
