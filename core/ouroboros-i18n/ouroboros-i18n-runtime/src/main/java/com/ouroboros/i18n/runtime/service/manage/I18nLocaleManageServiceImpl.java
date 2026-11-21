package com.ouroboros.i18n.runtime.service.manage;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import io.vavr.control.Either;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.model.I18nEntry;
import com.ouroboros.i18n.model.TextDirection;
import com.ouroboros.i18n.provider.I18nLocaleProvider;
import com.ouroboros.i18n.extractor.TextExtractor;
import com.ouroboros.i18n.runtime.dto.ExportResult;
import com.ouroboros.i18n.runtime.dto.I18nLanguageSummary;
import com.ouroboros.i18n.runtime.dto.SyncResult;
import com.ouroboros.i18n.runtime.entity.I18nLocaleEntity;
import com.ouroboros.i18n.runtime.model.I18nLocaleImpl;
import com.ouroboros.i18n.runtime.service.I18nLocaleService;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

/**
 * 国际化字典管理服务实现
 */
@Service
public class I18nLocaleManageServiceImpl implements I18nLocaleManageService {

  private static final Logger logger = LoggerFactory.getLogger(I18nLocaleManageServiceImpl.class);

  @Autowired
  private I18nLocaleService localeService;

  @Autowired(required = false)
  private CacheManager cacheManager;

  @Autowired
  private TypedDataModel<String, I18nLocaleEntity> dataModel;

  @Override
  public List<I18nLanguageSummary> getAllLanguages() {
    try {
      Set<String> locales = new HashSet<>();

      // 从所有Provider获取支持的语言
      SPI.getServiceStream(I18nLocaleProvider.class).forEach(provider -> {
        try {
          List<String> supportedLocales = provider.getSupportedLocales();
          if (supportedLocales != null) {
            locales.addAll(supportedLocales);
          }
        } catch (Exception e) {
          logger.error("Failed to get supported locales from provider: {}", provider.getClass().getName(), e);
        }
      });

      // 批量获取数据库中的元数据（不含 messages），用于 hasDbRecord 和 messageCount
      Map<String, I18nLocaleEntity> dbMetadataMap = getDbMetadataMap();

      List<I18nLanguageSummary> languages = new ArrayList<>();
      for (String locale : locales) {
        localeService.getLocale(locale).peek(dict -> {
          // 不过滤，显示所有语言（包括禁用的）
          I18nLanguageSummary summary = convertToSummary(dict, dbMetadataMap.containsKey(locale));
          languages.add(summary);
        });
      }

      return languages.stream()
          .sorted(Comparator.comparing(I18nLanguageSummary::getLocale))
          .collect(Collectors.toList());

    } catch (Exception e) {
      logger.error("Error getting all languages", e);
      return Collections.emptyList();
    }
  }

  private Map<String, I18nLocaleEntity> getDbMetadataMap() {
    try {
      TypedDataModel<String, I18nLocaleEntity> dataModel = getDataModel();
      // 只 select 元数据字段，排除大字段 messages
      List<String> selectColumns = Arrays.asList("locale", "name", "version", "enabled", "isDefault");
      var result = dataModel.query(selectColumns, new HashMap<>());
      if (result.isSuccess()) {
        return result.get().stream()
            .collect(Collectors.toMap(I18nLocaleEntity::getLocale, e -> e, (a, b) -> b));
      }
    } catch (Exception e) {
      logger.warn("Failed to get db metadata map", e);
    }
    return Collections.emptyMap();
  }

  private I18nLanguageSummary convertToSummary(I18nLocale locale, boolean hasDbRecord) {
    int messageCount = 0;
    if (locale.getMessages() != null) {
      messageCount = locale.getMessages().values().stream()
          .mapToInt(Map::size)
          .sum();
    }

    return new I18nLanguageSummary(
        locale.getLocale(),
        locale.getName(),
        locale.getEnabled(),
        locale.getIsDefault(),
        messageCount,
        locale.getDirection() != null ? locale.getDirection().name() : null,
        locale.getVersion(),
        null, // updatedAt - not needed
        hasDbRecord
    );
  }

