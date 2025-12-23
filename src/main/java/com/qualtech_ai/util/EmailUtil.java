package com.qualtech_ai.util;

public class EmailUtil {

    private EmailUtil() {
        // utility class
    }

    public static String verificationEmailBody(String link) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 10px;">
                        <h2 style="color: #007bff; text-align: center;">Welcome to Qualtech AI!</h2>
                        <p>Hello,</p>
                        <p>Thank you for registering. Please click the button below to verify your email address and activate your account:</p>
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" style="background-color: #007bff; color: white; padding: 12px 25px; text-decoration: none; border-radius: 5px; font-weight: bold; display: inline-block;">Verify Email Address</a>
                        </div>
                        <p>If the button doesn't work, you can also copy and paste the following link into your browser:</p>
                        <p style="word-break: break-all; color: #007bff;">%s</p>
                        <p>Valid for 24 hours.</p>
                        <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
                        <p style="font-size: 0.9em; color: #777; text-align: center;">If you did not create an account, please ignore this email.</p>
                        <p style="font-size: 0.9em; color: #777; text-align: center;">Regards,<br>Qualtech AI Team</p>
                    </div>
                </body>
                </html>
                """
                .formatted(link, link);
    }

    public static String resetPasswordEmailBody(String token) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #ddd; border-radius: 10px;">
                        <h2 style="color: #dc3545; text-align: center;">Password Reset Request</h2>
                        <p>Hello,</p>
                        <p>You requested to reset your password. Use the token below to complete the process:</p>
                        <div style="text-align: center; margin: 30px 0; background-color: #f8f9fa; padding: 15px; border-radius: 5px; font-size: 1.2em; font-weight: bold; letter-spacing: 2px;">
                            %s
                        </div>
                        <p>Enter this token in the password reset page to set a new password.</p>
                        <p>If you did not request this, please ignore this email.</p>
                        <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
                        <p style="font-size: 0.9em; color: #777; text-align: center;">Regards,<br>Qualtech AI Team</p>
                    </div>
                </body>
                </html>
                """
                .formatted(token);
    }
}
