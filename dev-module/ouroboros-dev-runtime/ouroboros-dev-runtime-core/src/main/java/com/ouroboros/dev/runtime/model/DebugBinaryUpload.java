package com.ouroboros.dev.runtime.model;

import java.util.Arrays;

public class DebugBinaryUpload {
  private final String fileName;
  private final byte[] content;

  private DebugBinaryUpload(Builder builder) {
    this.fileName = builder.fileName;
    this.content = builder.content == null ? new byte[0] : Arrays.copyOf(builder.content, builder.content.length);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getFileName() {
    return fileName;
  }

  public byte[] getContent() {
    return Arrays.copyOf(content, content.length);
  }

  public static class Builder {
    private String fileName;
    private byte[] content;

    private Builder() {
    }

    public Builder fileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    public Builder content(byte[] content) {
      this.content = content;
      return this;
    }

    public DebugBinaryUpload build() {
      return new DebugBinaryUpload(this);
    }
  }
}
