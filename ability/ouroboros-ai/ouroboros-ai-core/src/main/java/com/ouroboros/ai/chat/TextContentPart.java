package com.ouroboros.ai.chat;

public final class TextContentPart implements ChatContentPart {
  private final String text;

  private TextContentPart(String text) {
    this.text = text;
  }

  public static TextContentPart of(String text) {
    return new TextContentPart(text);
  }

  public String getText() {
    return text;
  }
}
