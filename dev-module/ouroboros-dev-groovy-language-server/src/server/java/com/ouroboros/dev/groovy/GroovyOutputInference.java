package com.ouroboros.dev.groovy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;

import com.ouroboros.codeintelligence.type.TypeShape;

/** Returns and structural literals reuse the same typed module and occurrence cache as editor features. */
final class GroovyOutputInference {
  private final GroovyDocument document;
  GroovyOutputInference(GroovyDocument document) { this.document = document; }
  TypeShape inferOutput(Map<String, TypeShape> bindings) {
    if (document.source.length() > 65536) return TypeShape.unknown("script-size-budget");
    var module = document.unit.getModuleNode();
    if (module == null) return TypeShape.unknown("groovy-syntax");
    document.analyze();
    return statement(module.getStatementBlock(), new LinkedHashMap<>(bindings), true, 0).result();
  }

  private ScriptTypeState statement(Statement node, Map<String, TypeShape> scope, boolean implicitReturn, int depth) {
    if (depth > 64) return ScriptTypeState.unknown("script-depth-budget");
    if (node instanceof BlockStatement block) {
      var state = ScriptTypeState.continuing();
      var statements = block.getStatements();
      for (int i = 0; i < statements.size() && state.fallsThrough(); i++) {
        state = state.then(statement(statements.get(i), scope, implicitReturn && i == statements.size() - 1, depth + 1));
      }
      return state;
    }
    if (node instanceof ReturnStatement returned) return new ScriptTypeState(expression(returned.getExpression(), scope, depth + 1), false);
    if (node instanceof IfStatement branch) {
      var pass = new LinkedHashMap<>(scope); var fail = new LinkedHashMap<>(scope);
      return ScriptTypeState.join(scope, pass, statement(branch.getIfBlock(), pass, implicitReturn, depth + 1),
          fail, statement(branch.getElseBlock(), fail, implicitReturn, depth + 1));
    }
    if (node instanceof ExpressionStatement value) {
      var expr = value.getExpression();
      if (expr instanceof BinaryExpression binary && "=".equals(binary.getOperation().getText())) {
        if (!(binary.getLeftExpression() instanceof VariableExpression variable)) return ScriptTypeState.unknown("dynamic-script-mutation");
        var shape = expression(binary.getRightExpression(), scope, depth + 1);
        scope.put(variable.getName(), shape);
        return implicitReturn ? new ScriptTypeState(shape, false) : ScriptTypeState.continuing();
      }
      if (!implicitReturn) {
        if (expr instanceof ConstantExpression || expr instanceof VariableExpression) return ScriptTypeState.continuing();
        return ScriptTypeState.unknown("unknown-script-expression-effects");
      }
      return new ScriptTypeState(expression(expr, scope, depth + 1), false);
    }
    if (node instanceof ThrowStatement) return new ScriptTypeState(null, false);
    if (node instanceof EmptyStatement) return implicitReturn ? new ScriptTypeState(TypeShape.scalar("null"), false) : ScriptTypeState.continuing();
    return ScriptTypeState.unknown("unsupported-groovy-statement:" + node.getClass().getSimpleName());
  }

