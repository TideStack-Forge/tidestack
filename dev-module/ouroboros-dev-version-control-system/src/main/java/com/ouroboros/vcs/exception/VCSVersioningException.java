package com.ouroboros.vcs.exception;

public class VCSVersioningException extends VCSException {
    private final String revision;
    private final String operation;

    public VCSVersioningException(VCSErrorCode errorCode, String operation, String revision) {
        super(errorCode, operation, revision);
        this.operation = operation;
        this.revision = revision;
    }

    public VCSVersioningException(VCSErrorCode errorCode, Throwable cause, String operation, String revision) {
        super(errorCode, cause, operation, revision);
        this.operation = operation;
        this.revision = revision;
    }

    public static VCSVersioningException commitFailed(String reason) {
        return new VCSVersioningException(VCSErrorCode.COMMIT_FAILED, reason, "");
    }

    public static VCSVersioningException commitFailed(Throwable cause, String reason) {
        return new VCSVersioningException(VCSErrorCode.COMMIT_FAILED, cause, reason, "");
    }

    public static VCSVersioningException branchNotFound(String branchName) {
        return new VCSVersioningException(VCSErrorCode.BRANCH_NOT_FOUND, "checkout", branchName);
    }

    public static VCSVersioningException tagNotFound(String tagName) {
        return new VCSVersioningException(VCSErrorCode.TAG_NOT_FOUND, "checkout", tagName);
    }

    public static VCSVersioningException revisionNotFound(String revision) {
        return new VCSVersioningException(VCSErrorCode.REVISION_NOT_FOUND, "checkout", revision);
    }

    public static VCSVersioningException gitOperationFailed(Throwable cause, String operation) {
        return new VCSVersioningException(VCSErrorCode.GIT_OPERATION_FAILED, cause, operation, "");
    }

    public String getRevision() {
        return revision;
    }

    public String getOperation() {
        return operation;
    }
}