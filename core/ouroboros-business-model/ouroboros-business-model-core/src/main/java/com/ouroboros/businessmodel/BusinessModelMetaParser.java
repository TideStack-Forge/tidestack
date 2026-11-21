package com.ouroboros.businessmodel;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.control.Try;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

/** Selects the owning document's format before Jackson can coerce its version. */
public final class BusinessModelMetaParser {
  public static final String CURRENT_VERSION = "2.0";
  private static final Set<String> DEFAULT_VERSIONS = Set.of("0.1", "1.0", CURRENT_VERSION);

  private BusinessModelMetaParser() {
  }

  public static Try<BusinessModelMeta> parse(String content) {
    return Json.tryToBean(JsonNode.class, content).flatMap(root -> Try.run(() -> validate(root)))
        .flatMap(ignored -> Json.tryToBean(BusinessModelMeta.class, content)
            .orElse(() -> Json.tryToBean(DefaultBusinessModelMeta.class, content)));
  }

  private static void validate(JsonNode root) {
    if (!root.isObject()) {
      throw new BusinessException(422, "业务模型元数据必须是 JSON 对象");
    }
    if (root.has("modelType") && !"default".equals(root.path("modelType").asText())) {
      return;
    }
    if (root.has("formatVersion")) {
      JsonNode version = root.get("formatVersion");
      if (!version.isTextual() || !DEFAULT_VERSIONS.contains(version.textValue())) {
        throw new BusinessException(422, "formatVersion：默认业务模型仅支持缺省、0.1、1.0 或 2.0");
      }
    }
  }

  public static boolean isCurrent(BusinessModelMeta meta) {
    return CURRENT_VERSION.equals(meta.getFormatVersion());
  }
}
