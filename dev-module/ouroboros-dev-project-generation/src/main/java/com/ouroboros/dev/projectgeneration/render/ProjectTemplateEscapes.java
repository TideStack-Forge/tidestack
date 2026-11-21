package com.ouroboros.dev.projectgeneration.render;

import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateIdentifierContracts;

public class ProjectTemplateEscapes {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
  private static final Pattern JAVA_PACKAGE =
      Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

  public String json(Object value) {
    try {
      return OBJECT_MAPPER.writeValueAsString(value)
          .replace("\u2028", "\\u2028")
          .replace("\u2029", "\\u2029");
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot escape JSON template value", e);
    }
  }

  public String xmlText(Object value) {
    return text(value, "xmlText")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }

  public String xmlAttribute(Object value) {
    return xmlText(value)
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  public String javaString(Object value) {
    String text = text(value, "javaString");
    StringBuilder encoded = new StringBuilder("\"");
    for (int index = 0; index < text.length(); index++) {
      char ch = text.charAt(index);
      switch (ch) {
        case '\\' -> encoded.append("\\\\");
        case '"' -> encoded.append("\\\"");
        case '\b' -> encoded.append("\\b");
        case '\t' -> encoded.append("\\t");
        case '\n' -> encoded.append("\\n");
        case '\f' -> encoded.append("\\f");
        case '\r' -> encoded.append("\\r");
        default -> appendPrintable(encoded, ch);
      }
    }
    return encoded.append('"').toString();
  }

  public String identifier(Object value) {
    String text = text(value, "identifier");
    if (!IDENTIFIER.matcher(text).matches()) {
      throw new IllegalArgumentException("Template identifier value format is invalid");
    }
    return text;
  }

  public String javaPackage(Object value) {
    String text = text(value, "javaPackage");
    if (!JAVA_PACKAGE.matcher(text).matches()) {
      throw new IllegalArgumentException("Template javaPackage value format is invalid");
    }
    return text;
  }

  public String propertiesValue(Object value) {
    String text = text(value, "propertiesValue");
    StringBuilder encoded = new StringBuilder();
    for (int index = 0; index < text.length(); index++) {
      char ch = text.charAt(index);
      if (index == 0 && ch == ' ') {
        encoded.append("\\ ");
        continue;
      }
      switch (ch) {
        case '\\' -> encoded.append("\\\\");
        case '\t' -> encoded.append("\\t");
        case '\n' -> encoded.append("\\n");
        case '\r' -> encoded.append("\\r");
        case '\f' -> encoded.append("\\f");
        case ':', '=', '#', '!' -> encoded.append('\\').append(ch);
        default -> appendPrintable(encoded, ch);
      }
    }
    return encoded.toString();
  }

  private static String text(Object value, String helperName) {
    if (!(value instanceof String || value instanceof Boolean || value instanceof Integer || value instanceof Long)) {
      throw new IllegalArgumentException("Template " + helperName + " value must be scalar");
    }
    String text = String.valueOf(value);
    ProjectTemplateIdentifierContracts.requireNoControlCharacters(text, helperName);
    return text;
  }

  private static void appendPrintable(StringBuilder encoded, char ch) {
    if (Character.isISOControl(ch)) {
      encoded.append(String.format("\\u%04x", (int) ch));
    } else {
      encoded.append(ch);
    }
  }
}
