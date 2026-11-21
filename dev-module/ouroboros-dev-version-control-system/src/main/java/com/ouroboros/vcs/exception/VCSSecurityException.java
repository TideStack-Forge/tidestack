package com.ouroboros.vcs.exception;

public class VCSSecurityException extends VCSException {
    private final String path;
    private final String securityViolationType;

    public VCSSecurityException(VCSErrorCode errorCode, String path, String securityViolationType) {
        super(errorCode, path);
        this.path = path;
        this.securityViolationType = securityViolationType;
    }

    public VCSSecurityException(String path, String securityViolationType) {
        super(VCSErrorCode.SECURITY_VIOLATION, path);
        this.path = path;
        this.securityViolationType = securityViolationType;
    }

    public static VCSSecurityException pathTraversalAttack(String path) {
        return new VCSSecurityException(VCSErrorCode.PATH_TRAVERSAL_ATTACK, path, "路径遍历攻击");
    }

    public static VCSSecurityException pathValidationFailed(String path, String reason) {
        return new VCSSecurityException(VCSErrorCode.PATH_VALIDATION_FAILED, path, reason);
    }

    public static VCSSecurityException accessDenied(String path, String reason) {
        return new VCSSecurityException(VCSErrorCode.ACCESS_DENIED, path, reason);
    }

    public String getPath() {
        return path;
    }

    public String getSecurityViolationType() {
        return securityViolationType;
    }
}