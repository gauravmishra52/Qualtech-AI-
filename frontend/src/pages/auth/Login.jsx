import { useState } from "react";
import { useNavigate, useLocation } from 'react-router-dom';
import { useForm } from '../../hooks/useForm';
import { login as loginService } from '../../api/authService';
import { setAuthTokens, getRememberMe } from '../../utils/auth';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import Alert from '../../components/common/Alert';
import './Auth.css';

// Form validation function
const validateLogin = (values) => {
  const errors = {};

  if (!values.email) {
    errors.email = 'Email is required';
  } else if (!/\S+@\S+\.\S+/.test(values.email)) {
    errors.email = 'Email is invalid';
  }

  if (!values.password) {
    errors.password = 'Password is required';
  } else if (values.password.length < 6) {
    errors.password = 'Password must be at least 6 characters';
  }

  return errors;
};

const Login = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const returnUrl = new URLSearchParams(location.search).get('returnUrl') || '/dashboard';
  const rememberMe = getRememberMe();

  const {
    values,
    errors,
    isSubmitting,
    handleChange,
    handleSubmit: handleFormSubmit,
    setErrors
  } = useForm(
    {
      email: '',
      password: '',
      rememberMe
    },
    validateLogin
  );

  const handleSubmit = async (formValues) => {
    try {
      const { email, password, rememberMe } = formValues;
      const response = await loginService({ email, password });

      // Save tokens based on remember me preference
      setAuthTokens({
        token: response.token,
        refreshToken: response.refreshToken,
        rememberMe
      });

      // Redirect to the original URL or dashboard
      navigate(returnUrl, { replace: true });
    } catch (error) {
      const message = error.response?.data?.error || error.response?.data?.message || 'Login failed. Please try again.';
      setErrors({ form: message });
      console.error('Login error:', error);
    }
  };

  // Handle remember me change
  const handleRememberMeChange = (e) => {
    handleChange({
      target: {
        name: 'rememberMe',
        value: e.target.checked
      }
    });
  };

  return (
    <div className="auth-container">
      <div className="auth-form">
        <h2>Login</h2>

        {/* Display form-level errors */}
        {errors.form && (
          <Alert type="error" message={errors.form} onClose={() => setErrors({ ...errors, form: '' })} />
        )}

        <form onSubmit={(e) => handleFormSubmit(e, handleSubmit)}>
          <div className="form-group">
            <label htmlFor="email">Email:</label>
            <input
              id="email"
              type="email"
              name="email"
              value={values.email}
              onChange={handleChange}
              className={errors.email ? 'error' : ''}
              disabled={isSubmitting}
            />
            {errors.email && <div className="error-message">{errors.email}</div>}
          </div>

          <div className="form-group">
            <label htmlFor="password">Password:</label>
            <input
              id="password"
              type="password"
              name="password"
              value={values.password}
              onChange={handleChange}
              className={errors.password ? 'error' : ''}
              disabled={isSubmitting}
            />
            {errors.password && <div className="error-message">{errors.password}</div>}
          </div>

          <div className="form-group remember-me">
            <label className="checkbox-container">
              <input
                type="checkbox"
                name="rememberMe"
                checked={values.rememberMe}
                onChange={handleRememberMeChange}
                disabled={isSubmitting}
              />
              <span className="checkmark"></span>
              Remember me
            </label>
            <a href="/forgot-password" className="forgot-password">
              Forgot password?
            </a>
          </div>

          <button
            type="submit"
            className="btn-primary"
            disabled={isSubmitting}
          >
            {isSubmitting ? <LoadingSpinner size="sm" /> : 'Login'}
          </button>
        </form>

        <div className="auth-footer">
          <p>Don't have an account? <a href="/register">Register here</a></p>
        </div>
      </div>
    </div>
  );
};

export default Login;