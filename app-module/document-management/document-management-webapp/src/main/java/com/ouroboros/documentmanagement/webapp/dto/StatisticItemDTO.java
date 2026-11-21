package com.ouroboros.documentmanagement.webapp.dto;

/**
 * 统计项 DTO
 */
public class StatisticItemDTO {
  private String label; // 统计项名称
  private String value; // 统计值（字符串，已格式化）

  public StatisticItemDTO() {}

  public StatisticItemDTO(String label, String value) {
    this.label = label;
    this.value = value;
  }

  public String getLabel() {
    return label;
  }

  public StatisticItemDTO setLabel(String label) {
    this.label = label;
    return this;
  }

  public String getValue() {
    return value;
  }

  public StatisticItemDTO setValue(String value) {
    this.value = value;
    return this;
  }
}