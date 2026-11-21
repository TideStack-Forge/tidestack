package com.ouroboros.i18n.runtime.model;

import com.ouroboros.i18n.model.I18nEntry;

/**
 * 国际化条目实现类
 */
public class I18nEntryImpl implements I18nEntry {

  private final String namespace;
  private final String key;
  private final String sourceText;
  private final String context;
  private final String propertyName;

  public I18nEntryImpl(String namespace, String key, String sourceText, String context, String propertyName) {
    this.namespace = namespace;
    this.key = key;
    this.sourceText = sourceText;
    this.context = context;
    this.propertyName = propertyName;
  }

  @Override
  public String getNamespace() {
    return namespace;
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public String getSourceText() {
    return sourceText;
  }

  @Override
  public String getContext() {
    return context;
  }

  @Override
  public String getPropertyName() {
    return propertyName;
  }
}
