package com.ouroboros.logicflow.authoring.analysis;
import java.util.LinkedHashMap;
import java.util.Map;
import com.ouroboros.codeintelligence.type.TypeShape;
public final class NodeScopes {
  private NodeScopes() {}
  public static Map<String, TypeShape> ordinary(NodeAnalysisInput input) {
    var scope = new LinkedHashMap<String, TypeShape>();
    for (int i = 0; i < input.inputs().size(); i++) scope.put("$" + i, input.inputs().get(i).shape());
    for (int i = 0; i < Math.min(input.inputs().size(), input.node().getInputPorts().size()); i++) {
      var name = input.node().getInputPorts().get(i).getName();
      if (name != null && !name.isBlank()) scope.putIfAbsent("$" + name, input.inputs().get(i).shape());
    }
    if (input.inputs().size() == 1 && "map".equals(input.firstInput().representation())) scope.putAll(input.firstInput().properties());
    scope.putAll(input.globals());
    return scope;
  }
  public static Map<String, TypeShape> item(NodeAnalysisInput input, boolean mapping) {
    var parent = ordinary(input);
    var item = "array".equals(input.firstInput().type()) ? input.firstInput().element() : input.firstInput();
    var scope = new LinkedHashMap<String, TypeShape>();
    if (mapping) scope.putAll(parent);
    if ("map".equals(item.representation())) scope.putAll(item.properties());
    scope.put("$0", item);
    if (mapping) scope.put("$item", item);
    scope.put("$index", TypeShape.scalar("integer"));
    scope.put("$parentContext", TypeShape.object(parent, "map"));
    scope.putAll(input.globals());
    return scope;
  }
  public static String pointer(String field) { return field.replace("~", "~0").replace("/", "~1"); }
}
