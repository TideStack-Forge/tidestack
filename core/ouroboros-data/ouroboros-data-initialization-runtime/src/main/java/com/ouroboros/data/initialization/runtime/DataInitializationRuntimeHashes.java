package com.ouroboros.data.initialization.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class DataInitializationRuntimeHashes {
  private DataInitializationRuntimeHashes() {
  }

  static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + IntStream.range(0, digest.length)
          .mapToObj(index -> String.format("%02x", digest[index] & 0xff))
          .collect(Collectors.joining());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 digest is unavailable", e);
    }
  }
}
