package com.ouroboros.dev.groovy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import groovyjarjarantlr4.v4.runtime.CharStreams;
import groovyjarjarantlr4.v4.runtime.Token;
import org.apache.groovy.parser.antlr4.GroovyLexer;
import org.eclipse.lsp4j.*;

/** Lexical operations use Groovy's own lexer, preserving strings, comments and GStrings. */
final class GroovySyntax {
  private GroovySyntax() {}

  static List<? extends Token> tokens(String source) {
    var lexer = new GroovyLexer(CharStreams.fromString(source));
    lexer.setErrorIgnored(true);
    lexer.removeErrorListeners();
    return lexer.getAllTokens();
  }

  /**
   * Compilation annotations can execute arbitrary code (ASTTest, local transforms and annotation
   * collectors). GE's reconcile parser deliberately ignores compiler config scripts. Project
   * annotations out before JDT sees the source, keeping every UTF-16 position and newline intact.
   * Java classpath annotations remain available to the resolver and closure type inference.
   */
  static String forEditor(String source) {
    var tokens = tokens(source).stream().filter(t -> t.getChannel() == Token.DEFAULT_CHANNEL).toList();
    char[] projected = source.toCharArray();
    for (int i = 0; i < tokens.size(); i++) {
      var annotation = tokens.get(i);
      if (!annotation.getText().equals("@")) continue;
      int end = annotation.getStopIndex() + 1;
      boolean name = true;
      while (i + 1 < tokens.size()) {
        String text = tokens.get(i + 1).getText();
        if (name && !text.isEmpty() && Character.isJavaIdentifierStart(text.charAt(0))) {
          end = tokens.get(++i).getStopIndex() + 1; name = false;
        } else if (!name && text.equals(".")) {
          end = tokens.get(++i).getStopIndex() + 1; name = true;
        } else break;
      }
      if (i + 1 < tokens.size() && tokens.get(i + 1).getText().equals("(")) {
        int depth = 0;
        do {
          var token = tokens.get(++i);
          if (token.getText().equals("(")) depth++;
          if (token.getText().equals(")")) depth--;
          end = token.getStopIndex() + 1;
        } while (depth > 0 && i + 1 < tokens.size());
        if (depth > 0) end = source.codePointCount(0, source.length());
      }
      int from = source.offsetByCodePoints(0, annotation.getStartIndex());
      int to = source.offsetByCodePoints(0, end);
      for (int offset = from; offset < to; offset++) if (projected[offset] != '\n' && projected[offset] != '\r') projected[offset] = ' ';
    }
    return new String(projected);
  }

  static String closeDelimiters(String source) {
    var stack = new ArrayDeque<String>();
    for (var token : tokens(source)) {
      String text = token.getText();
      if (token.getChannel() != Token.DEFAULT_CHANNEL) continue;
      switch (text) {
        case "(" -> stack.push(")");
        case "[" -> stack.push("]");
        case "{" -> stack.push("}");
        case ")", "]", "}" -> { if (!stack.isEmpty() && stack.peek().equals(text)) stack.pop(); }
        default -> { }
      }
    }
    return source + String.join("", stack);
  }

  static List<TextEdit> indentation(GroovyDocument document, FormattingOptions options) {
    var tokens = tokens(document.source);
    int depth = 0;
    var edits = new ArrayList<TextEdit>();
    var seen = new java.util.HashSet<Integer>();
    int tabSize = Math.max(1, Math.min(options.getTabSize(), 16));
    for (var token : tokens) {
      if (token.getChannel() != Token.DEFAULT_CHANNEL || token.getType() == GroovyLexer.NL) continue;
      String text = token.getText();
      int line = token.getLine() - 1;
      if (text.equals("}")) depth = Math.max(0, depth - 1);
      if (seen.add(line)) {
        int start = document.offset(new Position(line, 0));
        int end = start;
        while (end < document.source.length() && (document.source.charAt(end) == ' ' || document.source.charAt(end) == '\t')) end++;
        // A token after a multiline literal may share the literal's closing line. Leave that line intact.
        if (token.getCharPositionInLine() == end - start) {
          String indent = options.isInsertSpaces() ? " ".repeat(depth * tabSize) : "\t".repeat(depth);
          if (!document.source.substring(start, end).equals(indent)) edits.add(new TextEdit(document.range(start, end), indent));
        }
      }
      if (text.equals("{")) depth++;
    }
    return edits;
  }
}
