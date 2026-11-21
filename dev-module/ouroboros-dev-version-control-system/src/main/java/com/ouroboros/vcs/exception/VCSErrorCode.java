package com.ouroboros.vcs.exception;

public enum VCSErrorCode {
    // 通用错误
    GENERAL_ERROR("VCS-000", "VCS操作失败: %s"),

    // 文件系统错误 (100-199)
    FILE_NOT_FOUND("VCS-101", "文件不存在: %s"),
    FILE_ALREADY_EXISTS("VCS-102", "文件已存在: %s"),
    FILE_IS_DIRECTORY("VCS-103", "路径是目录而非文件: %s"),
    DIRECTORY_NOT_FOUND("VCS-104", "目录不存在: %s"),
    FILE_OPERATION_FAILED("VCS-105", "文件操作失败: %s"),
    FILE_READ_FAILED("VCS-106", "文件读取失败: %s"),
    FILE_WRITE_FAILED("VCS-107", "文件写入失败: %s"),

    // 锁定相关错误 (200-299)
    FILE_LOCKED("VCS-201", "文件已被锁定: %s (锁定者: %s)"),
    MULTIPLE_FILES_LOCKED("VCS-202", "多个文件被锁定: %s"),
    LOCK_OPERATION_FAILED("VCS-203", "锁定操作失败: %s"),
    UNLOCK_OPERATION_FAILED("VCS-204", "解锁操作失败: %s"),
    LOCK_PERMISSION_DENIED("VCS-205", "锁定权限不足: %s (当前用户: %s, 锁定者: %s)"),

    // 版本控制错误 (300-399)
    COMMIT_FAILED("VCS-301", "提交失败: %s"),
    BRANCH_NOT_FOUND("VCS-302", "分支不存在: %s"),
    TAG_NOT_FOUND("VCS-303", "标签不存在: %s"),
    REVISION_NOT_FOUND("VCS-304", "版本不存在: %s"),
    MERGE_CONFLICT("VCS-305", "合并冲突: %s"),
    CHECKOUT_FAILED("VCS-306", "检出失败: %s"),
    REPOSITORY_CORRUPTED("VCS-307", "仓库损坏: %s"),
    GIT_OPERATION_FAILED("VCS-308", "Git操作失败: %s"),

    // 安全相关错误 (400-499)
    PATH_TRAVERSAL_ATTACK("VCS-401", "检测到路径遍历攻击: %s"),
    SECURITY_VIOLATION("VCS-402", "安全违规: %s"),
    ACCESS_DENIED("VCS-403", "访问被拒绝: %s"),
    PATH_VALIDATION_FAILED("VCS-404", "路径验证失败: %s"),

    // 配置错误 (500-599)
    INVALID_CONFIGURATION("VCS-501", "配置无效: %s"),
    REPOSITORY_PATH_INVALID("VCS-502", "仓库路径无效: %s"),
    USER_NOT_CONFIGURED("VCS-503", "用户未配置: %s"),

    // 并发错误 (600-699)
    CONCURRENT_MODIFICATION("VCS-601", "并发修改冲突: %s"),
    LOCK_TIMEOUT("VCS-602", "锁定超时: %s"),
    DEADLOCK_DETECTED("VCS-603", "检测到死锁: %s");

    private final String code;
    private final String messageTemplate;

    VCSErrorCode(String code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    public String getCode() {
        return code;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }

    public String formatMessage(Object... parameters) {
        if (parameters == null || parameters.length == 0) {
            return messageTemplate;
        }
        try {
            return String.format(messageTemplate, parameters);
        } catch (Exception e) {
            return messageTemplate + " (参数格式化失败: " + java.util.Arrays.toString(parameters) + ")";
        }
    }
}