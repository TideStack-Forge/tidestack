package com.ouroboros.resource.exception;

import com.ouroboros.exception.BusinessException;

/**
 * Exception thrown when access to a resource is denied
 */
public class ResourceAccessDeniedException extends BusinessException {

    private static final Integer DEFAULT_ERROR_CODE = -4003;

    public ResourceAccessDeniedException(String path) {
        super(DEFAULT_ERROR_CODE, "Access denied to resource: " + path);
    }

    public ResourceAccessDeniedException(Integer errorCode, String message) {
        super(errorCode, message);
    }

    public ResourceAccessDeniedException(Integer errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
