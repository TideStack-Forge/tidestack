package com.ouroboros.businessmodel.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.stream.Collectors;

import javax.lang.model.SourceVersion;

import com.ouroboros.businessmodel.action.BusinessActionOutputs;
import com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.util.Json;

/** Optional source projections of the canonical dynamic business behavior contract. */
public final class BusinessBehaviorTypeGenerator {
  private static final Set<String> RESTRICTED_TYPE_NAMES = Set.of("var", "yield", "record", "sealed", "permits");
  private static final String JAVA_OBJECT_MAP = "java.util.Map<java.lang.String, java.lang.Object>";
  private BusinessBehaviorTypeGenerator() {
  }

  public record GeneratedTypes(Map<String, String> javaSources, String typeScript) {
    public GeneratedTypes {
      javaSources = Map.copyOf(javaSources);
    }
  }

  public static GeneratedTypes generate(BusinessModelBehavior behavior, String packageName, String className) {
    if (packageName == null || className == null || !SourceVersion.isName(packageName)
        || !SourceVersion.isIdentifier(className) || SourceVersion.isKeyword(className) || RESTRICTED_TYPE_NAMES.contains(className)) {
      throw new IllegalArgumentException("类型导出的 Java 包名和类名必须是合法标识符");
    }
    if (packageName.codePoints().anyMatch(Character::isIdentifierIgnorable)
        || className.codePoints().anyMatch(Character::isIdentifierIgnorable)) {
      throw new IllegalArgumentException("类型导出的 Java 包名和类名不能包含标识符解析时忽略的字符");
    }
    if (Set.of("java", "com").contains(className)) {
      throw new IllegalArgumentException("类型导出的 Java 类名不能遮蔽生成源码使用的包根 java 或 com");
    }
    String model = literal(behavior.getModelFullName());
    var javaTypes = new JavaTypes(className);
    String tsFactory = "create" + identifier(className);
    var tsNames = new Names(Set.of("businessActions", tsFactory));
    var methodNames = new Names(behavior.getActions().keySet().stream().map(BusinessBehaviorTypeGenerator::identifier).collect(Collectors.toSet()));
    var javaMethods = new StringBuilder();
    var tsTypes = new StringBuilder("import { businessActions } from 'ouroboros-sdk'\n\n");
    var tsMethods = new StringBuilder();
    for (var action : behavior.getActions().values()) {
      String member = identifier(action.getName());
      String stem = capitalize(member);
      Map<String, Object> partitions = schema(action.getInputSchema().get("properties"));
      String params = javaTypes.type(schema(partitions.get("params")), stem + "Params", "");
      String data = javaTypes.type(schema(partitions.get("data")), stem + "Input", "");
      String input = "com.ouroboros.businessmodel.action.BusinessActionInput<" + params + ", " + data + ">";
      boolean fileOutput = BusinessActionOutputs.isFile(action.getOutputSchema());
      String output = fileOutput ? "com.ouroboros.util.FileResult" : javaTypes.type(action.getOutputSchema(), stem + "Output", "");
      String fingerprint = literal(BusinessBehaviorContracts.actionFingerprint(action));
      javaMethods.append("  public ").append(output).append(' ').append(member).append('(').append(input).append(" input) {\n")
          .append("    java.lang.Object result = runtime.invoke(new com.ouroboros.businessmodel.action.BusinessActionInvocation(MODEL, ")
          .append(literal(action.getName())).append(", input")
          .append(", ").append(fingerprint).append("));\n");
      if (fileOutput) {
        javaMethods.append("    return com.ouroboros.util.FileResult.class.cast(result);\n  }\n");
      } else {
        javaMethods.append("    return com.ouroboros.util.Json.toBean(new com.fasterxml.jackson.core.type.TypeReference<")
            .append(output).append(">() {}, com.ouroboros.util.Json.toJsonString(result));\n  }\n");
      }
      String tsInput = tsNames.unique(stem + "Input");
      String tsOutput = tsNames.unique(stem + "Output");
      tsTypes.append("export type ").append(tsInput).append(" = ").append(tsActionInputType(action.getInputSchema())).append('\n')
          .append("export type ").append(tsOutput).append(" = ")
          .append(fileOutput ? "import('ouroboros-sdk').BusinessActionFile" : tsType(action.getOutputSchema())).append("\n");
      tsMethods.append("    [").append(literal(action.getName())).append("](input: ").append(tsInput).append(") {\n")
          .append(fileOutput ? "      return businessActions.invokeFile<" : "      return businessActions.invoke<").append(tsInput)
          .append(fileOutput ? "" : ", " + tsOutput).append(">(")
          .append("{ modelFullName: ").append(model).append(", actionName: ").append(literal(action.getName()))
          .append(", expectedContractFingerprint: ").append(fingerprint).append(" }, input")
          .append(")\n    },\n");
    }
    for (var event : behavior.getEvents().values()) {
      String stem = capitalize(identifier(event.getName()));
      String annotation = "  @com.ouroboros.businessmodel.annotation.BusinessEventPayload(modelFullName = " + model
          + ", eventName = " + literal(event.getName()) + ", schemaFingerprint = "
          + literal(BusinessBehaviorContracts.schemaFingerprint(event.getPayloadSchema())) + ")\n";
      String payload = javaTypes.type(event.getPayloadSchema(), stem + "Payload", annotation);
      javaMethods.append("  public static final com.ouroboros.businessmodel.event.BusinessEventIdentity ")
          .append(identifier(event.getName())).append("Event = new com.ouroboros.businessmodel.event.BusinessEventIdentity(MODEL, ")
          .append(literal(event.getName())).append(");\n")
          .append("  public static com.ouroboros.businessmodel.event.BusinessEventOccurrence<java.util.Map<java.lang.String, java.lang.Object>> ")
          .append(methodNames.unique("emit" + stem)).append("(com.ouroboros.businessmodel.event.BusinessEventPort port, ")
          .append("com.ouroboros.businessmodel.event.BusinessEventSubject subject, ").append(payload)
          .append(" payload, com.ouroboros.businessmodel.event.BusinessEventEmissionContext context) {\n")
          .append("    return port.emit(").append(identifier(event.getName())).append("Event, subject, payload, context.expectingSchema(")
          .append(literal(BusinessBehaviorContracts.schemaFingerprint(event.getPayloadSchema()))).append("));\n  }\n");
      tsTypes.append("export type ").append(tsNames.unique(stem + "Payload")).append(" = ").append(tsType(event.getPayloadSchema())).append("\n")
          .append("export const ").append(tsNames.unique(identifier(event.getName()) + "Event")).append(" = { modelFullName: ").append(model)
          .append(", eventName: ").append(literal(event.getName())).append(" } as const\n");
    }
    String java = "package " + packageName + ";\n\npublic final class " + className + " {\n"
        + "  public static final java.lang.String MODEL = " + model + ";\n"
        + "  private final com.ouroboros.businessmodel.action.BusinessActionRuntime runtime;\n"
        + "  public " + className + "(com.ouroboros.businessmodel.action.BusinessActionRuntime runtime) {\n"
        + "    this.runtime = java.util.Objects.requireNonNull(runtime);\n  }\n"
        + javaTypes.declarations + javaMethods + "}\n";
    String ts = tsTypes + "\nexport function " + tsFactory + "() {\n  return {\n    actions: {\n"
        + tsMethods + "    },\n  }\n}\n";
    return new GeneratedTypes(Map.of(packageName.replace('.', '/') + "/" + className + ".java", java), ts);
  }

