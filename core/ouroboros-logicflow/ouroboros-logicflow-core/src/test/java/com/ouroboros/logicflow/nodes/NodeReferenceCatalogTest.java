package com.ouroboros.logicflow.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class NodeReferenceCatalogTest {
  private static final NodeReferenceKind<String> MODEL_KIND =
      new NodeReferenceKind<>("test-model", String.class);

  @Test
  void completeReferenceSetDistinguishesFoundAndMissing() {
    var catalog = NodeReferenceCatalog.of(List.of(
        NodeReferenceSet.complete(MODEL_KIND, Map.of("model-a", "value-a"))
    ));

    assertEquals("found:value-a", describe(catalog.resolve(MODEL_KIND, "model-a")));
    assertEquals("missing", describe(catalog.resolve(MODEL_KIND, "model-b")));
  }

  @Test
  void indeterminateReferenceSetReturnsTrustedValuesWithoutClaimingMissing() {
    var catalog = NodeReferenceCatalog.of(List.of(NodeReferenceSet.indeterminate(
        MODEL_KIND,
        Map.of("workspace-model", "workspace-value"),
        NodeReferenceIndeterminateReason.INCOMPLETE
    )));

    assertEquals("found:workspace-value", describe(catalog.resolve(MODEL_KIND, "workspace-model")));
    assertEquals("indeterminate:INCOMPLETE", describe(catalog.resolve(MODEL_KIND, "unknown-model")));
    assertThrows(IllegalStateException.class, () -> NodeReferenceCatalog.empty().resolve(MODEL_KIND, "model"));
  }

  @Test
  void overlaysPreserveReferenceSetCompleteness() {
    var set = NodeReferenceSet.indeterminate(
        MODEL_KIND,
        Map.of("baseline", "baseline-value"),
        NodeReferenceIndeterminateReason.INCOMPLETE
    ).overlay(Map.of("workspace", "workspace-value"), List.of("baseline"));
    var catalog = NodeReferenceCatalog.of(List.of(set));

    assertEquals("found:workspace-value", describe(catalog.resolve(MODEL_KIND, "workspace")));
    assertEquals("indeterminate:INCOMPLETE", describe(catalog.resolve(MODEL_KIND, "baseline")));
  }

  @Test
  void referenceSetCanPreserveCaseInsensitiveModelNames() {
    var catalog = NodeReferenceCatalog.of(List.of(NodeReferenceSet.complete(
        MODEL_KIND,
        Map.of("Customer", "customer-value"),
        String::compareToIgnoreCase
    )));

    assertEquals("found:customer-value", describe(catalog.resolve(MODEL_KIND, "customer")));
  }

  @Test
  void duplicateCaseInsensitiveNamesFailButExplicitOverlayUsesItsCanonicalName() {
    assertThrows(IllegalArgumentException.class, () -> NodeReferenceSet.complete(
        MODEL_KIND,
        Map.of("Customer", "first", "customer", "second"),
        String::compareToIgnoreCase
    ));

    var overlaid = NodeReferenceSet.complete(
        MODEL_KIND,
        Map.of("Customer", "baseline"),
        String::compareToIgnoreCase
    ).overlay(Map.of("customer", "workspace"), List.of());
    var catalog = NodeReferenceCatalog.of(List.of(overlaid));
    assertEquals("found:workspace", describe(catalog.resolve(MODEL_KIND, "CUSTOMER")));
  }

  @Test
  void duplicateOrUnregisteredKindsFailFast() {
    var set = NodeReferenceSet.complete(MODEL_KIND, Map.of());

    assertThrows(IllegalArgumentException.class, () -> NodeReferenceCatalog.of(List.of(set, set)));
    assertThrows(IllegalStateException.class, () -> NodeReferenceCatalog.empty().resolve(MODEL_KIND, "model"));
  }

  @Test
  void lazyReferenceSetDefersValueLookupUntilResolution() {
    var values = new HashMap<String, String>();
    var catalog = NodeReferenceCatalog.of(List.of(
        NodeReferenceSet.lazyComplete(MODEL_KIND, values::get)
    ));

    values.put("model-a", "value-a");

    assertEquals("found:value-a", describe(catalog.resolve(MODEL_KIND, "model-a")));
  }

  @Test
  void lazyReferenceSetCanReportAnIndeterminateLookupWithoutPrefetchingValues() {
    var catalog = NodeReferenceCatalog.of(List.of(NodeReferenceSet.lazy(
        MODEL_KIND,
        name -> "model-a".equals(name)
            ? NodeReferenceResolution.found("value-a")
            : NodeReferenceResolution.indeterminate(NodeReferenceIndeterminateReason.INVALID),
        true,
        null
    )));

    assertEquals("found:value-a", describe(catalog.resolve(MODEL_KIND, "model-a")));
    assertEquals("indeterminate:INVALID", describe(catalog.resolve(MODEL_KIND, "broken-model")));
  }

  private String describe(NodeReferenceResolution<String> resolution) {
    return resolution.fold(
        value -> "found:" + value,
        () -> "missing",
        reason -> "indeterminate:" + reason
    );
  }
}
