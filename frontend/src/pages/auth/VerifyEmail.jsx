import { useEffect, useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { verifyEmailToken } from "../../api/authService";

const VerifyEmail = () => {
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token");
    const [status, setStatus] = useState("verifying");
    const [message, setMessage] = useState("");
    const navigate = useNavigate();

    useEffect(() => {
        const verifyToken = async () => {
            if (!token) {
                setStatus("error");
                setMessage("Invalid or missing verification token.");
                return;
            }

            try {
                const data = await verifyEmailToken(token);
                setStatus("success");
                setMessage(data.message || "Email verified successfully!");
                // Redirect to login after 5 seconds
                setTimeout(() => {
                    navigate("/login");
                }, 5000);
            } catch (err) {
                setStatus("error");
                setMessage(err.response?.data?.error || "Verification failed. The link might be expired.");
            }
        };

        verifyToken();
    }, [token, navigate]);

    return (
        <div className="auth-container">
            <div className="auth-form" style={{ textAlign: "center" }}>
                <h2>Email Verification</h2>

                {status === "verifying" && (
                    <div className="verifying-status">
                        <p>Verifying your email, please wait...</p>
                        <div className="spinner"></div>
                    </div>
                )}

                {status === "success" && (
                    <div className="success-status">
                        <div style={{ color: "green", fontSize: "1.2rem", marginBottom: "20px" }}>
                            <p>✅ {message}</p>
                        </div>
                        <p>You can now access all features of Qualtech AI.</p>
                        <button
                            className="btn-primary"
                            onClick={() => navigate("/login")}
                            style={{ marginTop: "20px" }}
                        >
                            Go to Login
                        </button>
                        <p style={{ marginTop: "15px", fontSize: "0.9rem", color: "#666" }}>
                            Redirecting to login page in 5 seconds...
                        </p>
                    </div>
                )}

                {status === "error" && (
                    <div className="error-status">
                        <div style={{ color: "red", fontSize: "1.2rem", marginBottom: "20px" }}>
                            <p>❌ {message}</p>
                        </div>
                        <p>The verification link may be expired or invalid.</p>
                        <button
                            className="btn-primary"
                            onClick={() => navigate("/register")}
                            style={{ marginTop: "20px" }}
                        >
                            Back to Register
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
};

export default VerifyEmail;
