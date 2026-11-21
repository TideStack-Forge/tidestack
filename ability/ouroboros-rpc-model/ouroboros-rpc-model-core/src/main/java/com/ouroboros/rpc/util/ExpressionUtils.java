package com.ouroboros.rpc.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.expression.EL;
import com.ouroboros.expression.ExpressionInputReference;

/** RPC template boundaries shared by evaluation and input discovery. */
public class ExpressionUtils {
  public enum Kind { LITERAL, CONFIGURATION, EXPRESSION }

  public record Segment(Kind kind, String value) { }

  public record Inputs(List<String> configKeys, List<ExpressionInputReference> variables) { }

  public static Either<Throwable, Inputs> analyze(String template) {
    return Try.of(() -> {
      var keys = new java.util.LinkedHashSet<String>();
      var variables = new java.util.LinkedHashSet<ExpressionInputReference>();
      for (var segment : segments(template)) {
        if (segment.kind() == Kind.CONFIGURATION) {
          keys.add(segment.value());
        } else if (segment.kind() == Kind.EXPRESSION) {
          var plan = EL.compile(segment.value()).getOrElseThrow(error -> new PlatformException("表达式语法错误", error));
          variables.addAll(plan.inputReferences().getOrElseThrow(error -> new PlatformException("表达式输入分析失败", error)));
        }
      }
      return new Inputs(List.copyOf(keys), List.copyOf(variables));
    }).toEither();
  }

  public static Either<Throwable, Object> eval(String template, Map<String, Object> variables,
                                               Function<String, String> configGetter) {
    return Try.of(() -> {
      var parts = segments(template);
      var text = new StringBuilder();
      for (var part : parts) {
        Object value = switch (part.kind()) {
          case LITERAL -> part.value();
          case CONFIGURATION -> {
            var configured = configGetter.apply(part.value());
            if (configured == null) {
              throw new PlatformException("缺少配置项: " + part.value());
            }
            yield configured;
          }
          case EXPRESSION -> EL.eval(part.value(), variables)
              .getOrElseThrow(error -> new PlatformException("计算表达式失败", error));
        };
        if (parts.size() == 1 && part.kind() != Kind.LITERAL) {
          return value;
        }
        text.append(Objects.toString(value, ""));
      }
      return (Object) text.toString();
    }).toEither();
  }

  public static Either<Throwable, String> eval(String template, Map<String, Object> variables) {
    return eval(template, variables, key -> "").map(value -> Objects.toString(value, ""));
  }

  public static List<Segment> segments(String template) {
    if (template == null) {
      return List.of();
    }
    var result = new ArrayList<Segment>();
    var literal = new StringBuilder();
    int offset = 0;
    while (offset < template.length()) {
      char marker = template.charAt(offset);
      if (template.startsWith("$${", offset)) {
        int end = expressionEnd(template, offset + 3);
        literal.append(template, offset + 1, end + 1);
        offset = end + 1;
        continue;
      }
      if ((marker == '$' || marker == '#') && offset + 1 < template.length()
          && template.charAt(offset + 1) == '{') {
        flushLiteral(result, literal);
        int end = expressionEnd(template, offset + 2);
        String content = template.substring(offset + 2, end);
        if (content.isBlank()) {
          throw new PlatformException("占位符不能为空");
        }
        result.add(new Segment(marker == '#' ? Kind.CONFIGURATION : Kind.EXPRESSION,
            marker == '#' ? content : template.substring(offset, end + 1)));
        offset = end + 1;
        continue;
      }
      if (marker == '$' && offset + 1 < template.length() && identifierStart(template.charAt(offset + 1))) {
        flushLiteral(result, literal);
        int end = offset + 2;
        while (end < template.length()) {
          char current = template.charAt(end);
          if (Character.isJavaIdentifierPart(current) && current != '$') {
            end++;
          } else if (current == '.' && end + 1 < template.length() && identifierStart(template.charAt(end + 1))) {
            end += 2;
          } else {
            break;
          }
        }
        result.add(new Segment(Kind.EXPRESSION, "${" + template.substring(offset + 1, end) + "}"));
        offset = end;
        continue;
      }
      literal.append(marker);
      offset++;
    }
    flushLiteral(result, literal);
    return List.copyOf(result);
  }

  private static boolean identifierStart(char value) {
    return Character.isJavaIdentifierStart(value) && value != '$';
  }

  private static void flushLiteral(List<Segment> result, StringBuilder literal) {
    if (!literal.isEmpty()) {
      result.add(new Segment(Kind.LITERAL, literal.toString()));
      literal.setLength(0);
    }
  }

  private static int expressionEnd(String template, int start) {
    int depth = 1;
    char quote = 0;
    for (int index = start; index < template.length(); index++) {
      char value = template.charAt(index);
      if (quote != 0) {
        if (value == quote) {
          if (index + 1 < template.length() && template.charAt(index + 1) == quote) {
            index++;
          } else {
            quote = 0;
          }
        }
      } else if (value == '\'' || value == '"') {
        quote = value;
      } else if (value == '{') {
        depth++;
      } else if (value == '}' && --depth == 0) {
        return index;
      }
    }
    throw new PlatformException("占位符缺少结束符");
  }
}
