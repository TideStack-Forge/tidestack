package com.ouroboros.businessmodel.action;

import java.util.Map;
import java.util.Objects;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.FileResult;
import com.ouroboros.util.JsonSchemas;

/** Uses the platform's existing OpenAPI binary schema; native content is never serialized for validation. */
public final class BusinessActionOutputs {
  private BusinessActionOutputs() { }

  public static boolean isFile(Map<String, Object> schema) {
    return "string".equals(schema.get("type")) && "binary".equals(schema.get("format"));
  }

  public static void validate(Map<String, Object> schema, Object result) {
    if (result instanceof java.util.concurrent.Future<?> || result instanceof java.util.concurrent.CompletionStage<?>) {
      throw new BusinessException("业务 Action 必须同步完成");
    }
    if (isFile(schema)) {
      if (!(result instanceof FileResult file)
          || schema.containsKey("contentMediaType") && !Objects.equals(schema.get("contentMediaType"), file.mediaType())) {
        throw new BusinessException("业务 Action 必须返回符合媒体类型契约的文件结果");
      }
    } else if (result instanceof FileResult || !JsonSchemas.validate(schema, result)) {
      throw new BusinessException("业务 Action 结果不符合契约");
    }
  }
}