  @Override
  public Optional<I18nLocale> getLanguageDetail(String locale) {
    var result = localeService.getLocale(locale);
    if (result.isRight()) {
      return Optional.of(result.get());
    } else {
      logger.warn("Failed to get language detail for: {}", locale, result.getLeft());
      return Optional.empty();
    }
  }

  @Override
  public Either<OuroborosException, Boolean> saveLocale(I18nLocale locale) {
    try {
      TypedDataModel<String, I18nLocaleEntity> dataModel = getDataModel();

      I18nLocaleEntity entity = new I18nLocaleEntity();
      entity.setLocale(locale.getLocale());
      entity.setName(locale.getName());
      entity.setVersion(locale.getVersion());
      entity.setMessages(Json.toJsonString(locale.getMessages()));

      // 处理 direction
      TextDirection direction = locale.getDirection();
      entity.setDirection(direction != null ? direction.name() : null);

      entity.setUpdatedAt(LocalDateTime.now());

      var existingResult = dataModel.get(locale.getLocale());
      if (existingResult.isSuccess() && existingResult.get() != null) {
        // 语言已存在：保持原有的enabled和isDefault状态
        I18nLocaleEntity existing = existingResult.get();
        entity.setEnabled(existing.getEnabled());
        entity.setIsDefault(existing.getIsDefault());

        var updateResult = dataModel.update(locale.getLocale(), entity);
        if (updateResult.isFailure()) {
          return Either.left(new InternalException("Failed to update locale", updateResult.getCause()));
        }
      } else {
        // 语言不存在：isDefault设为false，enabled遵从导入文件
        entity.setEnabled(locale.getEnabled());
        entity.setIsDefault(false);
        entity.setCreatedAt(LocalDateTime.now());

        var insertResult = dataModel.insert(entity);
        if (insertResult.isFailure()) {
          return Either.left(new InternalException("Failed to insert locale", insertResult.getCause()));
        }
      }

      clearCache(locale.getLocale());
      logger.info("Saved locale for: {}", locale.getLocale());
      return Either.right(true);

    } catch (Exception e) {
      logger.error("Error saving dictionary", e);
      return Either.left(new InternalException("Error saving dictionary", e));
    }
  }

