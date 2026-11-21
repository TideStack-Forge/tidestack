package com.ouroboros.businessmodel.behavior;

import java.util.Objects;

/** Implementation binding contributed for an Action contract declared by a model. */
public final class BusinessActionBindingContribution {
  private final String modelFullName;
  private final String actionName;
  private final String source;
  private final String formatVersion;
  private final ActionBinding binding;

  public BusinessActionBindingContribution(String modelFullName,
                                           String actionName,
                                           String source,
                                           ActionBinding binding) {
    this(modelFullName, actionName, source, binding, null);
  }

  public BusinessActionBindingContribution(String modelFullName, String actionName, String source,
                                           ActionBinding binding, String formatVersion) {
    new BehaviorDefinitionSource(source, "actions." + actionName + ".binding", formatVersion);
    this.formatVersion = formatVersion;
    this.modelFullName = requireText(modelFullName, "modelFullName");
    this.actionName = requireText(actionName, "actionName");
    this.source = requireText(source, "source");
    this.binding = Objects.requireNonNull(binding, "binding must not be null");
  }

  public String getModelFullName() {
    return modelFullName;
  }

  public String getActionName() {
    return actionName;
  }

  public String getFormatVersion() { return formatVersion; }

  public String getSource() {
    return source;
  }

  public ActionBinding getBinding() {
    return BehaviorDefinitionCopies.copyBinding(binding);
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
