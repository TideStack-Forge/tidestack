package com.ouroboros.logicflow.authoring.model;

import java.util.List;
import java.util.Map;

public class NodeDefine {
  private String type;
  private String label;
  private String icon;
  private String iconBg;
  private String iconColor;
  private String group;
  private String shape;
  private String description;
  private Boolean isTerminal;
  private String terminalKind;
  private Map<String, Object> editorCapabilities;
  private Map<String, Object> scaffold;
  private Map<String, Map<String, Object>> portRules;
  private List<Map<String, Object>> properties;
  private Map<String, String> svgs;
  private int order;

  public String getType() {
    return type;
  }

  public NodeDefine setType(String type) {
    this.type = type;
    return this;
  }

  public String getLabel() {
    return label;
  }

  public NodeDefine setLabel(String label) {
    this.label = label;
    return this;
  }

  public String getIcon() {
    return icon;
  }

  public NodeDefine setIcon(String icon) {
    this.icon = icon;
    return this;
  }

  public String getIconBg() {
    return iconBg;
  }

  public NodeDefine setIconBg(String iconBg) {
    this.iconBg = iconBg;
    return this;
  }

  public String getIconColor() {
    return iconColor;
  }

  public NodeDefine setIconColor(String iconColor) {
    this.iconColor = iconColor;
    return this;
  }

  public String getGroup() {
    return group;
  }

  public NodeDefine setGroup(String group) {
    this.group = group;
    return this;
  }

  public String getShape() {
    return shape;
  }

  public NodeDefine setShape(String shape) {
    this.shape = shape;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public NodeDefine setDescription(String description) {
    this.description = description;
    return this;
  }

  public Boolean getIsTerminal() {
    return isTerminal;
  }

  public NodeDefine setIsTerminal(Boolean isTerminal) {
    this.isTerminal = isTerminal;
    return this;
  }

  public String getTerminalKind() {
    return terminalKind;
  }

  public NodeDefine setTerminalKind(String terminalKind) {
    this.terminalKind = terminalKind;
    return this;
  }

  public Map<String, Object> getEditorCapabilities() {
    return editorCapabilities;
  }

  public NodeDefine setEditorCapabilities(Map<String, Object> editorCapabilities) {
    this.editorCapabilities = editorCapabilities;
    return this;
  }

  public Map<String, Object> getScaffold() {
    return scaffold;
  }

  public NodeDefine setScaffold(Map<String, Object> scaffold) {
    this.scaffold = scaffold;
    return this;
  }

  public Map<String, Map<String, Object>> getPortRules() {
    return portRules;
  }

  public NodeDefine setPortRules(Map<String, Map<String, Object>> portRules) {
    this.portRules = portRules;
    return this;
  }

  public List<Map<String, Object>> getProperties() {
    return properties;
  }

  public NodeDefine setProperties(List<Map<String, Object>> properties) {
    this.properties = properties;
    return this;
  }

  public Map<String, String> getSvgs() {
    return svgs;
  }

  public NodeDefine setSvgs(Map<String, String> svgs) {
    this.svgs = svgs;
    return this;
  }

  public int getOrder() {
    return order;
  }

  public NodeDefine setOrder(int order) {
    this.order = order;
    return this;
  }
}
