package com.ouroboros.dev.componentlib.dto;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class PlatformComponentLibDTO {
  String name;
  String version;
  String publicPath;
  String description;
  String author;
  List<ComponentDefineDTO> renderers;
  List<ComponentDefineDTO> formItems;
  List<ComponentDefineDTO> optionsControls;
  List<ComponentDefineDTO> actions;
  List<ComponentDefineDTO> rules;
  List<ComponentDefineDTO> formulas;
  List<ComponentDefineDTO> rendererEditorPlugins;
  List<ComponentDefineDTO> actionEditorPlugins;
  List<ComponentDefineDTO> validatorEditorPlugins;

  public String getName() {
    return name;
  }

  public PlatformComponentLibDTO setName(String name) {
    this.name = name;
    return this;
  }

  public String getVersion() {
    return version;
  }

  public PlatformComponentLibDTO setVersion(String version) {
    this.version = version;
    return this;
  }

  public String getPublicPath() {
    return publicPath;
  }

  public PlatformComponentLibDTO setPublicPath(String publicPath) {
    this.publicPath = publicPath;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public PlatformComponentLibDTO setDescription(String description) {
    this.description = description;
    return this;
  }

  public String getAuthor() {
    return author;
  }

  public PlatformComponentLibDTO setAuthor(String author) {
    this.author = author;
    return this;
  }

  public List<ComponentDefineDTO> getRenderers() {
    return renderers == null ? Collections.emptyList() : Collections.unmodifiableList(renderers);
  }

  public PlatformComponentLibDTO setRenderers(List<ComponentDefineDTO> renderers) {
    this.renderers = renderers.stream()
        .map(OverrideTypeComponentDefineMapper.of("renderer"))
        .collect(Collectors.toList());
    return this;
  }

  public List<ComponentDefineDTO> getFormItems() {
    return formItems == null ? Collections.emptyList() : Collections.unmodifiableList(formItems);
  }

  public PlatformComponentLibDTO setFormItems(List<ComponentDefineDTO> formItems) {
    this.formItems = formItems.stream()
        .map(OverrideTypeComponentDefineMapper.of("formItem"))
        .collect(Collectors.toList());
    return this;
  }

  public List<ComponentDefineDTO> getOptionsControls() {
    return optionsControls == null ? Collections.emptyList() : Collections.unmodifiableList(optionsControls);
  }

  public PlatformComponentLibDTO setOptionsControls(List<ComponentDefineDTO> optionsControls) {
    this.optionsControls = optionsControls.stream()
        .map(OverrideTypeComponentDefineMapper.of("optionsControl"))
        .collect(Collectors.toList());
    return this;
  }

  public List<ComponentDefineDTO> getActions() {
    return actions == null ? Collections.emptyList() : Collections.unmodifiableList(actions);
  }

  public PlatformComponentLibDTO setActions(List<ComponentDefineDTO> actions) {
    this.actions = actions.stream()
        .map(OverrideTypeComponentDefineMapper.of("action"))
        .collect(Collectors.toList());
    return this;
  }

  public List<ComponentDefineDTO> getRules() {
    return rules == null ? Collections.emptyList() : Collections.unmodifiableList(rules);
  }

  public PlatformComponentLibDTO setRules(List<ComponentDefineDTO> rules) {
    this.rules = rules.stream()
        .map(OverrideTypeComponentDefineMapper.of("rule"))
        .collect(Collectors.toList());
    return this;
  }

  public List<ComponentDefineDTO> getFormulas() {
    return formulas == null ? Collections.emptyList() : Collections.unmodifiableList(formulas);
  }

  public PlatformComponentLibDTO setFormulas(List<ComponentDefineDTO> formulas) {
    this.formulas = formulas.stream()
        .map(OverrideTypeComponentDefineMapper.of("formula"))
        .collect(Collectors.toList());
    return this;
  }

  public List<ComponentDefineDTO> getRendererEditorPlugins() {
    return rendererEditorPlugins == null ? Collections.emptyList() : Collections.unmodifiableList(rendererEditorPlugins);
  }

  public PlatformComponentLibDTO setRendererEditorPlugins(List<ComponentDefineDTO> rendererEditorPlugins) {
    this.rendererEditorPlugins = rendererEditorPlugins.stream()
        .map(OverrideTypeComponentDefineMapper.of("rendererEditorPlugin"))
        .collect(Collectors.toList());
    return this;
  }

  public List<ComponentDefineDTO> getActionEditorPlugins() {
    return actionEditorPlugins == null ? Collections.emptyList() : Collections.unmodifiableList(actionEditorPlugins);
  }

  public PlatformComponentLibDTO setActionEditorPlugins(List<ComponentDefineDTO> actionEditorPlugins) {
    this.actionEditorPlugins = actionEditorPlugins.stream()
        .map(OverrideTypeComponentDefineMapper.of("actionEditorPlugin"))
        .collect(Collectors.toList());
    return this;
  }

  public List<ComponentDefineDTO> getValidatorEditorPlugins() {
    return validatorEditorPlugins == null ? Collections.emptyList() : Collections.unmodifiableList(validatorEditorPlugins);
  }

  public PlatformComponentLibDTO setValidatorEditorPlugins(List<ComponentDefineDTO> validatorEditorPlugins) {
    this.validatorEditorPlugins = validatorEditorPlugins.stream()
        .map(OverrideTypeComponentDefineMapper.of("validatorEditorPlugin"))
        .collect(Collectors.toList());
    return this;
  }

  private static class OverrideTypeComponentDefineMapper extends ComponentDefineDTO {
    private final String type;
    @JsonIgnore
    private final ComponentDefineDTO cmp;

    public OverrideTypeComponentDefineMapper(ComponentDefineDTO cmp, String type) {
      this.cmp = cmp;
      this.type = type;
    }

    public static Function<ComponentDefineDTO, ComponentDefineDTO> of(String type) {
      return cmp -> new OverrideTypeComponentDefineMapper(cmp, type);
    }

    @Override
    public String getType() {
      return type;
    }

    @Override
    public String getName() {
      return cmp.getName();
    }

    @Override
    public String getLabel() {
      return cmp.getLabel();
    }

    @Override
    public String getDescription() {
      return cmp.getDescription();
    }

    @Override
    public List<String> getTags() {
      return cmp.getTags();
    }

    @Override
    public ComponentDefineDTO setType(String type) {
      return this;
    }

    @Override
    public ComponentDefineDTO setName(String name) {
      return this;
    }

    @Override
    public ComponentDefineDTO setLabel(String label) {
      return this;
    }

    @Override
    public ComponentDefineDTO setDescription(String description) {
      return this;
    }

    @Override
    public ComponentDefineDTO setTags(List<String> tags) {
      return this;
    }
  }
}
