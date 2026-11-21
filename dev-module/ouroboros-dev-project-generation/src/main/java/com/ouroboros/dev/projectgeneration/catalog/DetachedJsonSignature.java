package com.ouroboros.dev.projectgeneration.catalog;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public record DetachedJsonSignature(
    String algorithm,
    String keyId,
    String signature
) {
  private static final ObjectMapper MAPPER = JsonMapper.builder().build();

  @JsonCreator
  public DetachedJsonSignature(
      @JsonProperty("algorithm") String algorithm,
      @JsonProperty("keyId") String keyId,
      @JsonProperty("signature") String signature
  ) {
    this.algorithm = algorithm;
    this.keyId = keyId;
    this.signature = signature;
  }

  public static DetachedJsonSignature parse(byte[] envelope) {
    try {
      CanonicalJson.requireCanonical(envelope);
      return MAPPER.readValue(envelope, DetachedJsonSignature.class);
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid detached JSON signature envelope", e);
    }
  }
}
