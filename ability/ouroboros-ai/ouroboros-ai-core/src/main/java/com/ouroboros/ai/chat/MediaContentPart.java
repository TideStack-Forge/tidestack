package com.ouroboros.ai.chat;

import java.util.Map;

import com.ouroboros.ai.support.AiModelCopies;

public final class MediaContentPart implements ChatContentPart {
  private final String mediaType;
  private final String uri;
  private final Map<String, Object> metadata;

  private MediaContentPart(Builder builder) {
    this.mediaType = builder.mediaType;
    this.uri = builder.uri;
    this.metadata = AiModelCopies.copyMap(builder.metadata);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getMediaType() {
    return mediaType;
  }

  public String getUri() {
    return uri;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public static final class Builder {
    private String mediaType;
    private String uri;
    private Map<String, Object> metadata;

    private Builder() {
    }

    public Builder mediaType(String mediaType) {
      this.mediaType = mediaType;
      return this;
    }

    public Builder uri(String uri) {
      this.uri = uri;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
      return this;
    }

    public MediaContentPart build() {
      return new MediaContentPart(this);
    }
  }
}