  private static String literal(String value) {
    return Json.toJsonString(value);
  }

  private static String capitalize(String value) {
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  private static String identifier(String name) {
    StringBuilder result = new StringBuilder();
    name.codePoints().forEach(code -> {
      if (code == '_') {
        result.append("__");
      } else if (!(code >= 'a' && code <= 'z') && !(code >= 'A' && code <= 'Z') && !(code >= '0' && code <= '9')) {
        result.append("_u").append(Integer.toHexString(code)).append('_');
      } else {
        result.appendCodePoint(code);
      }
    });
    String value = result.toString();
    if (value.isEmpty() || !Character.isJavaIdentifierStart(value.charAt(0)) || SourceVersion.isKeyword(value)
        || Set.of("object", "getClass", "wait", "notify", "notifyAll", "hashCode", "toString", "clone", "finalize").contains(value)) {
      return "_" + value;
    }
    return value;
  }

  private static Map<String, Object> schema(Object value) {
    Map<String, Object> result = new TreeMap<>();
    if (value instanceof Map<?, ?> map) {
      map.forEach((key, nested) -> result.put(String.valueOf(key), nested));
    }
    return result;
  }

  private static boolean hasComposition(Map<String, Object> schema) {
    return schema.containsKey("$ref") || schema.containsKey("allOf") || schema.containsKey("anyOf") || schema.containsKey("oneOf")
        || schema.containsKey("if") || schema.containsKey("then") || schema.containsKey("else") || schema.containsKey("dependentSchemas");
  }

  private static String tsActionInputType(Map<String, Object> schema) {
    String type = tsType(schema);
    if ("object".equals(schema.get("type")) && !hasComposition(schema)
        && !schema.containsKey("patternProperties") && !schema.containsKey("unevaluatedProperties")) {
      return schema(schema.get("properties")).isEmpty() && Boolean.FALSE.equals(schema.get("additionalProperties"))
          ? "Record<string, never>" : type;
    }
    return "(" + type + ") & Record<string, unknown>";
  }

  private static String tsType(Map<String, Object> schema) {
    if (hasComposition(schema) || schema.containsKey("patternProperties") || schema.containsKey("unevaluatedProperties")) {
      return "unknown";
    }
    Object type = schema.get("type");
    if (type instanceof List<?> types) {
      return types.stream().map(value -> {
        var narrowed = new TreeMap<>(schema);
        narrowed.put("type", value);
        return tsType(narrowed);
      }).collect(Collectors.joining(" | "));
    }
    return switch (String.valueOf(type)) {
      case "string" -> "string";
      case "integer", "number" -> "number";
      case "boolean" -> "boolean";
      case "null" -> "null";
      case "array" -> "Array<" + tsType(schema(schema.get("items"))) + ">";
      case "object" -> {
        var required = schema.get("required") instanceof List<?> list ? list : List.of();
        var members = new ArrayList<String>();
        schema(schema.get("properties")).forEach((name, definition) -> members.add(literal(name)
            + (required.contains(name) ? "" : "?") + ": " + tsType(schema(definition))));
        if (!Boolean.FALSE.equals(schema.get("additionalProperties"))) {
          members.add("[key: string]: unknown");
        }
        yield "{ " + String.join("; ", members) + " }";
      }
      default -> "unknown";
    };
  }

  private static final class JavaTypes {
    private final StringBuilder declarations = new StringBuilder();
    private final Names names;

    private JavaTypes(String className) {
      names = new Names(Set.of(className));
    }

    String type(Map<String, Object> schema, String name, String annotation) {
      if (hasComposition(schema) || !(schema.get("type") instanceof String)) {
        return "java.lang.Object";
      }
      return switch (String.valueOf(schema.get("type"))) {
        case "string" -> "java.lang.String";
        case "integer" -> "java.math.BigInteger";
        case "number" -> "java.math.BigDecimal";
        case "boolean" -> "java.lang.Boolean";
        case "array" -> "java.util.List<" + type(schema(schema.get("items")), name + "Item", "") + ">";
        case "object" -> objectType(schema, name, annotation);
        default -> "java.lang.Object";
      };
    }

    private String objectType(Map<String, Object> schema, String name, String annotation) {
      var properties = schema(schema.get("properties"));
      // Open or nullable contracts stay as JSON maps so projection cannot discard values or collapse null into absence.
      if (!Boolean.FALSE.equals(schema.get("additionalProperties")) || properties.containsKey("") || schema.containsKey("patternProperties")
          || schema.containsKey("unevaluatedProperties") || properties.values().stream().anyMatch(value -> {
        var field = schema(value);
        return !(field.get("type") instanceof String) || "null".equals(field.get("type")) || hasComposition(field);
      })) {
        return JAVA_OBJECT_MAP;
      }
      name = names.unique(name);
      String recordName = name;
      List<String> fields = new ArrayList<>();
      properties.forEach((property, definition) -> fields.add("@com.fasterxml.jackson.annotation.JsonProperty("
          + literal(property) + ") " + type(schema(definition), recordName + "_" + identifier(property), "") + " " + identifier(property)));
      declarations.append(annotation).append("  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)\n")
          .append("  public record ").append(name).append('(').append(String.join(", ", fields)).append(") {}\n");
      return name;
    }
  }

  private static final class Names {
    private final Set<String> used;

    private Names(Set<String> reserved) {
      used = new HashSet<>(reserved);
    }

    private String unique(String name) {
      String candidate = name;
      int suffix = 2;
      while (!used.add(candidate)) {
        candidate = name + suffix++;
      }
      return candidate;
    }
  }
}