  @Override
  public Either<OuroborosException, SyncResult> syncFromExtractors(String localeCode, boolean removeOrphaned) {
    try {
      // 提取文案模板
      var templateResult = extractLocaleTemplate(localeCode);
      if (templateResult.isLeft()) {
        return Either.left(templateResult.getLeft());
      }
      I18nLocale template = templateResult.get();

      // 获取现有的locale
      var existingResult = localeService.getLocale(localeCode);

      Map<String, Map<String, String>> mergedMessages;
      final int[] addedCount = {0};
      final int[] removedCount = {0};

      if (existingResult.isRight()) {
        // 合并：现有的 + 新提取的
        I18nLocale existing = existingResult.get();
        mergedMessages = new LinkedHashMap<>();

        if (removeOrphaned) {
          // 先复制现有的翻译
          if (existing.getMessages() != null) {
            existing.getMessages().forEach((source, entries) ->
                mergedMessages.put(source, new LinkedHashMap<>(entries))
            );
          }
          
          // 只删除模板中存在的 namespace 里不存在的 key
          // 不删除模板中不存在的整个 namespace（可能是手动添加的）
          if (template.getMessages() != null) {
            template.getMessages().forEach((source, templateEntries) -> {
              Map<String, String> existingEntries = mergedMessages.get(source);
              if (existingEntries != null) {
                // 只保留模板中存在的 key
                existingEntries.keySet().retainAll(templateEntries.keySet());
              }
            });
            // 删除空的 namespace
            mergedMessages.entrySet().removeIf(entry -> entry.getValue().isEmpty());
          }
          
          // 计算删除的数量
          int mergedTotal = mergedMessages.values().stream().mapToInt(Map::size).sum();
          removedCount[0] = (existing.getMessages() != null ? 
              existing.getMessages().values().stream().mapToInt(Map::size).sum() : 0) - mergedTotal;
        } else {
          // 先复制现有的所有内容
          if (existing.getMessages() != null) {
            existing.getMessages().forEach((source, entries) ->
                mergedMessages.put(source, new LinkedHashMap<>(entries))
            );
          }
        }

        // 补充缺失的key（不覆盖已有的翻译）
        if (template.getMessages() != null) {
          template.getMessages().forEach((source, entries) -> {
            Map<String, String> targetEntries = mergedMessages.computeIfAbsent(source, k -> new LinkedHashMap<>());
            entries.forEach((key, value) -> {
              if (!targetEntries.containsKey(key)) {
                targetEntries.put(key, value);
                addedCount[0]++;
              }
            });
          });
        }
      } else {
        // 不存在，直接使用模板
        mergedMessages = template.getMessages();
        addedCount[0] = mergedMessages.values().stream().mapToInt(Map::size).sum();
      }

      // 保存合并后的locale
      var localeName = existingResult.toJavaOptional()
          .map(I18nLocale::getName)
          .orElse(template.getName());
      var version = existingResult.toJavaOptional()
          .map(I18nLocale::getVersion)
          .orElse(template.getVersion());
      var isEnabled = existingResult.toJavaOptional()
          .map(I18nLocale::getEnabled)
          .orElse(template.getEnabled());
      var isDefault = existingResult.toJavaOptional()
          .map(I18nLocale::getIsDefault)
          .orElse(template.getIsDefault());
      var direction = existingResult.toJavaOptional()
          .map(I18nLocale::getDirection)
          .orElse(template.getDirection());
      I18nLocale merged = new I18nLocaleImpl(
          localeCode,
          localeName,
          version,
          isEnabled,
          isDefault,
          mergedMessages,
          System.currentTimeMillis(),
          direction
      );

      var saveResult = saveLocale(merged);
      if (saveResult.isLeft()) {
        return Either.left(saveResult.getLeft());
      }

      SyncResult result = new SyncResult(addedCount[0], removedCount[0]);
      logger.info("Synced entries for locale: {}, added: {}, removed: {}, removeOrphaned: {}",
          localeCode, addedCount[0], removedCount[0], removeOrphaned);
      return Either.right(result);

    } catch (Exception e) {
      logger.error("Error syncing from extractors", e);
      return Either.left(new InternalException("Error syncing from extractors", e));
    }
  }

  public void clearCache(String locale) {
    if (cacheManager != null) {
      var cache = cacheManager.getCache("i18n-locale");
      if (cache != null) {
        cache.evict(locale);
        logger.debug("Cleared cache for locale: {}", locale);
      }
      // 同时清除语言列表缓存
      var languagesCache = cacheManager.getCache("i18n-languages");
      if (languagesCache != null) {
        languagesCache.clear();
        logger.debug("Cleared languages list cache");
      }
    }
  }

  @Override
  public Either<OuroborosException, ExportResult> exportLanguage(String locale) {
    try {
      // 获取语言详情
      Optional<I18nLocale> languageOpt = getLanguageDetail(locale);
      if (!languageOpt.isPresent()) {
        return Either.left(new BusinessException("Language not found: " + locale));
      }

      I18nLocale language = languageOpt.get();

      // 序列化为 JSON
      String jsonContent = Json.toPrettyJsonString(language);

      // 构造导出结果
      String filename = locale + ".json";
      ExportResult result = new ExportResult(filename, jsonContent, "application/json");

      logger.info("Exported language: {}, size: {} bytes", locale, jsonContent.length());
      return Either.right(result);

    } catch (Exception e) {
      logger.error("Error exporting language: {}", locale, e);
      return Either.left(new InternalException("Export failed", e));
    }
  }

