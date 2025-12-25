package com.qualtech_ai.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class SpaErrorController implements ErrorController {

    @RequestMapping("/error")
    @SuppressWarnings("null")
    public Object handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            // For 404 errors on frontend routes, forward to index.html for SPA routing
            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                // Check if it's likely an API call (should return JSON error)
                String accept = request.getHeader("Accept");
                if (accept != null && accept.contains("application/json") && !accept.contains("text/html")) {
                    return ResponseEntity.status(statusCode)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\": \"Not Found\", \"status\": 404, \"path\": \"" + requestUri + "\"}");
                }

                // For browser requests, forward to index.html
                return "forward:/index.html";
            }

            // For other errors, return JSON error response
            String errorMessage = (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = HttpStatus.valueOf(statusCode).getReasonPhrase();
            }

            return ResponseEntity.status(statusCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"" + errorMessage + "\", \"status\": " + statusCode + "}");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"Unknown error occurred\"}");
    }
}
