package com.ouroboros.i18n.runtime.provider;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.i18n.model.I18nLocale;
import com.ouroboros.i18n.model.TextDirection;
import com.ouroboros.i18n.provider.I18nLocaleProvider;
import com.ouroboros.i18n.runtime.entity.I18nLocaleEntity;
import com.ouroboros.i18n.runtime.model.I18nLocaleImpl;
import com.ouroboros.util.Json;

/**
 * 数据库字典提供者
 * 从数据库表 t_sys_i18n_locale 读取国际化字典
 */
@Component
public class DatabaseI18nLocaleProvider {

  private static final Logger logger = LoggerFactory.getLogger(DatabaseI18nLocaleProvider.class);

  @Autowired
  private TypedDataModel<String, I18nLocaleEntity> dataModel;

  public DatabaseI18nLocaleProvider() {
    Proxy.provider = this;
  }

  public Optional<I18nLocale> getLocale(String localeCode) {
    try {
      TypedDataModel<String, I18nLocaleEntity> dataModel = getDataModel();

      var result = dataModel.get(localeCode);
      if (result.isFailure() || result.get() == null) {
        logger.debug("Locale not found in database for locale: {}", localeCode);
        return Optional.empty();
      }

      I18nLocaleEntity entity = result.get();

      // 解析 JSON messages
      Map<String, Map<String, String>> messages = Json.tryToBean(
          new com.fasterxml.jackson.core.type.TypeReference<Map<String, Map<String, String>>>() {
          },
          entity.getMessages()
      ).getOrElse(new HashMap<>());

      // 解析 direction
      TextDirection direction = null;
      if (entity.getDirection() != null) {
        try {
          direction = TextDirection.valueOf(entity.getDirection());
        } catch (IllegalArgumentException e) {
          logger.warn("Invalid direction value: {}", entity.getDirection());
        }
      }

      I18nLocale locale = new I18nLocaleImpl(
          entity.getLocale(),
          entity.getName() != null ? entity.getName() : "database",
          entity.getVersion() != null ? entity.getVersion() : "1.0.0",
          entity.getEnabled() != null ? entity.getEnabled() : true,
          entity.getIsDefault() != null ? entity.getIsDefault() : false,
          messages,
          entity.getUpdatedAt() != null ?
              entity.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
              System.currentTimeMillis(),
          direction
      );

      logger.debug("Loaded i18n locale from database for locale: {}", localeCode);
      return Optional.of(locale);

    } catch (Exception e) {
      logger.error("Error loading i18n locale from database for locale: {}", localeCode, e);
      return Optional.empty();
    }
  }

  /**
   * 查询数据库中所有语言的元数据（不包含 messages 字段）
   */
  public List<I18nLocaleEntity> getLocaleMetadataList() {
    try {
      TypedDataModel<String, I18nLocaleEntity> dataModel = getDataModel();

      // 只 select 元数据字段，排除大字段 messages
      List<String> selectColumns = Arrays.asList(
          "locale", "name", "version", "enabled", "isDefault", "direction", "updatedAt"
      );
      var result = dataModel.query(selectColumns, new HashMap<>());
      if (result.isFailure()) {
        logger.warn("Failed to query locale metadata from database", result.getCause());
        return java.util.Collections.emptyList();
      }

      return result.get();

    } catch (Exception e) {
      logger.error("Error querying locale metadata from database", e);
      return java.util.Collections.emptyList();
    }
  }

  public List<String> getSupportedLocales() {
    return getLocaleMetadataList().stream()
        .map(I18nLocaleEntity::getLocale)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  private TypedDataModel<String, I18nLocaleEntity> getDataModel() {
    return dataModel;
  }

  @Priority(100)
  public static class Proxy implements I18nLocaleProvider {
    private static DatabaseI18nLocaleProvider provider;

    @Override
    public Optional<I18nLocale> getDictionary(String locale) {
      return Optional.ofNullable(provider)
          .map(p -> p.getLocale(locale))
          .orElse(Optional.empty());
    }

    @Override
    public List<String> getSupportedLocales() {
      return Optional.ofNullable(provider)
          .map(DatabaseI18nLocaleProvider::getSupportedLocales)
          .orElse(Collections.emptyList());
    }
  }
}
