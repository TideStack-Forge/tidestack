package com.ouroboros.dev.codeintelligence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.ouroboros.exception.InternalException;
import com.ouroboros.expression.EL;
import com.ouroboros.template.TextTemplateEngine;
import freemarker.core.ParseException;

/** Pure syntax checks for the platform's small expression/template profiles; full script languages use LSP. */
public final class CodeSyntaxDiagnostics {
  private final TextTemplateEngine templates = new TextTemplateEngine();

  public boolean supports(String language) {
    return Set.of("spel", "template", "rpc-template", "jsonpath", "freemarker").contains(language);
  }

  public List<Map<String, Object>> diagnose(CodeContextRequest request, CodeContextDescription description) {
    if (!supports(description.profile())) return List.of();
    JsonNode target = request.draft();
    if ("logicflow".equals(request.resource().type())) {
      target = null;
      for (var node : request.draft().path("nodes")) {
        if (node.path("name").asText().equals(request.location().nodeName())) { target = node; break; }
      }
    }
    if (target == null) return List.of();
    var result = new ArrayList<Map<String, Object>>();
    visit(target.at(request.location().pointer()), List.of(), description, result);
    return List.copyOf(result);
  }

  private void visit(JsonNode value, List<Object> path, CodeContextDescription description, List<Map<String, Object>> result) {
    if (result.size() >= 100) return;
    var hints = description.projectionHints();
    if (value.isTextual()) {
      for (var issue : check(value.asText(), path, description.profile(), hints)) {
        if (result.size() >= 100) break;
        result.add(Map.of("source", "ouroboros-syntax", "code", "syntax-error", "severity", 1,
            "message", issue.message(), "path", path, "range", Map.of(
                "start", position(value.asText(), issue.start()), "end", position(value.asText(), issue.end()))));
      }
    } else if (value.isObject()) {
      if ("legacy-output".equals(hints.get("embeddedExpressionMode"))) return;
      value.fields().forEachRemaining(entry -> visit(entry.getValue(), child(path, entry.getKey()), description, result));
    } else if (value.isArray() && !Boolean.FALSE.equals(hints.get("embeddedArrayValues"))) {
      for (int index = 0; index < value.size(); index++) visit(value.get(index), child(path, index), description, result);
    }
  }

  private List<Issue> check(String text, List<Object> path, String language, Map<String, Object> hints) {
    if (text.isBlank() || "disabled".equals(hints.get("templateMode"))) return List.of();
    if (language.equals("freemarker")) {
      try {
        templates.validateSyntax(text);
        return List.of();
      } catch (ParseException error) {
        int start = offset(text, error.getLineNumber() - 1, error.getColumnNumber() - 1);
        int end = offset(text, error.getEndLineNumber() - 1, error.getEndColumnNumber());
        return List.of(new Issue(start, Math.max(start + 1, end), error.getEditorMessage()));
      } catch (IOException error) {
        throw new InternalException("无法解析文本模板", error);
      }
    }
    if (language.equals("spel")) {
      String mode = String.valueOf(hints.getOrDefault("embeddedExpressionMode", "template"));
      if (mode.startsWith("legacy-")) {
        if (text.startsWith("$.")) return spel(text.substring(2), true, 2);
        if (!mode.equals("legacy-event")) return List.of();
      }
      return spel(text, mode.equals("bare"), 0);
    }
    String mode = String.valueOf(hints.getOrDefault("templateMode", "mixed"));
    if (mode.equals("expression-only")) return spel(text, false, 0);
    var issues = new ArrayList<Issue>();
    var masked = references(text, "#{", issues);
    if (!issues.isEmpty()) return issues;
    if (language.equals("jsonpath")) {
      // Configuration can supply an entire path; its value belongs to the application, not the editor.
      if (text.contains("#{")) return List.of();
      try { JsonPath.compile(text); }
      catch (InvalidPathException error) { issues.add(new Issue(0, text.length(), error.getMessage())); }
      return issues;
    }
    if (mode.equals("config-only")) return List.of();
    if (mode.equals("payload-only")) {
      if (path.isEmpty() || !(hints.get("embeddedPayloadFields") instanceof List<?> fields) || fields.contains(path.getLast()))
        references(text, "${", issues);
      return issues;
    }
    return spel(masked, false, 0);
  }

