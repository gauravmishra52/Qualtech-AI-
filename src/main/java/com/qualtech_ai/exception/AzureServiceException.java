package com.qualtech_ai.exception;

public class AzureServiceException extends RuntimeException {
    private final String service;
    private final String operation;

    public AzureServiceException(String service, String operation, String message) {
        super(String.format("[%s] %s: %s", service, operation, message));
        this.service = service;
        this.operation = operation;
    }

    public AzureServiceException(String service, String operation, String message, Throwable cause) {
        super(String.format("[%s] %s: %s", service, operation, message), cause);
        this.service = service;
        this.operation = operation;
    }

    public String getService() {
        return service;
    }

    public String getOperation() {
        return operation;
    }
}
