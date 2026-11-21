package com.ouroboros.testing.groovy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.jdt.groovy.model.GroovyCompilationUnit;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jdt.groovy.search.ITypeLookup;
import org.eclipse.jdt.groovy.search.TypeLookupResult;
import org.eclipse.jdt.groovy.search.TypeLookupResult.TypeConfidence;
import org.eclipse.jdt.groovy.search.VariableScope;

/** Qualification adapter registered through GE's typeLookup extension. No parallel flow inference. */
public final class ContextTypeLookup implements ITypeLookup {
  public static final QualifiedName CONTEXT_FILE = new QualifiedName("semantic-probe", "context-file");

  @Override
  public void initialize(GroovyCompilationUnit unit, VariableScope scope) {
    try {
      String filename = unit.getResource().getPersistentProperty(CONTEXT_FILE);
      if (filename == null) return;
      System.out.println("CONTEXT_LOOKUP " + unit.getElementName() + " " + filename);
      var response = JsonParser.parseString(Files.readString(Path.of(filename))).getAsJsonObject();
      for (var item : response.getAsJsonObject("snapshot").getAsJsonArray("globals")) {
        var binding = item.getAsJsonObject();
        ClassNode type = project(binding.getAsJsonObject("shape").getAsJsonObject("schema"), unit);
        scope.addVariable(binding.get("name").getAsString(), type, unit.getModuleNode().getClasses().getFirst());
      }
    } catch (IOException | CoreException e) {
      throw new IllegalStateException("Could not load the qualification context", e);
    }
  }

  @Override
  public TypeLookupResult lookupType(Expression expression, VariableScope scope, ClassNode receiver) {
    if (receiver == null || scope.isMethodCall() || !(expression instanceof ConstantExpression property)) return null;
    if (!(receiver instanceof ShapeMapNode map)) return null;
    ClassNode field = map.fields.get(property.getText());
    if (field == null) return null;
    return new TypeLookupResult(field, receiver, expression, TypeConfidence.INFERRED, scope);
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
  private static final class ShapeMapNode extends ClassNode {
    private final Map<String, ClassNode> fields;

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
