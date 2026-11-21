package com.ouroboros.businesslog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businesslog.service.model.BusinessOperationTemplateSourceView;
import com.ouroboros.util.SPI;

/**
 * 业务日志模板中心，负责按来源优先级合并模板。
 */
public final class BusinessOperationTemplateCenter {

  private static final List<BusinessOperationTemplateSource> SOURCE_PRECEDENCE =
      Collections.unmodifiableList(Arrays.asList(
          BusinessOperationTemplateSource.DATABASE,
          BusinessOperationTemplateSource.FILE,
          BusinessOperationTemplateSource.JAR
      ));

  private BusinessOperationTemplateCenter() {
  }

  public static Optional<BusinessOperationTemplate> getTemplate(String templateKey) {
    return Optional.ofNullable(getAllTemplates().get(normalizeTemplateKey(templateKey)));
  }

  public static Map<String, BusinessOperationTemplateSourceView> getAllTemplates() {
    return mergeProviders(SPI.getCachedSortedServiceStream(BusinessOperationTemplateProvider.class));
  }

  static Map<String, BusinessOperationTemplateSourceView> mergeProviders(
      Stream<BusinessOperationTemplateProvider> providers
  ) {
    Map<BusinessOperationTemplateSource, List<BusinessOperationTemplate>> templatesBySource =
        new EnumMap<>(BusinessOperationTemplateSource.class);
    providers.forEach(provider -> templatesBySource.computeIfAbsent(
        Objects.requireNonNull(provider.getSource(), "业务日志模板 Provider 来源不能为空"),
        source -> new ArrayList<>()
    ).addAll(provider.getAllTemplates()));
    return merge(templatesBySource);
  }

  public static Map<String, BusinessOperationTemplateSourceView> merge(
      Map<BusinessOperationTemplateSource, List<BusinessOperationTemplate>> templatesBySource
  ) {
    Map<String, Map<BusinessOperationTemplateSource, BusinessOperationTemplate>> grouped =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    SOURCE_PRECEDENCE.forEach(source -> templatesBySource.getOrDefault(source, Collections.emptyList()).stream()
        .filter(template -> template != null
            && StringUtils.isNotBlank(template.getHttpMethod())
            && StringUtils.isNotBlank(template.getUrl()))
        .forEach(template -> {
          String templateKey = buildTemplateKey(template);
          Map<BusinessOperationTemplateSource, BusinessOperationTemplate> templates = grouped.computeIfAbsent(
              templateKey,
              key -> new EnumMap<>(BusinessOperationTemplateSource.class)
          );
          templates.put(source, template);
        }));

    Map<String, BusinessOperationTemplateSourceView> merged = new LinkedHashMap<>();
    grouped.forEach((templateKey, templates) -> {
      List<BusinessOperationTemplateSource> availableSources = SOURCE_PRECEDENCE.stream()
          .filter(templates::containsKey)
          .collect(Collectors.toList());

      if (availableSources.isEmpty()) {
        return;
      }

      BusinessOperationTemplateSource source = availableSources.get(0);
      BusinessOperationTemplateSourceView view = BusinessOperationTemplateSourceView.fromTemplate(
          templateKey,
          source,
          templates.get(source)
      );
      view.setAvailableSources(availableSources);
      view.setOverridesSources(availableSources.subList(1, availableSources.size()));
      merged.put(templateKey, view);
    });
    return Collections.unmodifiableMap(merged);
  }

  public static String buildTemplateKey(BusinessOperationTemplate template) {
    return buildTemplateKey(template.getHttpMethod(), template.getUrl());
  }

  public static String buildTemplateKey(String httpMethod, String url) {
    return String.format("%s %s", StringUtils.trimToEmpty(httpMethod), StringUtils.trimToEmpty(url)).trim();
  }

  private static String normalizeTemplateKey(String templateKey) {
    return StringUtils.trimToEmpty(templateKey);
  }
}