  @Override
  public Either<OuroborosException, ExportResult> exportTemplate() {
    try {
      // 使用现有的 extractLocaleTemplate 方法
      var result = extractLocaleTemplate("template");
      if (result.isLeft()) {
        return Either.left(result.getLeft());
      }

      I18nLocale template = result.get();
      String jsonContent = Json.toPrettyJsonString(template);

      // 构造导出结果
      String filename = "translation-template.json";
      ExportResult exportResult = new ExportResult(filename, jsonContent, "application/json");

      logger.info("Exported translation template, size: {} bytes", jsonContent.length());
      return Either.right(exportResult);

    } catch (Exception e) {
      logger.error("Error exporting template", e);
      return Either.left(new InternalException("Export template failed", e));
    }
  }

  @Override
  public Either<OuroborosException, I18nLocale> extractLocaleTemplate(String sourceLocale) {
    if (sourceLocale == null || sourceLocale.trim().isEmpty()) {
      return Either.left(new BusinessException("Source locale cannot be null or empty"));
    }

    List<I18nEntry> allEntries = new ArrayList<>();

    SPI.getServiceStream(TextExtractor.class).forEach(extractor -> {
      try {
        extractor.extract().peek(entries -> {
          if (entries != null) {
            allEntries.addAll(entries);
          }
        }).peekLeft(e -> {
          logger.warn("Failed to extract texts from extractor: {}", extractor.getClass().getName(), e);
        });
      } catch (Exception e) {
        logger.error("Failed to extract texts from extractor: {}", extractor.getClass().getName(), e);
      }
    });

    Map<String, Map<String, String>> entries = new LinkedHashMap<>();
    for (I18nEntry entry : allEntries) {
      entries.computeIfAbsent(entry.getNamespace(), k -> new LinkedHashMap<>())
          .put(entry.getKey(), entry.getSourceText());
    }

    I18nLocale locale = new I18nLocaleImpl(
        sourceLocale,
        sourceLocale,
        "1.0.0",
        true,
        false,
        entries,
        System.currentTimeMillis(),
        null
    );

    logger.info("Extracted {} entries for locale: {}", allEntries.size(), sourceLocale);
    return Either.right(locale);
  }

  @Override
  public Either<OuroborosException, Boolean> setDefaultLocale(String locale) {
    try {
      TypedDataModel<String, I18nLocaleEntity> dataModel = getDataModel();

      // 1. 将所有语言的 isDefault 设为 false
      Map<String, Object> falseUpdate = new HashMap<>();
      falseUpdate.put("isDefault", false);
      Map<String, Object> trueCondition = new HashMap<>();
      trueCondition.put("isDefault", true);
      dataModel.update(trueCondition, falseUpdate);

      // 2. 检查指定语言是否存在
      var existingResult = dataModel.get(locale);

      if (existingResult.isSuccess() && existingResult.get() != null) {
        // 语言存在，更新为默认并启用
        Map<String, Object> trueUpdate = new HashMap<>();
        trueUpdate.put("isDefault", true);
        trueUpdate.put("enabled", true); // 默认语言必须启用
        Map<String, Object> localeCondition = new HashMap<>();
        localeCondition.put("locale", locale);
        dataModel.update(localeCondition, trueUpdate);

      } else {
        // 语言不存在，创建新记录
        I18nLocaleEntity entity = new I18nLocaleEntity();
        entity.setLocale(locale);
        entity.setName(locale); // 同代码同名
        entity.setVersion("1.0");
        entity.setEnabled(true); // 默认语言必须启用
        entity.setIsDefault(true);
        entity.setMessages("{}"); // 空messages
        entity.setDirection("LTR");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        var insertResult = dataModel.insert(entity);
        if (insertResult.isFailure()) {
          return Either.left(new InternalException("Failed to create locale", insertResult.getCause()));
        }
      }

      // 3. 清除所有语言的缓存（因为默认状态可能改变）
      if (cacheManager != null) {
        Cache cache = cacheManager.getCache("i18n-locale");
        if (cache != null) {
          cache.clear();
          logger.debug("Cleared all locale cache due to default language change");
        }
        Cache languagesCache = cacheManager.getCache("i18n-languages");
        if (languagesCache != null) {
          languagesCache.clear();
        }
      }

      logger.info("Set default locale to: {}", locale);
      return Either.right(true);

    } catch (Exception e) {
      logger.error("Error setting default locale: {}", locale, e);
      return Either.left(new InternalException("设置默认语言失败", e));
    }
  }

