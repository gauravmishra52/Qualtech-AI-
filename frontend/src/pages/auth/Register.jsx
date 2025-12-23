import { useState } from "react";
import axios from "../../api/api";

import { useNavigate } from "react-router-dom";

const Register = () => {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    username: "",
    email: "",
    password: "",
    confirmPassword: ""
  });

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault(); // 🔴 THIS IS CRITICAL

    if (formData.password !== formData.confirmPassword) {
      setError("Passwords do not match");
      return;
    }

    try {
      await axios.post("/auth/register", {
        username: formData.username,
        email: formData.email,
        password: formData.password
      });

      setSuccess("Registration successful! Feedback: Please check your email to verify your account.");
      setError("");
      // Don't navigate immediately, let the user read the message
    } catch (err) {
      const data = err.response?.data;
      if (data) {
        if (typeof data === "string") {
          setError(data);
        } else if (data.error) {
          setError(data.error);
        } else {
          const validationErrors = Object.values(data).join(". ");
          setError(validationErrors);
        }
      } else {
        setError("Registration failed. Please try again.");
      }
      setSuccess("");
      console.error(err);
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <h2>Register</h2>

      {error && <p style={{ color: "red" }}>{error}</p>}
      {success && <p style={{ color: "green" }}>{success}</p>}

      <input
        name="username"
        placeholder="Username"
        value={formData.username}
        onChange={handleChange}
      />

      <input
        name="email"
        placeholder="Email"
        value={formData.email}
        onChange={handleChange}
      />

      <input
        type="password"
        name="password"
        placeholder="Password"
        value={formData.password}
        onChange={handleChange}
      />

      <input
        type="password"
        name="confirmPassword"
        placeholder="Confirm Password"
        value={formData.confirmPassword}
        onChange={handleChange}
      />

      <button type="submit">Register</button>
    </form>
  );
};

export default Register;
