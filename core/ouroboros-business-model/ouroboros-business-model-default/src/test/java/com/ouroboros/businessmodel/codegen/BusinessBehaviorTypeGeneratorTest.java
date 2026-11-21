package com.ouroboros.businessmodel.codegen;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.ouroboros.businessmodel.action.BusinessActionInvocation;
import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.businessmodel.action.BusinessObjectReference;
import com.ouroboros.businessmodel.annotation.BusinessEventPayload;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.EventDefinition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessBehaviorTypeGeneratorTest {
  @TempDir Path temporary;
  private final java.util.List<URLClassLoader> loaders = new java.util.ArrayList<>();

  @AfterEach
  void closeGeneratedLoaders() throws Exception {
    for (var loader : loaders) {
      loader.close();
    }
  }

  @Test
  void compiledJavaClientInvokesTheDynamicIdentityWithTypedInputOutputAndObjectReference() throws Exception {
    var action = action();

    var event = new EventDefinition();
    event.setName("submitted");
    event.setSubject("model");
    event.setPayloadSchema(schema("orderId", "string"));
    var behavior = new BusinessModelBehavior("sales.Order", List.of(action), List.of(event), List.of());
    var types = BusinessBehaviorTypeGenerator.generate(behavior, "example.contract", "OrderBusiness");
    var type = compile(types.javaSources(), "example.contract.OrderBusiness");
    var runtime = mock(BusinessActionRuntime.class);
    when(runtime.invoke(any())).thenReturn(Map.of("accepted", true));
    Object model = type.getConstructor(BusinessActionRuntime.class).newInstance(runtime);
    Object order = model;
    var inputType = type.getClassLoader().loadClass("example.contract.OrderBusiness$SubmitInput");
    Object input = inputType.getConstructor(String.class).newInstance("confirmed");
    Object output = type.getMethod("submit", com.ouroboros.businessmodel.action.BusinessActionInput.class).invoke(order, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(input));
    assertEquals(true, output.getClass().getMethod("accepted").invoke(output));
    var invocation = ArgumentCaptor.forClass(BusinessActionInvocation.class);
    verify(runtime).invoke(invocation.capture());
    assertEquals(new BusinessActionInvocation("sales.Order", "submit", com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(input), BusinessBehaviorContracts.actionFingerprint(action)), invocation.getValue());
    var payloadType = type.getClassLoader().loadClass("example.contract.OrderBusiness$SubmittedPayload");
    var annotation = payloadType.getAnnotation(BusinessEventPayload.class);
    assertEquals("sales.Order", annotation.modelFullName());
    assertEquals("submitted", annotation.eventName());
    assertEquals(BusinessBehaviorContracts.schemaFingerprint(event.getPayloadSchema()), annotation.schemaFingerprint());
    Path artifacts = Path.of("target/generated-test-contracts");
    Files.createDirectories(artifacts);
    Files.writeString(artifacts.resolve("OrderBusiness.ts"), types.typeScript());
  }

  @Test
  void generatedFileClientPreservesOwnershipWithoutJsonConversion() throws Exception {
    var action = action(); action.setName("export");
    action.setOutputSchema(Map.of("type", "string", "format", "binary", "contentMediaType", "text/plain"));
    var types = BusinessBehaviorTypeGenerator.generate(new BusinessModelBehavior("sales.Order", List.of(action), List.of(), List.of()),
        "example.file", "FileBusiness");
    var type = compile(types.javaSources(), "example.file.FileBusiness");
    var runtime = mock(BusinessActionRuntime.class);
    var path = Files.writeString(temporary.resolve("export.tmp"), "native content");
    try (var result = com.ouroboros.util.FileResult.temporary(path, "export.txt", "text/plain")) {
      when(runtime.invoke(any())).thenReturn(result);
      var client = type.getConstructor(BusinessActionRuntime.class).newInstance(runtime);
      assertSame(result, type.getMethod("export", com.ouroboros.businessmodel.action.BusinessActionInput.class)
          .invoke(client, com.ouroboros.businessmodel.action.BusinessActionInput.empty()));
      try (var content = result.openStream()) { assertEquals("native content", new String(content.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)); }
    }
    assertFalse(Files.exists(path)); verify(runtime, times(1)).invoke(any());
    Files.createDirectories(Path.of("target/generated-test-contracts"));
    Files.writeString(Path.of("target/generated-test-contracts/FileBusiness.ts"), types.typeScript());
  }

  @Test
  void openInputPreservesUnknownFields() throws Exception {
    for (var schema : List.<Map<String, Object>>of(Map.of(), Map.of("type", "object"),
        Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "object"))))) {
      var action = action();
      action.setInputSchema(dataSchema(schema));
      var types = BusinessBehaviorTypeGenerator.generate(new BusinessModelBehavior("sales.Order",
          List.of(action), List.of(), List.of()), "example.open", "OpenBusiness");
      var type = compile(types.javaSources(), "example.open.OpenBusiness");
      var runtime = mock(BusinessActionRuntime.class);
      when(runtime.invoke(any())).thenReturn(Map.of("accepted", true));
      var model = type.getConstructor(BusinessActionRuntime.class).newInstance(runtime);
      var resource = new java.io.ByteArrayInputStream(new byte[]{1, 2, 3});
      type.getMethod("submit", com.ouroboros.businessmodel.action.BusinessActionInput.class).invoke(model, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("extra", 7, "file", resource)));
      var capture = ArgumentCaptor.forClass(BusinessActionInvocation.class);
      verify(runtime).invoke(capture.capture());
      assertEquals(7, ((Map<?, ?>) capture.getValue().input().data()).get("extra"));
      assertSame(resource, ((Map<?, ?>) capture.getValue().input().data()).get("file"));
      assertEquals(3, resource.available());
      Files.createDirectories(Path.of("target/generated-test-contracts"));
      Files.writeString(Path.of("target/generated-test-contracts/OpenBusiness.ts"), types.typeScript());
    }
  }

  @Test
  void optionalFieldsOmitAbsentValuesAndNullableFieldsPreserveExplicitNull() throws Exception {
    var action = action();
    action.setInputSchema(dataSchema(Map.of("type", "object", "properties", Map.of("comment", Map.of("type", "string")),
        "additionalProperties", false)));
    var type = compile(BusinessBehaviorTypeGenerator.generate(new BusinessModelBehavior("sales.Order",
        List.of(action), List.of(), List.of()), "example.optional", "OrderBusiness").javaSources(), "example.optional.OrderBusiness");
    var runtime = mock(BusinessActionRuntime.class);
    when(runtime.invoke(any())).thenReturn(Map.of("accepted", true));
    var inputType = type.getClassLoader().loadClass("example.optional.OrderBusiness$SubmitInput");
    var model = type.getConstructor(BusinessActionRuntime.class).newInstance(runtime);
    type.getMethod("submit", com.ouroboros.businessmodel.action.BusinessActionInput.class).invoke(model, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(inputType.getConstructor(String.class).newInstance(new Object[]{null})));
    var capture = ArgumentCaptor.forClass(BusinessActionInvocation.class);
    verify(runtime).invoke(capture.capture());
    assertTrue(capture.getValue().input().hasData());

    action.setInputSchema(dataSchema(Map.of("type", "object", "properties", Map.of("comment", Map.of("type", List.of("string", "null"))),
        "additionalProperties", false)));
    var nullableType = compile(BusinessBehaviorTypeGenerator.generate(new BusinessModelBehavior("sales.Order",
        List.of(action), List.of(), List.of()), "example.nullable", "OrderBusiness").javaSources(), "example.nullable.OrderBusiness");
    var nullableModel = nullableType.getConstructor(BusinessActionRuntime.class).newInstance(runtime);
    Map<String, Object> explicitNull = new LinkedHashMap<>();
    explicitNull.put("comment", null);
    nullableType.getMethod("submit", com.ouroboros.businessmodel.action.BusinessActionInput.class).invoke(nullableModel, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(explicitNull));
    verify(runtime, times(2)).invoke(capture.capture());
    assertEquals(explicitNull, capture.getValue().input().data());
    assertTrue(capture.getValue().input().hasData() && ((Map<?, ?>) capture.getValue().input().data()).containsKey("comment"));
  }

  @Test
  void patternPropertiesAndBooleanSchemasPreserveDynamicValues() throws Exception {
    var action = action();
    action.setInputSchema(dataSchema(Map.of("type", "object", "patternProperties", Map.of("^x", Map.of("type", "number")),
        "additionalProperties", false)));
    action.setOutputSchema(Map.of("type", "array", "items", true));
    var type = compile(BusinessBehaviorTypeGenerator.generate(new BusinessModelBehavior("sales.Order",
        List.of(action), List.of(), List.of()), "example.pattern", "OrderBusiness").javaSources(), "example.pattern.OrderBusiness");
    var runtime = mock(BusinessActionRuntime.class);
    when(runtime.invoke(any())).thenReturn(List.of(3, "text", true));
    var model = type.getConstructor(BusinessActionRuntime.class).newInstance(runtime);
    assertEquals(List.of(3, "text", true), type.getMethod("submit", com.ouroboros.businessmodel.action.BusinessActionInput.class).invoke(model, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("xExtra", 7))));
    var capture = ArgumentCaptor.forClass(BusinessActionInvocation.class);
    verify(runtime).invoke(capture.capture());
    assertEquals(Map.of("xExtra", 7), capture.getValue().input().data());
  }

  @Test
  void enclosingTypeCollisionAndLanguageReservedMemberNamesCompileAndKeepBusinessIdentity() throws Exception {
    var action = action();
    action.setName("object");
    action.setInputSchema(dataSchema(schema("getClass", "string")));
    var type = compile(BusinessBehaviorTypeGenerator.generate(new BusinessModelBehavior("sales.Order",
        List.of(action), List.of(), List.of()), "example.names", "_objectInput").javaSources(), "example.names._objectInput");
    var runtime = mock(BusinessActionRuntime.class);
    when(runtime.invoke(any())).thenReturn(Map.of("accepted", true));
    var inputType = type.getClassLoader().loadClass("example.names._objectInput$_objectInput2");
    var model = type.getConstructor(BusinessActionRuntime.class).newInstance(runtime);
    type.getMethod("_object", com.ouroboros.businessmodel.action.BusinessActionInput.class).invoke(model, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(inputType.getConstructor(String.class).newInstance("kept")));
    var capture = ArgumentCaptor.forClass(BusinessActionInvocation.class);
    verify(runtime).invoke(capture.capture());
    assertEquals("object", capture.getValue().actionName());
    assertNotNull(capture.getValue().input().data());
    assertNotNull(compile(BusinessBehaviorTypeGenerator.generate(new BusinessModelBehavior("sales.Order",
        List.of(action()), List.of(), List.of()), "example.shadow", "String").javaSources(), "example.shadow.String"));
    assertNotNull(compile(BusinessBehaviorTypeGenerator.generate(new BusinessModelBehavior("sales.Order",
        List.of(action()), List.of(), List.of()), "example.shadow", "Object").javaSources(), "example.shadow.Object"));
  }

  @Test
  void unicodeBusinessNamesKeepTheirWireIdentityInCompiledJavaAndTypeScriptArtifacts() throws Exception {
    var action = action();
    action.setName("€pay");
    var event = new EventDefinition();
    event.setName("€paid");
    event.setPayloadSchema(schema("orderId", "string"));
    var types = BusinessBehaviorTypeGenerator.generate(new BusinessModelBehavior("sales.Order", List.of(action),
        List.of(event), List.of()), "example.unicode", "€Client");
    var type = compile(types.javaSources(), "example.unicode.€Client");
    var runtime = mock(BusinessActionRuntime.class);
    when(runtime.invoke(any())).thenReturn(Map.of("accepted", true));
    var inputType = type.getClassLoader().loadClass("example.unicode.€Client$_u20ac_payInput");
    var client = type.getConstructor(BusinessActionRuntime.class).newInstance(runtime);
    type.getMethod("_u20ac_pay", com.ouroboros.businessmodel.action.BusinessActionInput.class).invoke(client, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(inputType.getConstructor(String.class).newInstance("confirmed")));
    var invocation = ArgumentCaptor.forClass(BusinessActionInvocation.class);
    verify(runtime).invoke(invocation.capture());
    assertEquals("€pay", invocation.getValue().actionName());
    Files.createDirectories(Path.of("target/generated-test-contracts"));
    Files.writeString(Path.of("target/generated-test-contracts/UnicodeBusiness.ts"), types.typeScript());
  }

  @Test
  void exportClassNamesCannotShadowPackagesUsedByGeneratedSources() {
    var behavior = new BusinessModelBehavior("sales.Order", List.of(action()), List.of(), List.of());
    assertThrows(IllegalArgumentException.class, () -> BusinessBehaviorTypeGenerator.generate(behavior, "example", "java"));
    assertThrows(IllegalArgumentException.class, () -> BusinessBehaviorTypeGenerator.generate(behavior, "example", "com"));
  }

  @Test
  void exportNamesRejectCharactersIgnoredByJavaIdentifierResolution() {
    var behavior = new BusinessModelBehavior("sales.Order", List.of(action()), List.of(), List.of());
    assertThrows(IllegalArgumentException.class,
        () -> BusinessBehaviorTypeGenerator.generate(behavior, "example", "Client\u200B"));
    assertThrows(IllegalArgumentException.class,
        () -> BusinessBehaviorTypeGenerator.generate(behavior, "example", "co\u200Bm"));
    assertThrows(IllegalArgumentException.class,
        () -> BusinessBehaviorTypeGenerator.generate(behavior, "example\u200B.business", "Client"));
  }

  @Test
  void emptyJsonPropertyNamesPreserveDynamicValues() throws Exception {
    var action = action();
    action.setInputSchema(dataSchema(schema("", "string")));
    var type = compile(BusinessBehaviorTypeGenerator.generate(new BusinessModelBehavior("sales.Order",
        List.of(action), List.of(), List.of()), "example.empty", "Client").javaSources(), "example.empty.Client");
    var runtime = mock(BusinessActionRuntime.class);
    when(runtime.invoke(any())).thenReturn(Map.of("accepted", true));
    var client = type.getConstructor(BusinessActionRuntime.class).newInstance(runtime);
    type.getMethod("submit", com.ouroboros.businessmodel.action.BusinessActionInput.class).invoke(client, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("", "kept")));
    var invocation = ArgumentCaptor.forClass(BusinessActionInvocation.class);
    verify(runtime).invoke(invocation.capture());
    assertEquals(Map.of("", "kept"), invocation.getValue().input().data());
  }

  @Test
  void fingerprintIgnoresMapOrderAndBindingButDetectsContractChanges() {
    var original = action();
    var reordered = new LinkedHashMap<String, Object>();
    original.getInputSchema().entrySet().stream().sorted(Map.Entry.<String, Object>comparingByKey().reversed())
        .forEach(entry -> reordered.put(entry.getKey(), entry.getValue()));
    var changed = action();
    changed.setInputSchema(reordered);
    assertEquals(BusinessBehaviorContracts.actionFingerprint(original), BusinessBehaviorContracts.actionFingerprint(changed));
    changed.setOutputSchema(Map.of("type", "string"));
    assertNotEquals(BusinessBehaviorContracts.actionFingerprint(original), BusinessBehaviorContracts.actionFingerprint(changed));
  }

  @Test
  void sourcePackageAndClassNamesCannotInjectExecutableCode() {
    var behavior = new BusinessModelBehavior("sales.Order", List.of(), List.of(), List.of());
    assertThrows(IllegalArgumentException.class, () -> BusinessBehaviorTypeGenerator.generate(behavior, "x;evil", "Order"));
    assertThrows(IllegalArgumentException.class, () -> BusinessBehaviorTypeGenerator.generate(behavior, "example", "class"));
    assertThrows(IllegalArgumentException.class, () -> BusinessBehaviorTypeGenerator.generate(behavior, "example", "record"));
    assertThrows(IllegalArgumentException.class, () -> BusinessBehaviorTypeGenerator.generate(behavior, "example", "var"));
  }

  private Class<?> compile(Map<String, String> sources, String className) throws Exception {
    var arguments = new java.util.ArrayList<String>(List.of("-classpath", System.getProperty("java.class.path"),
        "-d", temporary.toString()));
    for (var entry : sources.entrySet()) {
      Path source = temporary.resolve(entry.getKey());
      Files.createDirectories(source.getParent());
      Files.writeString(source, entry.getValue());
      arguments.add(source.toString());
    }
    assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
    var loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, getClass().getClassLoader());
    loaders.add(loader);
    return loader.loadClass(className);
  }

  private static ActionDefinition action() {
    var action = new ActionDefinition();
    action.setName("submit");

    action.setInputSchema(dataSchema(schema("comment", "string")));
    action.setOutputSchema(schema("accepted", "boolean"));
    return action;
  }

  private static Map<String, Object> dataSchema(Map<String, Object> schema) {
    return Map.of("type", "object", "additionalProperties", false, "properties", Map.of("data", schema));
  }

  private static Map<String, Object> schema(String name, String type) {
    return Map.of("type", "object", "properties", Map.of(name, Map.of("type", type)),
        "required", List.of(name), "additionalProperties", false);
  }
}
