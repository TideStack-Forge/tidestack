package com.ouroboros.dev.codeintelligence.analysis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.oracle.js.parser.ErrorManager;
import com.oracle.js.parser.Parser;
import com.oracle.js.parser.ParserException;
import com.oracle.js.parser.ScriptEnvironment;
import com.oracle.js.parser.Source;
import com.oracle.js.parser.ir.*;
import com.ouroboros.codeintelligence.type.TypeShape;

/** Uses the deployed GraalJS version's parser directly; no polyglot Context/Engine or user execution. */
public final class JavascriptOutputInference {

  public TypeShape inferOutput(String source, Map<String, TypeShape> bindings) {
    if (source == null || source.length() > 65536) return TypeShape.unknown("script-size-budget");
    try {
      var parser = new Parser(ScriptEnvironment.builder().ecmaScriptVersion(2021).build(),
          Source.sourceFor("authoring.js", source), new ErrorManager.ThrowErrorManager());
      var function = parser.parseFunctionBody(false, false);
      return function == null ? TypeShape.unknown("javascript-syntax")
          : block(function.getBody(), new LinkedHashMap<>(bindings), 0).result();
    } catch (ParserException exception) {
      return TypeShape.unknown("javascript-syntax");
    } catch (StackOverflowError error) {
      return TypeShape.unknown("script-parse-depth-budget");
    }
  }

  private ScriptTypeState block(Block block, Map<String, TypeShape> scope, int depth) {
    if (depth > 64) return ScriptTypeState.unknown("script-depth-budget");
    var state = ScriptTypeState.continuing();
    for (var node : block.getStatements()) {
      if (!state.fallsThrough()) break;
      state = state.then(statement(node, scope, depth + 1));
    }
    return state;
  }

  private ScriptTypeState statement(Statement node, Map<String, TypeShape> scope, int depth) {
    if (depth > 64) return ScriptTypeState.unknown("script-depth-budget");
    if (node instanceof BlockStatement nested) return block(nested.getBlock(), scope, depth + 1);
    if (node instanceof ReturnNode returned) return new ScriptTypeState(expression(returned.getExpression(), scope, depth + 1), false);
    if (node instanceof VarNode variable) {
      if (variable.isDestructuring()) return ScriptTypeState.unknown("script-destructuring");
      scope.put(variable.getName().getName(), expression(variable.getInit(), scope, depth + 1));
      return ScriptTypeState.continuing();
    }
    if (node instanceof IfNode branch) {
      var pass = new LinkedHashMap<>(scope); var fail = new LinkedHashMap<>(scope);
      return ScriptTypeState.join(scope, pass, block(branch.getPass(), pass, depth + 1), fail,
          branch.getFail() == null ? ScriptTypeState.continuing() : block(branch.getFail(), fail, depth + 1));
    }
    if (node instanceof ExpressionStatement value) {
      if (value.getExpression() instanceof BinaryNode binary && binary.isAssignment()) {
        if (!"ASSIGN".equals(binary.tokenType().name())) return ScriptTypeState.unknown("script-compound-assignment");
        if (!(binary.getLhs() instanceof IdentNode variable)) return ScriptTypeState.unknown("dynamic-script-mutation");
        scope.put(variable.getName(), expression(binary.getRhs(), scope, depth + 1));
        return ScriptTypeState.continuing();
      }
      // Discarded expressions can hide mutations, calls, or increment/decrement operations.
      if (value.getExpression() instanceof LiteralNode<?> literal && !literal.isArray()
          || value.getExpression() instanceof IdentNode) return ScriptTypeState.continuing();
      return ScriptTypeState.unknown("unknown-script-expression-effects");
    }
    if (node instanceof ThrowNode) return new ScriptTypeState(null, false);
    if (node instanceof EmptyNode) return ScriptTypeState.continuing();
    return ScriptTypeState.unknown("unsupported-javascript-statement:" + node.getClass().getSimpleName());
  }

  private TypeShape expression(Expression node, Map<String, TypeShape> scope, int depth) {
    if (depth > 64) return TypeShape.unknown("script-depth-budget");
    if (node == null) return TypeShape.scalar("null");
    if (node instanceof LiteralNode<?> value) {
      if (!value.isArray()) return TypeShape.literal(value.getValue());
      TypeShape item = null;
      for (var element : value.getElementExpressions()) item = ScriptTypeState.union(item, expression(element, scope, depth + 1));
      return TypeShape.array(item == null ? TypeShape.unknown("empty-script-array") : item);
    }
    if (node instanceof IdentNode variable) return scope.getOrDefault(variable.getName(), TypeShape.unknown("unbound-script-variable"));
    if (node instanceof ObjectNode object) {
      var fields = new LinkedHashMap<String, TypeShape>();
      for (var property : object.getElements()) {
        if (property.isComputed() || property.isRest()) return TypeShape.unknown("dynamic-script-object");
        fields.put(property.getKeyName(), property.isAccessor() ? TypeShape.unknown("script-getter") : expression(property.getValue(), scope, depth + 1));
      }
      return TypeShape.object(fields, "map");
    }
    if (node instanceof AccessNode property) return expression(property.getBase(), scope, depth + 1).property(property.getProperty());
    if (node instanceof IndexNode index) {
      var owner = expression(index.getBase(), scope, depth + 1);
      if ("array".equals(owner.type())) return owner.element();
      if (index.getIndex() instanceof LiteralNode<?> key) return owner.property(String.valueOf(key.getValue()));
    }
    if (node instanceof JoinPredecessorExpression joined) return expression(joined.getExpression(), scope, depth + 1);
    if (node instanceof TernaryNode branch) return expression(branch.getTrueExpression(), scope, depth + 1).merge(expression(branch.getFalseExpression(), scope, depth + 1));
    if (node instanceof BinaryNode binary) {
      if (binary.isComparison() || binary.isRelational()) return TypeShape.scalar("boolean");
      var left = expression(binary.getLhs(), scope, depth + 1); var right = expression(binary.getRhs(), scope, depth + 1);
      if (binary.isLogical()) return left.merge(right);
      if (Set.of("ADD", "SUB", "MUL", "DIV", "MOD", "EXP").contains(binary.tokenType().name())) {
        return TypeShape.scalar("ADD".equals(binary.tokenType().name()) && ("string".equals(left.type()) || "string".equals(right.type())) ? "string" : "number");
      }
    }
    if (node instanceof CallNode call && call.getFunction() instanceof AccessNode method) {
      return ScriptTypeState.method(expression(method.getBase(), scope, depth + 1), method.getProperty(), call.getArgs().size());
    }
    return TypeShape.unknown("unsupported-javascript-expression:" + node.getClass().getSimpleName());
  }
}
