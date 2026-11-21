package com.ouroboros.rpc.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

class RpcConfigurationTemplatesTest {
  @Test void discoveryUsesTheSameLiteralConfigurationGrammarAsResolution() {
    String template = "#{first}/$literal/${notSpel[}/#{second}/#{first}";
    assertEquals(java.util.Set.of("first", "second"), RpcConfigurationTemplates.keys(template));
    assertEquals("one/$literal/${notSpel[}/two/one",
        RpcConfigurationTemplates.resolve(template, Map.of("first", "one", "second", "two")::get));
    assertTrue(RpcConfigurationTemplates.keys("$['data']['token']").isEmpty());
    assertThrows(IllegalArgumentException.class, () -> RpcConfigurationTemplates.keys("#{ }"));
    assertThrows(IllegalArgumentException.class, () -> RpcConfigurationTemplates.resolve("#{ }", key -> "value"));
  }
  @Test void substitutionValuesAreLiteralAndOriginalInvocationExpressionsKeepTheirTypes() {
    var config = Map.of("secret", "$password-${notAnExpression}-#{other}");
    assertEquals("$password-${notAnExpression}-#{other}", ExpressionUtils.eval("#{secret}", Map.of(), config::get).get());
    assertEquals("prefix $password-${notAnExpression}-#{other}/7", ExpressionUtils.eval("prefix #{secret}/${id}", Map.of("id", 7), config::get).get());
    assertEquals(7, ExpressionUtils.eval("${id}", Map.of("id", 7), config::get).get());
    assertEquals("alice", ExpressionUtils.eval("$customer.name", Map.of("customer", Map.of("name", "alice")), config::get).get());
    assertTrue(ExpressionUtils.eval("#{missing}", Map.of(), config::get).isLeft());
    assertTrue(ExpressionUtils.eval("${id + }", Map.of(), config::get).isLeft());
    assertEquals("right}brace", ExpressionUtils.eval("${'right}brace'}", Map.of(), config::get).get());
    assertEquals("ok", ExpressionUtils.eval("${{'answer':'ok'}['answer']}", Map.of(), config::get).get());
    assertTrue(ExpressionUtils.eval("${id", Map.of(), config::get).isLeft());
    assertTrue(ExpressionUtils.eval("#{}", Map.of(), config::get).isLeft());
  }

  @Test void rawRequestResolvesEnumsNamesAndRequiredBeforeConversionAndReadsCurrentConfiguration() {
    var service = mock(ConfigurationService.class);
    when(service.get(eq(String.class), anyString())).thenReturn(Optional.empty());
    var secret = new java.util.concurrent.atomic.AtomicReference<>("$safe-${literal}");
    when(service.get(String.class, "secret")).thenAnswer(invocation -> Optional.of(secret.get()));
    var env = new MockEnvironment().withProperty("method", "POST").withProperty("part", "header")
        .withProperty("header", "X-Key").withProperty("secret", "$safe-${literal}").withProperty("required", "true");
    var builder = new HttpRpcRequestBuilder(service, env);
    Map<String, Object> raw = Map.of("url", "http://localhost/${id}", "method", "#{method}", "parameters",
        List.of(Map.of("in", "#{part}", "name", "#{header}", "value", "#{secret}", "required", "#{required}")));
    var request = builder.buildConfiguredRequest(raw, Map.of("id", 42)).get();
    assertEquals("http://localhost/42", request.getUrl()); assertEquals("POST", request.getMethod());
    assertEquals("$safe-${literal}", request.getHeaders().get("X-Key"));
    env.setProperty("header", "X-Updated");
    assertTrue(builder.buildConfiguredRequest(raw, Map.of("id", 9)).get().getHeaders().containsKey("X-Updated"));
    secret.set("");
    assertTrue(builder.buildConfiguredRequest(raw, Map.of()).isFailure());
    env.setProperty("part", "unsupported");
    assertTrue(builder.buildConfiguredRequest(raw, Map.of()).isFailure());
  }

  @Test void credentialProjectionResolvesOnlyStructuralLeavesWithoutMutatingMetadata() {
    var source = new RPCCredentialMetadata(); source.setName("identity"); source.setType("HmacSignature");
    source.setExtraProp("hmacSignature", Map.of("preset", "#{preset}", "secretKey", "#{secret}",
        "signedHeaders", Map.of("include", List.of("#{header}"))));
    var projected = RpcConfigurationTemplates.credential(source, Map.of("preset", "aws_sigv4", "header", "X-Example")::get);
    assertEquals("aws_sigv4", projected.getExtraPropDeep("hmacSignature", "preset").orElseThrow());
    assertEquals("#{secret}", projected.getExtraPropDeep("hmacSignature", "secretKey").orElseThrow());
    assertEquals(List.of("X-Example"), projected.getExtraPropDeep("hmacSignature", "signedHeaders", "include").orElseThrow());
    assertEquals("#{preset}", source.getExtraPropDeep("hmacSignature", "preset").orElseThrow());
    assertThrows(IllegalArgumentException.class, () -> RpcConfigurationTemplates.credential(source, key -> null));
  }
}
