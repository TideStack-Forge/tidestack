package com.ouroboros.dev.groovy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.eclipse.jdt.groovy.search.VariableScope;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

final class GroovyCompletions {
  private GroovyCompletions() {}

  static List<CompletionItem> complete(GroovyDocument document, Position position) throws Exception {
    int offset = document.offset(position), start = offset, end = offset;
    while (start > 0 && Character.isJavaIdentifierPart(document.source.charAt(start - 1))) start--;
    while (end < document.source.length() && Character.isJavaIdentifierPart(document.source.charAt(end))) end++;
    String prefix = document.source.substring(start, offset);
    boolean member = start > 0 && document.source.charAt(start - 1) == '.';
    var point = new ContextTypeLookup.CompletionPoint();
    document.unit.getResource().setSessionProperty(ContextTypeLookup.COMPLETION, point);
    String recovered = document.source.substring(0, start) + ContextTypeLookup.COMPLETION_NAME + document.source.substring(end);
    recovered = GroovySyntax.closeDelimiters(recovered);
    try {
      document.unit.getBuffer().setContents(GroovySyntax.forEditor(recovered));
      document.unit.reconcile(org.eclipse.jdt.core.ICompilationUnit.NO_AST, org.eclipse.jdt.core.ICompilationUnit.FORCE_PROBLEM_DETECTION, null, null);
      document.unit.getNewModuleInfo();
      document.visit();
      var candidates = new LinkedHashMap<String, CompletionItem>();
      Range range = document.range(start, end);
      if (member && point.receiver != null) addMembers(point.receiver, candidates, range);
      if (!member && point.scope != null) {
        for (var variable : point.scope) add(candidates, variable.name, CompletionItemKind.Variable, variable.type.toString(false), variable.name, range);
        addMembers(point.scope.getDelegateOrThis(), candidates, range);
      }
      return candidates.values().stream().filter(c -> c.getLabel().startsWith(prefix)).toList();
    } finally {
      document.unit.getResource().setSessionProperty(ContextTypeLookup.COMPLETION, null);
      document.unit.getBuffer().setContents(GroovySyntax.forEditor(document.source));
      document.unit.reconcile(org.eclipse.jdt.core.ICompilationUnit.NO_AST, org.eclipse.jdt.core.ICompilationUnit.FORCE_PROBLEM_DETECTION, null, null);
      document.unit.getNewModuleInfo();
    }
  }

  private static void addMembers(ClassNode type, LinkedHashMap<String, CompletionItem> candidates, Range range) {
    if (type == null) return;
    if (type instanceof ContextTypeLookup.ShapeMapNode map) map.fields.forEach((name, field) ->
        add(candidates, name, CompletionItemKind.Property, field.toString(false), name, range));
    var hierarchy = new LinkedHashSet<ClassNode>();
    VariableScope.createTypeHierarchy(type, hierarchy, false);
    if (type.getName().equals("groovy.lang.GString")) hierarchy.add(VariableScope.STRING_CLASS_NODE);
    for (var node : hierarchy) {
      for (var field : node.getFields()) if (field.isPublic() && !field.isSynthetic())
        add(candidates, field.getName(), CompletionItemKind.Field, field.getType().toString(false), field.getName(), range);
      for (var property : node.getProperties()) if (property.isPublic())
        add(candidates, property.getName(), CompletionItemKind.Property, property.getType().toString(false), property.getName(), range);
      for (var method : node.getMethods()) {
        if (!method.isPublic() || method.isSynthetic() || method.getName().contains("$")) continue;
        add(candidates, method.getName(), CompletionItemKind.Method, signature(method), method.getName(), range);
        String name = method.getName();
        if (method.getParameters().length == 0 && name.startsWith("get") && name.length() > 3 && !name.equals("getClass")) {
          String property = Character.toLowerCase(name.charAt(3)) + name.substring(4);
          add(candidates, property, CompletionItemKind.Property, method.getReturnType().toString(false), property, range);
        }
      }
    }
    // Groovy's extension methods are compiler metadata, including their closure annotations.
    for (var category : List.of(VariableScope.DGM_CLASS_NODE, VariableScope.DGSM_CLASS_NODE)) {
      for (var method : category.getMethods()) {
        var parameters = method.getParameters();
        if (!method.isPublic() || !method.isStatic() || parameters.length == 0) continue;
        ClassNode receiver = parameters[0].getType();
        if (receiver.equals(type) || type.isDerivedFrom(receiver) || type.implementsInterface(receiver))
          add(candidates, method.getName(), CompletionItemKind.Method, signature(method), method.getName(), range);
      }
    }
  }

  static String signature(MethodNode method) {
    return method.getName() + "(" + java.util.Arrays.stream(method.getParameters())
        .map(p -> p.getType().toString(false) + " " + p.getName()).collect(java.util.stream.Collectors.joining(", "))
        + "): " + method.getReturnType().toString(false);
  }

  private static void add(LinkedHashMap<String, CompletionItem> candidates, String name, CompletionItemKind kind,
      String detail, String insert, Range range) {
    if (name.startsWith("__ouroboros")) return;
    var item = new CompletionItem(name);
    item.setKind(kind);
    item.setSortText((kind == CompletionItemKind.Method ? "1" : "0") + name);
    item.setDetail(detail);
    item.setTextEdit(Either.forLeft(new TextEdit(range, insert)));
    candidates.putIfAbsent(name + ":" + detail, item);
  }
}