  @Override
  public Either<OuroborosException, Boolean> deleteLanguage(String locale) {
    try {
      TypedDataModel<String, I18nLocaleEntity> dataModel = getDataModel();

      // 检查是否为默认语言
      Map<String, Object> condition = new HashMap<>();
      condition.put("locale", locale);
      condition.put("isDefault", true);

      if (dataModel.count(condition).getOrElse(0L) > 0) {
        return Either.left(new BusinessException("不能删除默认语言"));
      }

      // 删除语言
      Map<String, Object> deleteCondition = new HashMap<>();
      deleteCondition.put("locale", locale);
      long deleted = dataModel.delete(deleteCondition).getOrElse(0L);

      if (deleted == 0) {
        return Either.left(new BusinessException("语言不存在: " + locale));
      }

      // 清除所有语言的缓存（因为语言列表改变）
      if (cacheManager != null) {
        Cache cache = cacheManager.getCache("i18n-locale");
        if (cache != null) {
          cache.clear();
          logger.debug("Cleared all locale cache due to language deletion");
        }
        Cache languagesCache = cacheManager.getCache("i18n-languages");
        if (languagesCache != null) {
          languagesCache.clear();
        }
      }

      logger.info("Deleted language: {}", locale);
      return Either.right(true);

    } catch (Exception e) {
      logger.error("Error deleting language: {}", locale, e);
      return Either.left(new InternalException("删除语言失败", e));
    }
  }

  @Override
  public Either<OuroborosException, Boolean> setLanguageEnabled(String locale, boolean enabled) {
    try {
      TypedDataModel<String, I18nLocaleEntity> dataModel = getDataModel();

      // 检查数据库中是否存在该语言
      var existingResult = dataModel.get(locale);

      if (existingResult.isSuccess() && existingResult.get() != null) {
        // 语言存在，更新状态
        I18nLocaleEntity existing = existingResult.get();

        // 如果要禁用，检查是否为默认语言
        if (!enabled && existing.getIsDefault()) {
          return Either.left(new BusinessException("不能禁用默认语言"));
        }

        // 更新启用状态
        Map<String, Object> update = new HashMap<>();
        update.put("enabled", enabled);
        Map<String, Object> localeCondition = new HashMap<>();
        localeCondition.put("locale", locale);
        dataModel.update(localeCondition, update);

      } else {
        // 语言不存在，创建新记录
        I18nLocaleEntity entity = new I18nLocaleEntity();
        entity.setLocale(locale);
        entity.setName(locale); // 同代码同名
        entity.setVersion("1.0");
        entity.setEnabled(enabled);
        entity.setIsDefault(false);
        entity.setMessages("{}"); // 空messages
        entity.setDirection("LTR");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        var insertResult = dataModel.insert(entity);
        if (insertResult.isFailure()) {
          return Either.left(new InternalException("Failed to create locale", insertResult.getCause()));
        }
      }

      // 清除所有语言的缓存（因为启用状态改变可能影响语言列表）
      if (cacheManager != null) {
        Cache cache = cacheManager.getCache("i18n-locale");
        if (cache != null) {
          cache.clear();
          logger.debug("Cleared all locale cache due to language enabled status change");
        }
        Cache languagesCache = cacheManager.getCache("i18n-languages");
        if (languagesCache != null) {
          languagesCache.clear();
        }
      }

      logger.info("{} language: {}", enabled ? "Enabled" : "Disabled", locale);
      return Either.right(true);

    } catch (Exception e) {
      logger.error("Error setting language enabled status: {}, enabled: {}", locale, enabled, e);
      return Either.left(new InternalException("设置语言状态失败", e));
    }
  }

  private TypedDataModel<String, I18nLocaleEntity> getDataModel() {
    return dataModel;
  }
}
