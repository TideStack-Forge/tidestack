package com.ouroboros.cache;

import java.util.Collections;
import java.util.List;

public class CacheEntryPage {
  private Boolean inspectable;
  private Long total;
  private List<CacheEntryDescriptor> items;
  private String message;

  public CacheEntryPage() {
  }

  public CacheEntryPage(Boolean inspectable, Long total, List<CacheEntryDescriptor> items, String message) {
    this.inspectable = inspectable;
    this.total = total;
    this.items = items;
    this.message = message;
  }

  public static CacheEntryPage unsupported(String message) {
    return new CacheEntryPage(Boolean.FALSE, 0L, Collections.emptyList(), message);
  }

  public Boolean getInspectable() {
    return inspectable;
  }

  public CacheEntryPage setInspectable(Boolean inspectable) {
    this.inspectable = inspectable;
    return this;
  }

  public Long getTotal() {
    return total;
  }

  public CacheEntryPage setTotal(Long total) {
    this.total = total;
    return this;
  }

  public List<CacheEntryDescriptor> getItems() {
    return items;
  }

  public CacheEntryPage setItems(List<CacheEntryDescriptor> items) {
    this.items = items;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public CacheEntryPage setMessage(String message) {
    this.message = message;
    return this;
  }
}
