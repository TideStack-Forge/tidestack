package com.ouroboros.dev.app.model;

import java.util.Map;

public class RuntimeImageViewDTO {
  private String reference;
  private String repository;
  private String tag;
  private String digest;
  private String inputMode;
  private Boolean updateRecommended;
  private Map<String, Boolean> capabilities;

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public String getRepository() {
    return repository;
  }

  public void setRepository(String repository) {
    this.repository = repository;
  }

  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public String getDigest() {
    return digest;
  }

  public void setDigest(String digest) {
    this.digest = digest;
  }

  public String getInputMode() {
    return inputMode;
  }

  public void setInputMode(String inputMode) {
    this.inputMode = inputMode;
  }

  public Boolean getUpdateRecommended() {
    return updateRecommended;
  }

  public void setUpdateRecommended(Boolean updateRecommended) {
    this.updateRecommended = updateRecommended;
  }

  public Map<String, Boolean> getCapabilities() {
    return capabilities;
  }

  public void setCapabilities(Map<String, Boolean> capabilities) {
    this.capabilities = capabilities;
  }
}
