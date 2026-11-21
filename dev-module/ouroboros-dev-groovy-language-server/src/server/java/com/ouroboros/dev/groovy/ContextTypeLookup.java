package com.ouroboros.dev.groovy;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.jdt.groovy.model.GroovyCompilationUnit;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jdt.groovy.search.ITypeLookup;
import org.eclipse.jdt.groovy.search.TypeLookupResult;
import org.eclipse.jdt.groovy.search.TypeLookupResult.TypeConfidence;
import org.eclipse.jdt.groovy.search.VariableScope;
import org.eclipse.jdt.groovy.search.SimpleTypeLookup;

/** Projects the platform context through the Groovy-Eclipse type lookup extension. */
public final class ContextTypeLookup implements ITypeLookup {
  public static final QualifiedName CONTEXT = new QualifiedName("ouroboros-groovy", "context");
  static final QualifiedName COMPLETION = new QualifiedName("ouroboros-groovy", "completion");
  static final String COMPLETION_NAME = "__ouroboros_completion__";
  private CompletionPoint completion;
  private final SimpleTypeLookup stringLookup = new SimpleTypeLookup();

  @Override
  public void initialize(GroovyCompilationUnit unit, VariableScope scope) {
    try {
      stringLookup.initialize(unit, scope);
      completion = (CompletionPoint) unit.getResource().getSessionProperty(COMPLETION);
      String context = (String) unit.getResource().getSessionProperty(CONTEXT);
      if (context == null) return;
      // Incomplete source can have compiler diagnostics without a script class to bind variables to.
      var module = unit.getModuleNode();
      if (module == null || module.getClasses().isEmpty()) return;
      var response = JsonParser.parseString(context).getAsJsonObject();
      for (var item : response.getAsJsonObject("snapshot").getAsJsonArray("globals")) {
        var binding = item.getAsJsonObject();
        ClassNode type = project(binding.getAsJsonObject("shape").getAsJsonObject("schema"), unit);
        scope.addVariable(binding.get("name").getAsString(), type, module.getClasses().getFirst());
      }
    } catch (CoreException e) {
      throw new IllegalStateException("Could not load the code context", e);
    }
  }

  @Override
  public TypeLookupResult lookupType(Expression expression, VariableScope scope, ClassNode receiver) {
    if (completion != null && COMPLETION_NAME.equals(expression.getText())) {
      completion.receiver = receiver;
      completion.scope = scope;
    }
    if (receiver != null && receiver.getName().equals("groovy.lang.GString") && scope.isMethodCall()) {
      return stringLookup.lookupType(expression, scope, VariableScope.STRING_CLASS_NODE, false);
    }
    if (receiver == null || scope.isMethodCall() || !(expression instanceof ConstantExpression property)) return null;
    if (!(receiver instanceof ShapeMapNode map)) return null;
    ClassNode field = map.fields.get(property.getText());
    if (field == null) return null;
    return new TypeLookupResult(field, receiver, null, TypeConfidence.INFERRED, scope);
  }

  static final class CompletionPoint {
    ClassNode receiver;
    VariableScope scope;
  }

  private static ClassNode project(JsonObject schema, GroovyCompilationUnit unit) {
    if (schema.has("x-javaType")) return unit.getModuleInfo(true).resolver.resolve(schema.get("x-javaType").getAsString());
    String type = schema.has("type") ? schema.get("type").getAsString() : "any";
    return switch (type) {
      case "string" -> ClassHelper.STRING_TYPE;
      case "integer", "number" -> VariableScope.NUMBER_CLASS_NODE;
      case "boolean" -> ClassHelper.Boolean_TYPE;
      case "array" -> {
        var list = VariableScope.clonedList();
        list.setGenericsTypes(new GenericsType[] {new GenericsType(project(schema.getAsJsonObject("items"), unit))});
        yield list;
      }
      case "object" -> {
        var fields = new LinkedHashMap<String, ClassNode>();
        if (schema.has("properties")) schema.getAsJsonObject("properties").entrySet()
            .forEach(entry -> fields.put(entry.getKey(), project(entry.getValue().getAsJsonObject(), unit)));
        yield new ShapeMapNode(fields);
      }
      default -> ClassHelper.OBJECT_TYPE;
    };
  }

  /** A Map type reference carrying structural keys; GE clones type references during closure inference. */
  static final class ShapeMapNode extends ClassNode {
    final Map<String, ClassNode> fields;

    private ShapeMapNode(Map<String, ClassNode> fields) {
      super(Map.class);
      this.fields = Map.copyOf(fields);
      setRedirect(VariableScope.MAP_CLASS_NODE);
      setGenericsTypes(new GenericsType[] {new GenericsType(ClassHelper.STRING_TYPE), new GenericsType(ClassHelper.OBJECT_TYPE)});
    }

    @Override public ClassNode getPlainNodeReference() { return new ShapeMapNode(fields); }
    @Override public ClassNode getPlainNodeReference(boolean skipPrimitives) { return new ShapeMapNode(fields); }
  }
}
