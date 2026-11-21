package com.ouroboros.security.externalauth.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CompositeSecretResolverTest {

  @Test
  void conflictingSchemesFailBeforeReadingEitherSecretOrFallingBackToInline() {
    var first = mock(SecretResolver.class);
    var second = mock(SecretResolver.class);
    when(first.supports("managed")).thenReturn(true);
    when(second.supports("managed")).thenReturn(true);
    var resolver = new CompositeSecretResolver(List.of(first, second));
    assertThatThrownBy(() -> resolver.resolveWithFallback("managed:connection", "inline"))
        .hasMessageContaining("多个解析器").hasMessageNotContaining("connection").hasMessageNotContaining("inline");
    verify(first, never()).resolve(org.mockito.ArgumentMatchers.anyString());
    verify(second, never()).resolve(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void resolvesTextFieldFromJsonSecretWithoutChangingBaseReferenceBehavior() {
    String payload = """
        {"appId":"cli_test","appSecret":"client-secret","nested":{"value":"ignored"}}
        """;
    CompositeSecretResolver resolver = new CompositeSecretResolver(List.of(
        new FixedSecretResolver("test", payload)));

    assertThat(resolver.resolve("test:feishu-main")).contains(payload);
    assertThat(resolver.resolve("test:feishu-main#/appId")).contains("cli_test");
    assertThat(resolver.resolve("test:feishu-main#/appSecret")).contains("client-secret");
  }

  @Test
  void rejectsMissingNonTextAndMalformedJsonProjections() {
    CompositeSecretResolver objectResolver = new CompositeSecretResolver(List.of(
        new FixedSecretResolver("test", "{\"object\":{\"secret\":\"value\"}}")));
    CompositeSecretResolver malformedResolver = new CompositeSecretResolver(List.of(
        new FixedSecretResolver("test", "not-json")));

    assertThat(objectResolver.resolve("test:credential#/missing")).isEmpty();
    assertThat(objectResolver.resolve("test:credential#/object")).isEmpty();
    assertThat(malformedResolver.resolve("test:credential#/appSecret")).isEmpty();
  }

  @Test
  void resolvesSeveralProjectionsFromOneSecretRead() {
    AtomicInteger reads = new AtomicInteger();
    CompositeSecretResolver resolver = new CompositeSecretResolver(List.of(new SecretResolver() {
      @Override
      public boolean supports(String scheme) {
        return "test".equals(scheme);
      }

      @Override
      public Optional<String> resolve(String reference) {
        reads.incrementAndGet();
        return Optional.of("{\"appId\":\"cli_test\",\"appSecret\":\"client-secret\"}");
      }
    }));

    Optional<Map<String, String>> result = resolver.resolveProjections(Map.of(
        "clientId", "test:feishu-main#/appId",
        "clientSecret", "test:feishu-main#/appSecret"));

    assertThat(result).contains(Map.of("clientId", "cli_test", "clientSecret", "client-secret"));
    assertThat(reads).hasValue(1);
  }

  @Test
  void configuredReferenceTakesPrecedenceAndNeverFallsBackOnResolutionFailure() {
    CompositeSecretResolver resolver = new CompositeSecretResolver(List.of(
        new FixedSecretResolver("test", "{\"secret\":\"configured-secret\"}")));

    assertThat(resolver.resolveWithFallback("test:credential#/secret", "inline-secret"))
        .contains("configured-secret");
    assertThat(resolver.resolveWithFallback("test:credential#/missing", "inline-secret")).isEmpty();
    assertThat(resolver.resolveWithFallback("unknown:credential", "inline-secret")).isEmpty();
  }

  @Test
  void usesInlineSecretOnlyWhenNoReferenceIsConfigured() {
    CompositeSecretResolver resolver = new CompositeSecretResolver(List.of());

    assertThat(resolver.resolveWithFallback(null, "inline-secret")).contains("inline-secret");
    assertThat(resolver.resolveWithFallback("", "inline-secret")).contains("inline-secret");
    assertThat(resolver.resolveWithFallback(null, null)).isEmpty();
    assertThat(resolver.resolveWithFallback("", "")).isEmpty();
  }

  private record FixedSecretResolver(String scheme, String value) implements SecretResolver {
    @Override
    public boolean supports(String requestedScheme) {
      return scheme.equals(requestedScheme);
    }

    @Override
    public Optional<String> resolve(String reference) {
      return Optional.of(value);
    }
  }
}
