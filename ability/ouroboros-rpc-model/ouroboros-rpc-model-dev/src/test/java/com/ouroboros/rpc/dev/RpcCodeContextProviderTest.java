package com.ouroboros.rpc.dev;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.dev.metadata.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.rpc.metadata.RPCModelMetadata;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;
import com.ouroboros.script.intelligence.ScriptIntelligenceGlobal;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import com.ouroboros.util.Json;

class RpcCodeContextProviderTest {
  private final RpcCodeContextProvider provider = new RpcCodeContextProvider();
  private final ReadOnlyMetadataView empty = new ReadOnlyMetadataView("app", ApplicationMetadataSnapshot.unavailable(), new ScriptIntelligenceSnapshot());
  private CodeContextRequest request(String resource, String pointer, String draft) {
    return new CodeContextRequest("rpc-editor", 1, 1, "rpc.field", "rpc-template", new CodeContextRequest.Resource(resource, "test"),
        new CodeContextRequest.Location(null, pointer), Json.toBean(JsonNode.class, draft));
  }

  @Test void invocationScopeComesFromDraftSchemaAndNeverFromRequestConfiguration() {
    var description = provider.describe(request("rpc-model", "/request/url", """
        {"name":"test","request":{"url":"https://server","parameters":[]},"inputSchema":{"type":"object","properties":{
          "customer":{"type":"object","properties":{"id":{"type":"integer"}}}}}}
        """), empty);
    assertEquals(List.of("customer"), description.bindings().stream().map(ScriptIntelligenceGlobal::getName).toList());
    assertEquals("integer", description.bindings().getFirst().getShape().property("id").type());
    assertEquals("spel", provider.environmentLanguage(request("rpc-model", "/request/url", "{}")));
    var unknown = provider.describe(request("rpc-model", "/request/parameters/0/value", "{\"request\":{\"url\":\"a\"}}"), empty);
    assertTrue(unknown.bindings().isEmpty()); assertEquals("unknown-invocation-shape", unknown.diagnostics().getFirst().get("code"));
    assertEquals("config-only", provider.describe(request("rpc-model", "/request/method", "{}"), empty).projectionHints().get("templateMode"));
  }

  @Test void credentialPhasesExposeOnlyTheBindingsTheirExecutorsProvide() {
    var basic = provider.describe(request("rpc-auth", "/httpBasic/username", "{\"type\":\"HttpBasic\"}"), empty);
    assertTrue(basic.bindings().isEmpty()); assertTrue(basic.allowGlobals());
    var token = provider.describe(request("rpc-auth", "/accessToken/customPlacement/0/value", "{\"type\":\"AccessToken\"}"), empty);
    assertEquals(List.of("accessToken"), token.bindings().stream().map(ScriptIntelligenceGlobal::getName).toList());
    var fixed = provider.describe(request("rpc-auth", "/accessToken/staticAccessToken", "{\"type\":\"AccessToken\"}"), empty);
    assertFalse(fixed.allowGlobals()); assertEquals("config-only", fixed.projectionHints().get("templateMode"));
    assertThrows(BusinessException.class, () -> provider.describe(request("rpc-auth", "/accessToken/accessTokenPath", "{\"type\":\"AccessToken\"}"), empty));
    for (var preset : List.of("aliyun_acs3", "aliyun_rpc_v1", "aws_sigv4", "volcengine_hmac_sha256", "tencent_tc3", "huawei_sdk_hmac_sha256")) {
      var hmac = "{\"type\":\"HmacSignature\",\"hmacSignature\":{\"preset\":\"" + preset + "\"}}";
      assertTrue(provider.describe(request("rpc-auth", "/hmacSignature/accessKey", hmac), empty).allowGlobals());
    }
    assertTrue(provider.describe(request("rpc-auth", "/hmacSignature/region", "{\"type\":\"HmacSignature\",\"hmacSignature\":{\"preset\":\"#{preset}\"}}"), empty).allowGlobals());
  }

