package com.ouroboros.notification.service.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ouroboros.exception.BusinessException;

/** Runtime management contracts; these types do not participate in sending or Provider resolution. */
public final class NotificationTemplateManagement {
  private NotificationTemplateManagement() { }

  public record Command(String expectedDefaultRevision, String expectedOverrideRevision, Map<String, Object> changes) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
      throw new BusinessException(400, "[NOTIFICATION_TEMPLATE_UNKNOWN_FIELD] 请求字段不支持：" + name);
    }
  }

  public record Problem(String path, String channel, String code, String message) { }

  public record Difference(String path, String operation, Object before, Object after) { }

  public record Definition(String name, Map<String, Object> defaultTemplate, Map<String, Object> effectiveTemplate,
      boolean hasOverride, boolean defaultExists, String effectiveSource, String defaultSource,
      String effectiveProvider, String defaultRevision, String overrideRevision, Boolean baseChanged,
      String modifiedBy, LocalDateTime modifiedAt, Map<String, Boolean> availableActions,
      List<Problem> diagnostics, List<Difference> differences) { }

  public record Validation(boolean valid, Map<String, Object> candidate, List<Difference> changes,
      List<Difference> overrides, List<Problem> errors, List<Problem> warnings) { }

  public record WriteResult(String name, boolean changed, boolean committed, boolean confirmed,
      String defaultRevision, String overrideRevision, Definition definition, String reason) { }

  public record ErrorDetails(String reason, String defaultRevision, String overrideRevision, List<Problem> errors) { }

  public static class ManagementException extends BusinessException {
    private final ErrorDetails details;

    public ManagementException(int code, String message, ErrorDetails details) {
      super(code, message); this.details = details;
    }

    public ErrorDetails getDetails() { return details; }
  }
}
