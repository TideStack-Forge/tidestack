package com.ouroboros.businessmodel.projection.custom;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import com.ouroboros.businessmodel.BusinessModelMetaParser;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.MetaWithExtraProps;
import com.ouroboros.uischema.schema.ActionNode;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Maps;

public class CustomConfig extends MetaWithExtraProps {
  private final CustomApiConfig customApiConfig;
  private final CustomUiConfig customUiConfig;

  public String getName() {
    return getExtraProps(String.class, "name", "");
  }

  public String getTitle() {
    return getExtraProps(String.class, "title", "");
  }

  public String getIcon() {
    return getExtraProps(String.class, "icon", "");
  }

  public String getDescription() {
    return getExtraProps(String.class, "description", "");
  }

  public CustomApiConfig getApi() {
    return customApiConfig;
  }

  public CustomUiConfig getUi() {
    return customUiConfig;
  }

  public CustomConfig(Map<String, Object> config) {
    this(config, false);
  }

  private CustomConfig(Map<String, Object> config, boolean referenceFormat) {
    extraProps = config;
    this.customApiConfig = new CustomApiConfig(getExtraProps(Map.class, "api", Collections.emptyMap()), referenceFormat);
    this.customUiConfig = new CustomUiConfig(getExtraProps(Map.class, "ui", Collections.emptyMap()));
  }

  public static List<CustomConfig> from(BusinessModelMeta meta) {
    boolean referenceFormat = BusinessModelMetaParser.isCurrent(meta);
    var configs = new java.util.ArrayList<CustomConfig>();
    for (Object value : meta.getExtraProp(List.class, "custom").orElse(List.of())) {
      if (value instanceof Map<?, ?> definition) {
        Map<String, Object> copy = new LinkedHashMap<>();
        definition.forEach((key, item) -> {
          if (key instanceof String name) { copy.put(name, item); }
        });
        configs.add(new CustomConfig(copy, referenceFormat));
      }
    }
    return List.copyOf(configs);
  }

  public static class CustomApiConfig extends MetaWithExtraProps {
    private static final Map<String, String> LEGACY_INPUT_MAPPING = Map.of(
        "params", "${ request.params }", "data", "${ request.body }");
    private final boolean referenceFormat;

    public CustomApiConfig(Map<String, Object> config) {
      this(config, false);
    }

    private CustomApiConfig(Map<String, Object> config, boolean referenceFormat) {
      this.extraProps = config;
      this.referenceFormat = referenceFormat;
    }

    public Boolean getIsEnabled() {
      return getExtraProps(Boolean.class, "isEnabled", false);
    }

    public Boolean getIsGenerateImplementation() {
      return getExtraProps(Boolean.class, "isGenerateImplementation", false);
    }

    /**
     * Whether a high-code API is explicitly bound to the same-name business Action.
     * A missing value preserves the historical default: generated implementations
     * are Action-backed, while direct high-code Web APIs are not.
     */
    public Boolean getIsBindAction() {
      return getExtraProps(Boolean.class, "isBindAction", null);
    }

    public boolean isActionBindingEnabled() {
      return referenceFormat ? isActionReference() : isActionBindingEnabled(getExtraProps());
    }

    public boolean isActionReference() {
      return referenceFormat && "ACTION".equals(getExtraProps(String.class, "implementationMode", ""));
    }

    public String getActionName(String functionName) {
      return isActionReference() ? getExtraProps(String.class, "action", "") : functionName;
    }

    /** Legacy generated APIs received the request without declaring an input mapping. */
    public Optional<Map<?, ?>> getActionInputMapping() {
      if (extraProps.get("actionInputMapping") instanceof Map<?, ?> mapping) {
        return Optional.of(mapping);
      }
      return hasLegacyInputDefault() ? Optional.of(LEGACY_INPUT_MAPPING) : Optional.empty();
    }

    public String getActionInputMappingFormatVersion() {
      return referenceFormat || hasLegacyInputDefault() ? BusinessModelMetaParser.CURRENT_VERSION : null;
    }

    private boolean hasLegacyInputDefault() {
      return !referenceFormat && getIsGenerateImplementation() && !extraProps.containsKey("actionInputMapping");
    }

