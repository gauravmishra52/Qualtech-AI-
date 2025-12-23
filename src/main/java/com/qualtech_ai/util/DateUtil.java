package com.qualtech_ai.util;

import java.time.LocalDateTime;

public class DateUtil {

    private DateUtil() {
        // utility class
    }

    public static LocalDateTime nowPlusMinutes(int minutes) {
        return LocalDateTime.now().plusMinutes(minutes);
    }

    public static boolean isExpired(LocalDateTime expiryTime) {
        return LocalDateTime.now().isAfter(expiryTime);
    }
    
    /**
     * Calculates the expiry date by adding the specified number of minutes to the current time.
     * @param expiryTimeInMinutes The number of minutes until expiry
     * @return The LocalDateTime representing the expiry time
     */
    public static LocalDateTime calculateExpiryDate(int expiryTimeInMinutes) {
        return LocalDateTime.now().plusMinutes(expiryTimeInMinutes);
    }
}

