package com.ouroboros.businessmodel.behavior;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.businessmodel.event.BusinessEventAuthorities;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class BusinessBehaviorCatalogControllerTest {
  @AfterEach
  void restorePublicationBoundary() { OuroborosContextRefreshLifecycle.publish(this, event -> { }); }

  @Test
  void actionAndPayloadPermissionsDoNotDiscloseTheModelingContractOrEvenItsExistence() {
    var provider = mock(BusinessModelBehaviorProvider.class);
    var controller = new BusinessBehaviorCatalogController(provider);
    withPermissions(List.of("sales.Order.approve", BusinessEventAuthorities.read(new BusinessEventIdentity("sales.Order", "approved"))),
        () -> assertThrows(BusinessException.class, () -> controller.inspect("sales.Order")));
    verifyNoInteractions(provider);
  }

  @Test
  void publishedViewUsesTheStableSnapshotAndFiltersConsumptionAndExternalDefinitionsByModelManagementAccess() throws Exception {
    var provider = fixture();
    var controller = new BusinessBehaviorCatalogController(provider);
    withPermissions(List.of(BusinessBehaviorAuthorities.inspect("sales.Order"), BusinessBehaviorAuthorities.inspect("billing.Invoice")), () -> {
      var result = controller.inspect("sales.Order").getData();
      assertEquals("published", result.view());
      assertEquals("published-revision", result.catalog().revision());
      assertEquals("authorized-models-in-current-application", result.consumerCoverage());
      assertEquals(1, result.consumers().size());
      assertEquals("billing.Invoice", result.consumers().getFirst().modelFullName());
      assertEquals("${ false }", result.consumers().getFirst().subscription().getFilter());
      assertEquals(1, result.externalEvents().size());
      assertEquals("forbidden", result.externalEvents().getFirst().status());
      assertNull(result.externalEvents().getFirst().definition());
      assertTrue(result.externalEvents().getFirst().sources().isEmpty());
      return null;
    });
    verify(provider, never()).getBusinessModelBehavior("shipping.Dispatch");
    var mvc = MockMvcBuilders.standaloneSetup(controller).build();
    withPermissions(List.of(BusinessBehaviorAuthorities.inspect("sales.Order")), () -> {
      try {
        var response = mvc.perform(get("/api/business-model-behaviors/sales.Order/catalog")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        var envelope = Json.toMap(response.getContentAsString());
        assertEquals(0, envelope.get("code"));
        assertEquals("published", ((Map<?, ?>) envelope.get("data")).get("view"));
      } catch (Exception error) { throw new AssertionError(error); }
      return null;
    });
  }

  @Test
  void separatelyAuthorizedExternalEventRemainsExternalAndMissingContractIsReportedExplicitly() {
    var provider = fixture();
    var controller = new BusinessBehaviorCatalogController(provider);
    var permissions = List.of(BusinessBehaviorAuthorities.inspect("sales.Order"), BusinessBehaviorAuthorities.inspect("shipping.Dispatch"));
    withPermissions(permissions, () -> {
      var response = controller.inspect("sales.Order").getData();
      assertEquals(List.of("approved"), response.catalog().events().stream().map(EventDefinition::getName).toList());
      var external = response.externalEvents().getFirst();
      assertEquals("available", external.status());
      assertEquals("sent", external.definition().getName());
      when(provider.getBusinessModelBehavior("shipping.Dispatch")).thenReturn(Optional.empty());
      assertEquals("unavailable", controller.inspect("sales.Order").getData().externalEvents().getFirst().status());
      return null;
    });
  }

  @Test
  void failedPublicationCannotBeReadAsACurrentContractAndSuccessfulRefreshRestoresIt() {
    var provider = fixture();
    var controller = new BusinessBehaviorCatalogController(provider);
    assertThrows(IllegalStateException.class, () -> OuroborosContextRefreshLifecycle.publish(this, event -> {
      throw new IllegalStateException("injected refresh failure");
    }));
    withPermissions(List.of(BusinessBehaviorAuthorities.inspect("sales.Order")), () -> {
      assertThrows(BusinessException.class, () -> controller.inspect("sales.Order"));
      verify(provider, never()).getBusinessModelBehavior(anyString());
      OuroborosContextRefreshLifecycle.publish(this, event -> { });
      assertEquals("published-revision", controller.inspect("sales.Order").getData().catalog().revision());
      when(provider.getBusinessModelBehavior("sales.Order")).thenReturn(Optional.empty());
      assertThrows(BusinessException.class, () -> controller.inspect("sales.Order"));
      return null;
    });
  }

  private static BusinessModelBehaviorProvider fixture() {
    var event = new EventDefinition(); event.setName("approved");
    var external = new EventDefinition(); external.setName("sent");
    var action = new ActionDefinition(); action.setName("approve");
    var reference = new EventReference(); reference.setModelFullName("shipping.Dispatch"); reference.setEventName("sent");
    action.setEmits(List.of(reference, reference));
    var producer = new BusinessModelBehavior("sales.Order", List.of(action), List.of(event), List.of(), List.of(), "published-revision");
    var billing = new BusinessModelBehavior("billing.Invoice", List.of(), List.of(), List.of(subscription("createInvoice", false)));
    var hidden = new BusinessModelBehavior("private.Audit", List.of(), List.of(), List.of(subscription("auditOrder", true)));
    var shipping = new BusinessModelBehavior("shipping.Dispatch", List.of(), List.of(external), List.of());
    var provider = mock(BusinessModelBehaviorProvider.class);
    when(provider.getBusinessModelBehavior("sales.Order")).thenReturn(Optional.of(producer));
    when(provider.getBusinessModelBehavior("shipping.Dispatch")).thenReturn(Optional.of(shipping));
    when(provider.getBusinessModelBehaviorMap()).thenReturn(Map.of("sales.Order", producer, "billing.Invoice", billing,
        "private.Audit", hidden, "shipping.Dispatch", shipping));
    return provider;
  }

  private static EventSubscriptionDefinition subscription(String name, boolean enabled) {
    var result = new EventSubscriptionDefinition(); result.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL); result.setName(name); result.setFilter(enabled ? "${ true }" : "${ false }");
    result.getSourceEvent().setModelFullName("sales.Order"); result.getSourceEvent().setEventName("approved");
    return result;
  }

  private static <T> T withPermissions(List<String> names, Supplier<T> action) {
    var grants = names.stream().map(name -> {
      var authority = mock(OuroborosGrantedAuthority.class); when(authority.getAuthority()).thenReturn(name); return authority;
    }).toList();
    var authentication = mock(OuroborosAuthentication.class);
    when(authentication.isAuthenticated()).thenReturn(true); doReturn(grants).when(authentication).getAuthorities();
    return CurrentSecurityContextHolder.withContext(new CurrentSecurityContext() {
      public Optional<SecurityIdentity> findIdentity() { return Optional.empty(); }
      public Optional<OuroborosAuthentication> findAuthentication() { return Optional.of(authentication); }
    }, action);
  }
}