    public List<String> definitionErrors() {
      var errors = new java.util.ArrayList<String>();
      if (referenceFormat) {
        if (!List.of("ACTION", "WEB_API").contains(getExtraProps(String.class, "implementationMode", ""))) {
          errors.add("implementationMode-invalid");
        }
        for (String key : List.of("isGenerateImplementation", "isCustomLogicFlow", "isBindAction", "script", "scriptLanguage",
            "binding", "inputSchema", "outputSchema", "precondition", "authorization", "readOnly", "emits")) {
          if (extraProps.containsKey(key)) { errors.add(key + "-inline-definition-not-allowed"); }
        }
        if (isActionReference() && getActionName("").isBlank()) { errors.add("action-required"); }
        if (!isActionReference() && extraProps.containsKey("action")) { errors.add("action-requires-ACTION"); }
      } else {
        if (extraProps.containsKey("implementationMode") || extraProps.containsKey("action")) {
          errors.add("reference-requires-formatVersion-2.0");
        }
        if (getIsGenerateImplementation() && Boolean.FALSE.equals(getIsBindAction())) {
          errors.add("isGenerateImplementation-conflicts-isBindAction");
        }
      }
      return List.copyOf(errors);
    }

    public static boolean isActionBindingEnabled(Map<?, ?> api) {
      Object configured = api.get("isBindAction");
      if (configured instanceof Boolean value) {
        return value;
      }
      return Boolean.TRUE.equals(api.get("isGenerateImplementation"));
    }

    public String getHttpMethod() {
      return getExtraProps(String.class, "httpMethod", "GET");
    }

    public String getUrl() {
      return getExtraProps(String.class, "url", "");
    }

    public String getScriptLanguage() {
      return getExtraProps(String.class, "scriptLanguage", "javascript");
    }

    public String getScript() {
      return getExtraProps(String.class, "script", "");
    }

    public Boolean getCustomLogicFlow() {
      return getExtraProps(Boolean.class, "isCustomLogicFlow", false);
    }
  }

  public static class CustomUiConfig extends MetaWithExtraProps {
    public CustomUiConfig(Map<String, Object> config) {
      this.extraProps = config;
    }

    public Boolean getIsEnabled() {
      return getExtraProps(Boolean.class, "isEnabled", false);
    }

    public String getEntryPoint() {
      return getExtraProps(String.class, "entryPoint", "operation");
    }

    public String getMode() {
      return getExtraProps(String.class, "mode", "dialog");
    }

    public String getButtonIcon() {
      return getExtraProps(String.class, "buttonIcon", "");
    }

    public Boolean getButtonLabelVisible() {
      return getExtraProps(Boolean.class, "buttonLabelVisible", true);
    }

    public String getButtonLabel() {
      return getExtraProps(String.class, "buttonLabel", "");
    }

    public String getButtonDisabledOn() {
      return getExtraProps(String.class, "buttonDisabledOn", "");
    }

    public String getButtonLevel() {
      return getExtraProps(String.class, "buttonLevel", "default");
    }

    public String getButtonSize() {
      return getExtraProps(String.class, "buttonSize", "md");
    }

    public Number getButtonOrder() {
      return getExtraProps(Number.class, "buttonOrder", 0.0f);
    }

    public String getButtonAlign() {
      return getExtraProps(String.class, "buttonAlign", "left");
    }

    public String getButtonBackgroundColor() {
      return getExtraProps(String.class, "buttonBackgroundColor", "");
    }

    public String getButtonForegroundColor() {
      return getExtraProps(String.class, "buttonForegroundColor", "");
    }

    @Deprecated
    public SchemaNode getSchema() {
      var schemaMap = getExtraProps(Map.class, "schema", null);
      if (schemaMap == null) {
        return null;
      }
      return new SchemaNode(Maps.deepClone(schemaMap, LinkedHashMap::new));
    }

    public SchemaNode getButtonSchema() {
      var schemaMap = getExtraProps(Map.class, "buttonSchema", null);
      if (schemaMap == null || schemaMap.isEmpty()) {
        return new ActionNode();
      }
      return new SchemaNode(Maps.deepClone(schemaMap, LinkedHashMap::new));
    }

    public Boolean getApiConfirm() {
      return getExtraProps(Boolean.class, "apiConfirm", false);
    }

    public String getApiConfirmTitle() {
      return getExtraProps(String.class, "apiConfirmTitle", "");
    }

    public String getApiConfirmText() {
      return getExtraProps(String.class, "apiConfirmText", "");
    }

    public Boolean getApiFeedback() {
      return getExtraProps(Boolean.class, "apiFeedback", false);
    }

    public String getApiFeedbackTitle() {
      return getExtraProps(String.class, "apiFeedbackTitle", "");
    }

    public SchemaNode getApiFeedbackBody() {
      var schemaMap = getExtraProps(Map.class, "apiFeedbackBody", null);
      if (schemaMap == null) {
        return null;
      }
      return new SchemaNode(schemaMap);
    }

    public Boolean getApiReload() {
      return getExtraProps(Boolean.class, "apiReload", false);
    }

    public Boolean getApiRedirect() {
      return getExtraProps(Boolean.class, "apiRedirect", false);
    }

    public String getApiRedirectUrl() {
      return getExtraProps(String.class, "apiRedirectUrl", "");
    }

  }
}
