package com.ouroboros.statistics.reporting.runtime.result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatasetDiagnostics {
  private String queryType;
  private String matchedSqlDialect;
  private List<String> messages = new ArrayList<>();
  private Map<String, Object> attributes = new LinkedHashMap<>();

  public String getQueryType() {
    return queryType;
  }

  public void setQueryType(String queryType) {
    this.queryType = queryType;
  }

  public String getMatchedSqlDialect() {
    return matchedSqlDialect;
  }

  public void setMatchedSqlDialect(String matchedSqlDialect) {
    this.matchedSqlDialect = matchedSqlDialect;
  }

  public List<String> getMessages() {
    return messages;
  }

  public void setMessages(List<String> messages) {
    this.messages = messages;
  }

  public Map<String, Object> getAttributes() {
    return attributes;
  }

  public void setAttributes(Map<String, Object> attributes) {
    this.attributes = attributes;
  }
}
