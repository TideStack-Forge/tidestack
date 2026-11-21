package com.ouroboros.security.externalauth.secret;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.exception.InternalException;

/**
 * Delegates secret resolution to scheme-specific resolvers.
 * Also provides fallback logic for inline secrets.
 */
public class CompositeSecretResolver {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<SecretResolver> resolvers;

  public CompositeSecretResolver(List<SecretResolver> resolvers) {
    this.resolvers = List.copyOf(resolvers);
  }

  /**
   * Resolve a secret reference by parsing its scheme and delegating.
   */
  public Optional<String> resolve(String reference) {
    Reference parsed = parse(reference);
    if (parsed == null) {
      return Optional.empty();
    }
    Optional<String> resolved = resolveBase(parsed);
    return parsed.fragment() == null
        ? resolved
        : resolved.flatMap(value -> project(value, parsed.fragment()));
  }

  /** Resolves several textual JSON fields from one immutable read of the same base secret. */
  public Optional<Map<String, String>> resolveProjections(Map<String, String> references) {
    if (references == null || references.isEmpty()) {
      return Optional.empty();
    }
    Map<String, Reference> parsed = new LinkedHashMap<>();
    String baseReference = null;
    for (Map.Entry<String, String> entry : references.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) {
        return Optional.empty();
      }
      Reference reference = parse(entry.getValue());
      if (reference == null || reference.fragment() == null) {
        return Optional.empty();
      }
      if (baseReference == null) {
        baseReference = reference.baseReference();
      } else if (!baseReference.equals(reference.baseReference())) {
        return Optional.empty();
      }
      parsed.put(entry.getKey(), reference);
    }
    Reference first = parsed.values().iterator().next();
    Optional<String> resolved = resolveBase(first);
    if (resolved.isEmpty()) {
      return Optional.empty();
    }
    Map<String, String> projections = new LinkedHashMap<>();
    for (Map.Entry<String, Reference> entry : parsed.entrySet()) {
      Optional<String> value = project(resolved.get(), entry.getValue().fragment());
      if (value.isEmpty()) {
        return Optional.empty();
      }
      projections.put(entry.getKey(), value.get());
    }
    return Optional.of(Map.copyOf(projections));
  }

  private Optional<String> resolveBase(Reference reference) {
    SecretResolver selected = null;
    for (SecretResolver resolver : resolvers) {
      if (resolver.supports(reference.scheme())) {
        if (selected != null) {
          throw new InternalException("密钥引用协议存在多个解析器: " + reference.scheme());
        }
        selected = resolver;
      }
    }
    return selected == null ? Optional.empty() : selected.resolve(reference.baseReference());
  }

  private Reference parse(String reference) {
    if (reference == null || reference.isEmpty()) {
      return null;
    }
    int fragmentIndex = reference.indexOf('#');
    String baseReference = fragmentIndex < 0 ? reference : reference.substring(0, fragmentIndex);
    String fragment = fragmentIndex < 0 ? null : reference.substring(fragmentIndex + 1);
    if (baseReference.isEmpty() || (fragmentIndex >= 0 && fragment.isEmpty())) {
      return null;
    }
    int colonIndex = baseReference.indexOf(':');
    if (colonIndex <= 0) {
      return null;
    }
    return new Reference(baseReference.substring(0, colonIndex), baseReference, fragment);
  }

  private Optional<String> project(String json, String fragment) {
    if (!fragment.startsWith("/")) {
      return Optional.empty();
    }
    try {
      JsonNode value = OBJECT_MAPPER.readTree(json).at(fragment);
      return value.isTextual() && !value.asText().isBlank()
          ? Optional.of(value.asText())
          : Optional.empty();
    } catch (JsonProcessingException | IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  /**
   * Try to resolve ref first, fall back to inline value.
   */
  public Optional<String> resolveWithFallback(String ref, String inline) {
    if (ref != null && !ref.isEmpty()) {
      return resolve(ref);
    }
    if (inline != null && !inline.isEmpty()) {
      return Optional.of(inline);
    }
    return Optional.empty();
  }

  private record Reference(String scheme, String baseReference, String fragment) {
  }
}
