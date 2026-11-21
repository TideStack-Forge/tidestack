package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRemoteFetchPolicy;

class ProjectTemplateRemoteFetchSecurityTest {

  @Test
  void shouldRejectUnsafeUriShapesBeforeFetch() throws Exception {
    ProjectTemplateRemoteFetchPolicy policy = ProjectTemplateRemoteFetchPolicy.exactOrigin(
        "https://templates.example.com",
        List.of()
    );

    assertThrows(IllegalArgumentException.class, () -> policy.validate(URI.create("http://templates.example.com/catalog.json"), List.of()));
    assertThrows(IllegalArgumentException.class, () -> policy.validate(URI.create("https://user@templates.example.com/catalog.json"), List.of()));
    assertThrows(IllegalArgumentException.class, () -> policy.validate(URI.create("https://templates.example.com/catalog.json#frag"), List.of()));
    assertThrows(IllegalArgumentException.class, () -> policy.validate(URI.create("https://93.184.216.34/catalog.json"), List.of(
        InetAddress.getByName("93.184.216.34")
    )));
    assertThrows(IllegalArgumentException.class, () -> policy.validate(URI.create("https://evil.example.com/catalog.json"), List.of(
        InetAddress.getByName("93.184.216.34")
    )));
  }

  @Test
  void shouldRejectPrivateResolvedAddressUnlessExplicitCidrAllowsIt() throws Exception {
    ProjectTemplateRemoteFetchPolicy publicOnly = ProjectTemplateRemoteFetchPolicy.exactOrigin(
        "https://templates.example.com",
        List.of()
    );
    ProjectTemplateRemoteFetchPolicy explicitlyAllowedInternal = ProjectTemplateRemoteFetchPolicy.exactOrigin(
        "https://templates.example.com",
        List.of("10.20.0.0/16")
    );

    assertThrows(IllegalArgumentException.class, () -> publicOnly.validate(URI.create("https://templates.example.com/catalog.json"), List.of(
        InetAddress.getByName("10.20.30.40")
    )));
    explicitlyAllowedInternal.validate(URI.create("https://templates.example.com/catalog.json"), List.of(
        InetAddress.getByName("10.20.30.40")
    ));
  }
}