  private TypeShape expression(Expression node, Map<String, TypeShape> scope, int depth) {
    if (depth > 64) return TypeShape.unknown("script-depth-budget");
    if (node instanceof ConstantExpression value) return TypeShape.literal(value.getValue());
    if (node instanceof VariableExpression variable) return scope.getOrDefault(variable.getName(), TypeShape.unknown("unbound-script-variable"));
    if (node instanceof GStringExpression) return TypeShape.scalar("string");
    if (node instanceof MapExpression map) {
      var fields = new LinkedHashMap<String, TypeShape>();
      for (var entry : map.getMapEntryExpressions()) {
        if (!(entry.getKeyExpression() instanceof ConstantExpression key)) return TypeShape.unknown("dynamic-map-key");
        fields.put(String.valueOf(key.getValue()), expression(entry.getValueExpression(), scope, depth + 1));
      }
      return TypeShape.object(fields, "map");
    }
    if (node instanceof ListExpression list) {
      TypeShape item = null;
      for (var value : list.getExpressions()) item = ScriptTypeState.union(item, expression(value, scope, depth + 1));
      return TypeShape.array(item == null ? TypeShape.unknown("empty-script-array") : item);
    }
    if (node instanceof PropertyExpression property) return expression(property.getObjectExpression(), scope, depth + 1).property(property.getPropertyAsString());
    if (node instanceof TernaryExpression branch) return expression(branch.getTrueExpression(), scope, depth + 1).merge(expression(branch.getFalseExpression(), scope, depth + 1));
    if (node instanceof BooleanExpression || node instanceof NotExpression) return TypeShape.scalar("boolean");
    if (node instanceof CastExpression || node instanceof ConstructorCallExpression) return semantic(node, depth + 1);
    if (node instanceof BinaryExpression binary) {
      var operator = binary.getOperation().getText();
      var left = expression(binary.getLeftExpression(), scope, depth + 1);
      if ("[".equals(operator)) {
        if ("array".equals(left.type())) return left.element();
        if (binary.getRightExpression() instanceof ConstantExpression key) return left.property(String.valueOf(key.getValue()));
      }
      if (Set.of("==", "!=", "<", ">", "<=", ">=", "&&", "||", "in", "instanceof", "==~").contains(operator)) return TypeShape.scalar("boolean");
      if (Set.of("+", "-", "*", "/", "%", "**").contains(operator)) {
        var right = expression(binary.getRightExpression(), scope, depth + 1);
        return TypeShape.scalar("+".equals(operator) && ("string".equals(left.type()) || "string".equals(right.type())) ? "string" : "number");
      }
    }
    if (node instanceof MethodCallExpression call && call.getMethodAsString() != null && call.getArguments() instanceof TupleExpression args) {
      var signature = ScriptTypeState.method(expression(call.getObjectExpression(), scope, depth + 1), call.getMethodAsString(), args.getExpressions().size());
      return signature.isUnknown() ? semantic(node, depth + 1) : signature;
    }
    return TypeShape.unknown("unsupported-groovy-expression:" + node.getClass().getSimpleName());
  }
  private TypeShape semantic(Expression expression, int depth) {
    var occurrence = document.analyze().stream().filter(item -> item.node() == expression
        || item.node().getStart() == expression.getStart() && item.node().getEnd() == expression.getEnd())
        .filter(item -> !"UNKNOWN".equals(item.confidence()))
        .min(java.util.Comparator.comparingInt(item -> item.node() == expression ? 0 : 1)).orElse(null);
    return occurrence == null ? TypeShape.unknown("unresolved-groovy-type") : type(occurrence.type(), depth, new java.util.HashSet<>());
  }

  private TypeShape type(org.codehaus.groovy.ast.ClassNode node, int depth, Set<String> visiting) {
    if (node == null || depth > 16) return TypeShape.unknown("groovy-type-budget");
    if (node.isArray()) return TypeShape.array(type(node.getComponentType(), depth + 1, visiting));
    var name = node.getName();
    if (Set.of("java.lang.String", "java.lang.CharSequence", "char", "java.lang.Character").contains(name)) return TypeShape.scalar("string");
    if (Set.of("boolean", "java.lang.Boolean").contains(name)) return TypeShape.scalar("boolean");
    if (Set.of("byte", "short", "int", "long", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.math.BigInteger").contains(name)) return TypeShape.scalar("integer");
    if (Set.of("float", "double", "java.lang.Float", "java.lang.Double", "java.math.BigDecimal", "java.lang.Number").contains(name)) return TypeShape.scalar("number");
    if (name.equals("void")) return TypeShape.scalar("null");
    if (name.equals("java.lang.Object") || node.isGenericsPlaceHolder()) return TypeShape.unknown("dynamic-groovy-type");
    if (name.startsWith("java.util.") && (name.contains("List") || name.contains("Collection") || name.contains("Set") || name.contains("Iterable"))) {
      var generics = node.getGenericsTypes();
      return TypeShape.array(generics == null || generics.length == 0 ? TypeShape.unknown("raw-collection") : type(generics[0].getType(), depth + 1, visiting));
    }
    if (name.startsWith("java.util.") && name.contains("Map")) return TypeShape.unknown("dynamic-map-keys");
    if (!visiting.add(name)) return TypeShape.unknown("recursive-groovy-type");
    var fields = new LinkedHashMap<String, TypeShape>();
    var generics = org.codehaus.groovy.ast.tools.GenericsUtils.createGenericsSpec(node);
    try {
      for (var property : node.getProperties()) if (!property.isStatic()) fields.put(property.getName(), type(org.codehaus.groovy.ast.tools.GenericsUtils.correctToGenericsSpecRecurse(generics, property.getType()), depth + 1, visiting));
      for (var method : node.getMethods()) {
        var methodName = method.getName();
        if (method.isStatic() || !method.isPublic() || method.getParameters().length != 0 || methodName.equals("getClass") || methodName.equals("getMetaClass")) continue;
        String property = methodName.startsWith("get") && methodName.length() > 3 ? java.beans.Introspector.decapitalize(methodName.substring(3))
            : methodName.startsWith("is") && methodName.length() > 2 && Set.of("boolean", "java.lang.Boolean").contains(method.getReturnType().getName()) ? java.beans.Introspector.decapitalize(methodName.substring(2)) : null;
        if (property != null) fields.putIfAbsent(property, type(org.codehaus.groovy.ast.tools.GenericsUtils.correctToGenericsSpecRecurse(generics, method.getReturnType()), depth + 1, visiting));
        if (fields.size() > 256) break;
      }
      return TypeShape.object(fields, "java").annotated("x-javaType", name);
    } finally { visiting.remove(name); }
  }

}
