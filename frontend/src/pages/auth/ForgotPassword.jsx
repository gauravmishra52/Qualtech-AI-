import { useState } from "react";
import { requestPasswordReset } from "../../api/authService";
import { useNavigate } from "react-router-dom";

const ForgotPassword = () => {
    const [email, setEmail] = useState("");
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            const data = await requestPasswordReset(email);
            setMessage(data.message);
            setError("");
        } catch (err) {
            setError(err.response?.data?.error || "Error sending reset link");
            setMessage("");
        }
    };

    return (
        <div style={{ maxWidth: "400px", margin: "50px auto", textAlign: "center" }}>
            <h2>Forgot Password</h2>
            <form onSubmit={handleSubmit}>
                <input
                    type="email"
                    placeholder="Enter your email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    style={{ width: "100%", padding: "10px", marginBottom: "10px" }}
                />
                <button type="submit" style={{ width: "100%", padding: "10px" }}>Send Reset Link</button>
            </form>
            {message && <p style={{ color: "green" }}>{message}</p>}
            {error && <p style={{ color: "red" }}>{error}</p>}
            <button onClick={() => navigate("/login")} style={{ marginTop: "10px", background: "none", border: "none", color: "blue", cursor: "pointer" }}>Back to Login</button>
        </div>
    );
};

export default ForgotPassword;
