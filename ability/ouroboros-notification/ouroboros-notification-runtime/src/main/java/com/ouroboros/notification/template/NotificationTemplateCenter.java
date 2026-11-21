package com.ouroboros.notification.template;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;

import com.ouroboros.util.SPI;

/**
 * @author liansz
 */
public class NotificationTemplateCenter {
  /**
   * 获取模板
   *
   * @param name
   * @return
   */
  public static Optional<NotificationTemplate> getTemplate(String name) {
    return resolveTemplate(name).map(ResolvedTemplate::template);
  }

  public static Optional<ResolvedTemplate> resolveTemplate(String name) {
    return resolve(providers(), name);
  }

  /** Resolves through the original SPI order, omitting only runtime override providers. */
  public static Optional<ResolvedTemplate> resolveDefaultTemplate(String name) {
    return resolve(defaultProviders(), name);
  }

  public static Map<String, NotificationTemplate> getAllDefaultTemplate() {
    return collect(defaultProviders());
  }

  /** Each Provider is observed once so management versions and content refer to the same rows. */
  public static Optional<TemplateDefinition> observeTemplate(String name) {
    var definitions = providers().flatMap(provider -> provider.getTemplate(name)
        .map(template -> resolved(provider, template)).stream()).toList();
    return definitions.isEmpty() ? Optional.empty() : Optional.of(new TemplateDefinition(definitions));
  }

  public static Map<String, TemplateDefinition> observeAllTemplates() {
    var grouped = new TreeMap<String, List<ResolvedTemplate>>(String.CASE_INSENSITIVE_ORDER);
    providers().forEach(provider -> provider.getAllTemplate().forEach(template ->
        grouped.computeIfAbsent(template.getName(), ignored -> new ArrayList<>()).add(resolved(provider, template))));
    var result = new TreeMap<String, TemplateDefinition>(String.CASE_INSENSITIVE_ORDER);
    grouped.forEach((name, definitions) -> result.put(name, new TemplateDefinition(List.copyOf(definitions))));
    return result;
  }

  public record TemplateDefinition(List<ResolvedTemplate> definitions) {
    public ResolvedTemplate effective() { return definitions.getFirst(); }
    public ResolvedTemplate deployedDefault() { return definitions.stream().filter(item -> !item.runtimeOverride()).findFirst().orElse(null); }
    public ResolvedTemplate runtimeOverride() { return definitions.stream().filter(ResolvedTemplate::runtimeOverride).findFirst().orElse(null); }
  }

  private static Stream<NotificationTemplateProvider> providers() {
    return SPI.getCachedSortedServiceStream(NotificationTemplateProvider.class);
  }

  private static Stream<NotificationTemplateProvider> defaultProviders() {
    return providers().filter(provider -> !provider.isRuntimeOverride());
  }

  private static Optional<ResolvedTemplate> resolve(Stream<NotificationTemplateProvider> providers, String name) {
    return providers.flatMap(provider -> provider.getTemplate(name)
            .map(template -> resolved(provider, template)).stream())
        .findFirst();
  }

  private static ResolvedTemplate resolved(NotificationTemplateProvider provider, NotificationTemplate template) {
    Priority priority = provider.getClass().getAnnotation(Priority.class);
    return new ResolvedTemplate(template, provider.getClass().getName(),
        priority == null ? 0 : priority.value(), provider.isRuntimeOverride());
  }

  public record ResolvedTemplate(NotificationTemplate template, String provider, int priority, boolean runtimeOverride) {
  }

  /**
   * 获取所有模板
   *
   * @return
   */
  public static Map<String, NotificationTemplate> getAllTemplate() {
    return collect(providers());
  }

  private static Map<String, NotificationTemplate> collect(Stream<NotificationTemplateProvider> providers) {
    return providers.flatMap(provider -> provider.getAllTemplate().stream())
        .collect(
            Collectors.toMap(
                NotificationTemplate::getName,
                template -> template,
                (o, n) -> o,
                () -> new TreeMap<>(String::compareToIgnoreCase)
            )
        );
  }
}
