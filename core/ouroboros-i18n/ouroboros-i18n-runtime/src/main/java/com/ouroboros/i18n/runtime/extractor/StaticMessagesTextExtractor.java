package com.ouroboros.i18n.runtime.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import io.vavr.control.Either;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.i18n.extractor.TextExtractor;
import com.ouroboros.i18n.model.I18nEntry;
import com.ouroboros.i18n.runtime.model.I18nEntryImpl;
import com.ouroboros.util.Json;

/**
 * 静态消息文件提取器
 * 从 META-INF/ouroboros/i18n/messages 目录下读取 JSON 文件
 * <p>
 * JSON 结构（二级结构）：
 * {
 * "common": {
 * "save": "保存",
 * "cancel": "取消"
 * },
 * "validation": {
 * "required": "必填"
 * }
 * }
 * <p>
 * 生成的键格式：直接使用第二层的 key
 * 例如：上面的结构会生成 "save"、"cancel"、"required" 等键
 */
public class StaticMessagesTextExtractor implements TextExtractor {

  private static final Logger logger = LoggerFactory.getLogger(StaticMessagesTextExtractor.class);
  private static final String MESSAGES_PATTERN = "classpath*:META-INF/ouroboros/i18n/messages/*.json";

  private static final Supplier<List<I18nEntry>> CLASSPATH_ENTRIES = cacheEntries(new PathMatchingResourcePatternResolver());
  private final Supplier<List<I18nEntry>> entries;

  public StaticMessagesTextExtractor() {
    entries = CLASSPATH_ENTRIES;
  }

  StaticMessagesTextExtractor(ResourcePatternResolver resolver) {
    entries = cacheEntries(resolver);
  }

  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    return Either.right(new ArrayList<>(entries.get()));
  }

  private static Supplier<List<I18nEntry>> cacheEntries(ResourcePatternResolver resolver) {
    return new Supplier<>() {
      private List<I18nEntry> snapshot;

      @Override
      public synchronized List<I18nEntry> get() {
        if (snapshot != null) {
          return snapshot;
        }
        List<I18nEntry> loaded = new ArrayList<>();
        boolean complete = true;
        try {
          for (Resource resource : resolver.getResources(MESSAGES_PATTERN)) {
            try {
              var resourceEntries = new ArrayList<I18nEntry>();
              extractFromResource(resource, resourceEntries);
              loaded.addAll(resourceEntries);
            } catch (Exception e) {
              logger.error("Failed to read or parse message file: {}", resource.getDescription(), e);
              complete = false;
            }
          }
          if (complete) {
            snapshot = List.copyOf(loaded);
          }
        } catch (Exception e) {
          logger.error("Failed to load message resources: {}", MESSAGES_PATTERN, e);
        }
        return loaded;
      }
    };
  }

  private static void extractFromResource(Resource resource, List<I18nEntry> entries) throws IOException {
    String filename = resource.getFilename();
    if (filename == null) {
      return;
    }

    try (InputStream inputStream = resource.getInputStream()) {
      String content = org.apache.commons.io.IOUtils.toString(inputStream, StandardCharsets.UTF_8);

      // 解析为 Namespace + Key 结构：category -> (key -> message)
      Map<String, Map<String, String>> messageMap = Json.toBean(
          new TypeReference<Map<String, Map<String, String>>>() {
          },
          content
      );

      // 遍历分类和消息
      messageMap.forEach((category, messages) -> {
        if (messages != null) {
          messages.forEach((key, message) -> {
            // 保留所有 key，包括 null 值（作为占位符）
            entries.add(new I18nEntryImpl(
                category,  // 使用第一层的 category 作为源
                key,  // 使用第二层的 key
                message,  // 允许 null 值
                "Static Messages",
                "static"
            ));
          });
        }
      });

      logger.debug("Extracted {} entries from {}",
          entries.size(), "static messages");

    }
  }
}