  @Test void configurationCatalogContainsOnlySafeMetadata() {
    var publicKey = metadata("rpc.url", Map.of("label", "URL", "valueType", "STRING", "defaultValue", "DO-NOT-RETURN"));
    var protectedKey = metadata("signing.key", Map.of("managementAccessPolicyRequired", true, "sensitiveSourceNames", List.of("SIGNING_KEY")));
    var source = metadata("SIGNING_KEY", Map.of("defaultValue", "SECRET"));
    var view = new ReadOnlyMetadataView("app", ApplicationMetadataSnapshot.current(Map.of("configuration", List.of(publicKey, protectedKey, source))), new ScriptIntelligenceSnapshot());
    var result = provider.describe(request("rpc-auth", "/httpBasic/password", "{\"type\":\"HttpBasic\"}"), view);
    assertEquals(List.of(Map.of("key", "rpc.url", "label", "URL", "valueType", "STRING")), result.projectionHints().get("configurationKeys"));
    assertFalse(Json.toJsonString(result).contains("DO-NOT-RETURN")); assertFalse(Json.toJsonString(result).contains("SECRET"));
  }

  @Test void invocationContractsSurviveMetadataRoundTripsAndRemainOptional() {
    var schema = new LinkedHashMap<String, Object>(Map.of("type", "object", "properties", Map.of("id", Map.of("type", "integer"))));
    var model = new RPCModelMetadata();
    model.setInputSchema(schema); model.setOutputSchema(schema);
    var credential = new RPCCredentialMetadata();
    credential.setInputSchema(schema); credential.setOutputSchema(schema);
    schema.put("type", "string");

    var restoredModel = Json.toBean(RPCModelMetadata.class, Json.toJsonString(model));
    var restoredCredential = Json.toBean(RPCCredentialMetadata.class, Json.toJsonString(credential));
    assertEquals("object", restoredModel.getInputSchema().get("type"));
    assertEquals(model.getInputSchema(), restoredModel.getInputSchema());
    assertEquals(model.getOutputSchema(), restoredModel.getOutputSchema());
    assertEquals(model.getInputSchema(), restoredCredential.getInputSchema());
    assertEquals(model.getOutputSchema(), restoredCredential.getOutputSchema());
    assertFalse(restoredModel.getExtraProps().containsKey("inputSchema"));
    assertFalse(restoredCredential.getExtraProps().containsKey("outputSchema"));
    assertTrue(Json.toBean(RPCModelMetadata.class, "{}").getInputSchema().isEmpty());
    assertTrue(Json.toBean(RPCCredentialMetadata.class, "{}").getOutputSchema().isEmpty());
    model.setInputSchema(null); model.setOutputSchema(null);
    credential.setInputSchema(null); credential.setOutputSchema(null);
    assertTrue(model.getInputSchema().isEmpty()); assertTrue(model.getOutputSchema().isEmpty());
    assertTrue(credential.getInputSchema().isEmpty()); assertTrue(credential.getOutputSchema().isEmpty());
  }
  @Test void knownCallSitesSupplyMissingContractsWithoutRewritingSharedCredentialMetadata() {
    var input = com.ouroboros.codeintelligence.type.TypeShape.object(Map.of("customer",
        com.ouroboros.codeintelligence.type.TypeShape.object(Map.of("name", com.ouroboros.codeintelligence.type.TypeShape.scalar("string")), "map")), "map");
    var actual = new RpcCodeContextProvider(List.of((type, name, metadata) -> java.util.Optional.of(input)));
    var model = metadata("orders", Map.of("authCredential", "shared"));
    var view = new ReadOnlyMetadataView("app", ApplicationMetadataSnapshot.current(Map.of("rpc-model", List.of(model))), new ScriptIntelligenceSnapshot());
    var modelResult = actual.describe(request("rpc-model", "/request/url", "{}"), view);
    assertEquals("string", modelResult.bindings().getFirst().getShape().property("name").type());
    var authResult = actual.describe(request("rpc-auth", "/plainCredentials/credentials/0/value", "{\"type\":\"PlainCredentials\"}"), view);
    assertTrue(authResult.bindings().isEmpty(), "An unrelated credential cannot inherit a model's call inputs");
    var authRequest = new CodeContextRequest("auth", 1, 1, "rpc.field", "rpc-template", new CodeContextRequest.Resource("rpc-auth", "shared"),
        new CodeContextRequest.Location(null, "/plainCredentials/credentials/0/value"), Json.toBean(JsonNode.class, "{\"type\":\"PlainCredentials\"}"));
    assertEquals("customer", actual.describe(authRequest, view).bindings().getFirst().getName());
    assertFalse(authRequest.draft().has("inputSchema"));
  }
  private Metadata metadata(String name, Map<String, Object> details) { var entry = new Metadata(); entry.setName(name); entry.setDetails(details); return entry; }
}
