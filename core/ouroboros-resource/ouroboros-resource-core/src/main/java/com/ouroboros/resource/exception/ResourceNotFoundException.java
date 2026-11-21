package com.ouroboros.resource.exception;

import com.ouroboros.exception.BusinessException;

/**
 * Exception thrown when a resource is not found
 */
public class ResourceNotFoundException extends BusinessException {

    private static final Integer DEFAULT_ERROR_CODE = -4001;

    public ResourceNotFoundException(String path) {
        super(DEFAULT_ERROR_CODE, "Resource not found: " + path);
    }

    public ResourceNotFoundException(String path, Throwable cause) {
        super(DEFAULT_ERROR_CODE, "Resource not found: " + path, cause);
    }

    public ResourceNotFoundException(Integer errorCode, String message) {
        super(errorCode, message);
    }

    public ResourceNotFoundException(Integer errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