  private List<Issue> spel(String text, boolean bare, int shift) {
    String expression = bare ? EL.wrapExpression(text) : text;
    var parsed = EL.validateSyntax(expression);
    if (parsed.isRight()) return List.of();
    var failure = parsed.getLeft();
    if (!(failure instanceof org.springframework.expression.ParseException error)) {
      throw new InternalException("表达式语法解析器不可用", failure);
    }
    int position = Math.max(0, error.getPosition());
    String body = error.getExpressionString();
    int bodyStart = body == null ? -1 : bare ? text.indexOf(body) : templateBodyStart(text, body);
    if (bodyStart >= 0) position += bodyStart;
    else if (bare && !expression.equals(text)) position -= 3;
    position = Math.max(0, Math.min(text.length(), position));
    return List.of(new Issue(position + shift, position + shift + 1, error.getSimpleMessage()));
  }

  private int templateBodyStart(String text, String body) {
    for (int open = text.indexOf("${"); open >= 0; open = text.indexOf("${", open + 2)) {
      if (open > 0 && text.charAt(open - 1) == '$') continue;
      int start = open + 2;
      while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
      // A matching body may also occur in literal text or a quoted string in a preceding expression.
      if (text.startsWith(body, start) && EL.validateSyntax(text.substring(0, open)).isRight()) return start;
    }
    return -1;
  }

  /** These profiles use literal key substitution, not the SpEL grammar. Preserve offsets for mixed templates. */
  private String references(String text, String prefix, List<Issue> issues) {
    var masked = new StringBuilder(text);
    for (int start = text.indexOf(prefix); start >= 0; start = text.indexOf(prefix, start + prefix.length())) {
      int end = text.indexOf('}', start + prefix.length());
      if (end < 0) {
        issues.add(new Issue(start, text.length(), "占位符缺少结束符 }"));
        break;
      }
      String key = text.substring(start + prefix.length(), end);
      if (key.isBlank() || key.contains("{")) issues.add(new Issue(start, end + 1, "占位符键不能为空或包含嵌套花括号"));
      for (int index = start; index <= end; index++) masked.setCharAt(index, index == start ? '0' : ' ');
    }
    return masked.toString();
  }

  private static List<Object> child(List<Object> path, Object segment) {
    var result = new ArrayList<>(path); result.add(segment); return List.copyOf(result);
  }

  private static Map<String, Integer> position(String text, int offset) {
    int line = 0, column = 0;
    for (int index = 0; index < Math.min(text.length(), offset); index++) {
      char current = text.charAt(index);
      if (current == '\r' || current == '\n') {
        line++; column = 0;
        if (current == '\r' && index + 1 < Math.min(text.length(), offset) && text.charAt(index + 1) == '\n') index++;
      } else column++;
    }
    return Map.of("line", line, "character", column);
  }

  private static int offset(String text, int line, int column) {
    int index = 0, row = 0, visualColumn = 0;
    while (index < text.length()) {
      if (row == line && visualColumn >= Math.max(0, column)) return index;
      char current = text.charAt(index);
      if (current == '\r' || current == '\n') {
        if (row == line) return index;
        row++; visualColumn = 0;
        if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
      } else {
        // TextTemplateEngine uses FreeMarker's default tab size (8 visual columns).
        visualColumn = current == '\t' ? (visualColumn / 8 + 1) * 8 : visualColumn + 1;
      }
      index++;
    }
    return text.length();
  }

  private record Issue(int start, int end, String message) {}
}
