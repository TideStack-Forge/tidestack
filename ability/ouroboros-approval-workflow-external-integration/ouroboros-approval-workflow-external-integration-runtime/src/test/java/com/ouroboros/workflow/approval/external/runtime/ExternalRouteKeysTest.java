package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExternalRouteKeysTest {

  @Test
  void routeKeysAreFixedLengthAndNamespaceAware() {
    String active = ExternalRouteKeys.of("mapping-active", "scope", "flow");
    String inactive = ExternalRouteKeys.of("mapping-inactive", "scope", "flow");

    assertThat(active).startsWith("v1:").hasSize(67);
    assertThat(inactive).startsWith("v1:").hasSize(67);
    assertThat(active).isNotEqualTo(inactive);
  }
}
