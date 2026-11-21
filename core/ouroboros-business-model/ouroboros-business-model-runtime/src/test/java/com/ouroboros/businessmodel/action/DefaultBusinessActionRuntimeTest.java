package com.ouroboros.businessmodel.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.vavr.control.Try;

import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.BusinessActionAnnotationCatalog;
import com.ouroboros.businessmodel.annotation.BusinessAction;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.logicflow.LogicFlowExecutionFacade;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowModelCenter;
import com.ouroboros.logicflow.model.FlowModelProvider;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowConnection;
import com.ouroboros.logicflow.model.DataTypeEnum;
import com.ouroboros.logicflow.exception.DesignException;
import com.ouroboros.data.record.Record;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.BaseAuthority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

class DefaultBusinessActionRuntimeTest {
  private static final String MODEL = "sales.Order";
  private static final String ACTION = "submit";
  private static final String AUTHORITY = MODEL + "." + ACTION;

  @Test
  void binaryResultRemainsNativeAndOwnedByTheCallerAfterSuccessfulSynchronousAction(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    var path = java.nio.file.Files.writeString(directory.resolve("result.tmp"), "ready");
    var result = com.ouroboros.util.FileResult.temporary(path, "result.txt", "text/plain");
    var action = handlerAction(); action.setReadOnly(true);
    action.setOutputSchema(Map.of("type", "string", "format", "binary", "contentMediaType", "text/plain"));
    var handler = new RecordingHandler("submit-handler", result);
    var runtime = runtime(action, new SynchronizationTransactionManager(), handler);
    assertSame(result, withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "download")))));
    assertTrue(java.nio.file.Files.exists(path)); assertEquals(1, handler.calls);
    try (result; var input = result.openStream()) { assertEquals("ready", new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)); }
    assertFalse(java.nio.file.Files.exists(path));
  }

  @Test
  void binaryResourcesAreReleasedOnOutputMismatchOrAuditRollback(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    for (String failure : List.of("json-contract", "media-type", "audit")) {
      var path = java.nio.file.Files.writeString(directory.resolve(failure + ".tmp"), "ready");
      var result = com.ouroboros.util.FileResult.temporary(path, "result.txt", "text/plain");
      var action = handlerAction();
      if (!"json-contract".equals(failure)) {
        action.setOutputSchema(Map.of("type", "string", "format", "binary", "contentMediaType", "media-type".equals(failure) ? "application/pdf" : "text/plain"));
      }
      var runtime = runtime(action, new SynchronizationTransactionManager(), new RecordingHandler("submit-handler", result), audit -> {
        if ("audit".equals(failure) && "COMMITTED".equals(audit.transactionResult())) { throw new BusinessException("audit failed"); }
      });
      assertThrows(BusinessException.class, () -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "download")))));
      assertFalse(java.nio.file.Files.exists(path), failure);
    }
  }

  @Test
  void targetlessAliasKeepsTheSourceReadScopeInsteadOfReplacingItWithTheActionGrant() {
    String permission = MODEL + ".existingPermission";
    var action = handlerAction(); action.setReadOnly(true);
    var behavior = new BusinessModelBehavior(MODEL, List.of(action), List.of(), List.of(), List.of(), "alias",
        Map.of(), Map.of(), Map.of(), Map.of(ACTION, permission));
    var readParams = org.mockito.Mockito.mock(AccessControlParams.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    org.mockito.Mockito.when(readParams.getFilters()).thenReturn(Map.of("company", "C1"));
    org.mockito.Mockito.when(readParams.getDisallowedFields()).thenReturn(List.of("cost"));
    var authentication = org.mockito.Mockito.mock(OuroborosAuthentication.class);
    org.mockito.Mockito.when(authentication.isAuthenticated()).thenReturn(true);
    org.mockito.Mockito.doReturn(List.of(grant(permission))).when(authentication).getAuthorities();
    org.mockito.Mockito.when(authentication.getAccessControlParams(MODEL + ".search")).thenReturn(readParams);
    var runtime = new DefaultBusinessActionRuntime(provider(behavior), (flow, inputs) -> Try.success(null), List.of(new BusinessActionHandler() {
      public String getName() { return "submit-handler"; }
      public Object handle(BusinessActionContext context, BusinessActionInput<?, ?> input) {
        assertEquals(permission, context.authorityName());
        var scoped = CurrentSecurityContextHolder.findAuthentication().orElseThrow();
        assertTrue(scoped.findScopedAccessControlParams(MODEL + ".search").isEmpty());
        assertSame(readParams, scoped.getAccessControlParams(MODEL + ".search"));
        assertEquals(Map.of("company", "C1"), scoped.getAccessControlParams(MODEL + ".search").getFilters());
        assertEquals(List.of("cost"), scoped.getAccessControlParams(MODEL + ".search").getDisallowedFields());
        return Map.of("accepted", true);
      }
    }), new RecordingTransactionManager(), ignored -> { });
    var security = new CurrentSecurityContext() {
      public Optional<SecurityIdentity> findIdentity() { return Optional.empty(); }
      public Optional<OuroborosAuthentication> findAuthentication() { return Optional.of(authentication); }
    };
    assertEquals(Map.of("accepted", true), CurrentSecurityContextHolder.withContext(security,
        () -> runtime.invoke(invocation(Map.of("comment", "read")))));
  }

  @Test
  void mappedWorkflowPermissionAuthorizesItsOwnObjectAndScopesItsDataParamsWithoutGrantingSearch() {
    String permission = MODEL + ".workflow";
    var behavior = new BusinessModelBehavior(MODEL, List.of(instanceAction()), List.of(), List.of(), List.of(), "workflow",
        Map.of(), Map.of(), Map.of(), Map.of(ACTION, permission));
    var runtime = new DefaultBusinessActionRuntime(provider(behavior), (flow, inputs) -> Try.success(null), List.of(new BusinessActionHandler() {
      public String getName() { return "submit-handler"; }
      public Object handle(BusinessActionContext context, BusinessActionInput<?, ?> input) {
        assertEquals("ORD-1", context.objectStates().get("/params/record").get("id"));
        assertFalse(com.ouroboros.businessmodel.security.BusinessBehaviorSecurity.hasAuthority(MODEL + ".search"));
        var authentication = CurrentSecurityContextHolder.findAuthentication().orElseThrow();
        assertTrue(authentication.findScopedAccessControlParams(MODEL + ".search").isPresent());
        assertTrue(authentication.findScopedAccessControlParams("sales.Other.search").isEmpty());
        return Map.of("accepted", true);
      }
    }), new RecordingTransactionManager(), ignored -> { });
    try (var models = DataModelSnapshot.publish(MODEL)) {
      assertEquals(BusinessActionAvailability.Status.EXECUTABLE,
          withAuthority(permission, () -> runtime.checkAvailability(instanceInvocation())).status());
      assertEquals(Map.of("accepted", true), withAuthority(permission, () -> runtime.invoke(instanceInvocation())));
      assertThrows(BusinessException.class, () -> withAuthority(MODEL + ".search", () -> runtime.invoke(instanceInvocation())));
    }
  }

  @Test
  void integrationValidationRunsWithTrustedContextBeforeImplementationAndRollsBackOnFailure() {
    var transactions = new RecordingTransactionManager();
    var handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    var runtime = runtime(handlerAction(), transactions, handler);
    var validated = new java.util.concurrent.atomic.AtomicInteger();
    runtime.setInvocationValidators(List.of((context, input) -> {
      assertEquals(MODEL, context.modelFullName());
      assertEquals(AUTHORITY, context.authorityName());
      assertEquals(Map.of("comment", "validated"), input.data());
      validated.incrementAndGet();
      throw new BusinessException("foreign task binding");
    }));
    assertThrows(BusinessException.class, () -> withAuthority(AUTHORITY,
        () -> runtime.invoke(invocation(Map.of("comment", "validated")))));
    assertEquals(1, validated.get());
    assertEquals(0, handler.calls);
    assertEquals(1, transactions.rollbacks);
  }

  @Test
  void publishedExecutionAuthorityControlsDescriptionAvailabilityInvocationContextAndAudit() {
    String authority = MODEL + ".workflow";
    var behavior = new BusinessModelBehavior(MODEL, List.of(handlerAction()), List.of(), List.of(), List.of(), "mapped",
        Map.of(), Map.of(), Map.of(), Map.of(ACTION, authority));
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var audits = new ArrayList<BusinessActionExecutionAuditRecord>();
    var runtime = new DefaultBusinessActionRuntime(provider(behavior), (flow, inputs) -> Try.success(null),
        List.of(new BusinessActionHandler() {
          public String getName() { return "submit-handler"; }
          public Object handle(BusinessActionContext context, BusinessActionInput<?, ?> input) {
            calls.incrementAndGet();
            assertEquals(authority, context.authorityName());
            return Map.of("accepted", true);
          }
        }), new RecordingTransactionManager(), audits::add);
    var invocation = invocation(Map.of("comment", "mapped permission"));
    assertTrue(withAuthority(AUTHORITY, () -> runtime.describe(MODEL)).isEmpty());
    assertEquals(BusinessActionAvailability.Status.BLOCKED,
        withAuthority(AUTHORITY, () -> runtime.checkAvailability(invocation)).status());
    assertThrows(BusinessException.class, () -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation)));
    assertEquals(0, calls.get());
    assertEquals(1, withAuthority(authority, () -> runtime.describe(MODEL)).size());
    assertEquals(BusinessActionAvailability.Status.EXECUTABLE,
        withAuthority(authority, () -> runtime.checkAvailability(invocation)).status());
    assertEquals(Map.of("accepted", true), withAuthority(authority, () -> runtime.invoke(invocation)));
    assertEquals(1, calls.get());
    assertEquals(authority, audits.getLast().executionAuthorityName());
  }

  @AfterEach
  void clearAnnotationFixture() {
    try (var empty = new AnnotationConfigApplicationContext()) {
      empty.refresh();
      registerBusinessActionAnnotationCatalog(empty).afterSingletonsInstantiated();
    }
  }

  @Test
  void inputOnlyPreconditionSharesAvailabilityAndExecutionWithoutLocksOrAvailabilityEffects() {
    var action = handlerAction();
    action.setPrecondition(Map.of("expression", "${input.data.comment == 'confirmed'}", "message", "请先确认"));
    var transactions = new RecordingTransactionManager();
    var handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    var runtime = runtime(action, transactions, handler);
    var blocked = withAuthority(AUTHORITY, () -> runtime.checkAvailability(invocation(Map.of("comment", "draft"))));
    assertEquals(BusinessActionAvailability.Blockage.PRECONDITION, blocked.blockage());
    assertEquals(List.of("请先确认"), blocked.reasons());
    assertEquals(0, transactions.starts);
    assertEquals(0, handler.calls);
    assertThrows(BusinessActionPreconditionException.class, () -> withAuthority(AUTHORITY,
        () -> runtime.invoke(invocation(Map.of("comment", "draft")))));
    assertEquals(0, handler.calls);
    assertEquals(BusinessActionAvailability.Status.EXECUTABLE,
        withAuthority(AUTHORITY, () -> runtime.checkAvailability(invocation(Map.of("comment", "confirmed")))).status());
    assertEquals(Map.of("accepted", true), withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "confirmed")))));
    assertEquals(1, handler.calls);
  }

  @Test
  void generatedContractMismatchIsRejectedBeforeEffectsAndAvailabilityReportsIt() {
    var transactions = new RecordingTransactionManager();
    var handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    var runtime = runtime(handlerAction(), transactions, handler);
    var stale = new BusinessActionInvocation(MODEL, ACTION, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("comment", "ok")), "stale-schema");
    var failure = assertThrows(BusinessException.class, () -> withAuthority(AUTHORITY, () -> runtime.invoke(stale)));
    assertTrue(failure.getMessage().contains("SDK"));
    var availability = withAuthority(AUTHORITY, () -> runtime.checkAvailability(stale));
    assertEquals(BusinessActionAvailability.Blockage.CONTRACT, availability.blockage());
    assertEquals(0, handler.calls);
    assertEquals(0, transactions.starts);
  }

  @Test
  void generatedCallUsesTheDynamicContractAndSurvivesAnImplementationChange() {
    var action = handlerAction();
    var token = com.ouroboros.businessmodel.behavior.BusinessBehaviorContracts.actionFingerprint(action);
    action.getBinding().setHandlerName("replacement-handler");
    var handler = new RecordingHandler("replacement-handler", Map.of("accepted", true));
    var runtime = runtime(action, new RecordingTransactionManager(), handler);
    var invocation = new BusinessActionInvocation(MODEL, ACTION, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("comment", "ok")), token);
    assertEquals(Map.of("accepted", true), withAuthority(AUTHORITY, () -> runtime.invoke(invocation)));
    assertEquals(1, handler.calls);
    assertEquals(BusinessActionAvailability.Status.EXECUTABLE,
        withAuthority(AUTHORITY, () -> runtime.checkAvailability(invocation)).status());
  }

  @Test
  void implementationDiagnosticsUseTheSelectedBindingWithoutExecutingIt() {
    var handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    var runtime = runtime(handlerAction(), new RecordingTransactionManager(), handler);
    var implementation = runtime.inspectImplementation(MODEL, ACTION).orElseThrow();
    assertTrue(implementation.implementationRef().startsWith("handler:submit-handler:"));
    assertTrue(runtime.inspectImplementation(MODEL, "missing").isEmpty());
    assertEquals(0, handler.calls);
  }

  @Test
  void theOfficialContextEventValidatesRuntimeBindingsBeforeBusinessCallsResume() {
    var transactions = new RecordingTransactionManager();
    var runtime = new DefaultBusinessActionRuntime(provider(handlerAction()), (flow, inputs) -> Try.success(null),
        List.of(), transactions, ignored -> { });
    try (var context = new AnnotationConfigApplicationContext()) {
      context.registerBean(BusinessActionRuntime.class, () -> runtime);
      var ready = new ReadyListener();
      context.registerBean(ReadyListener.class, () -> ready);
      context.refresh();
      try {
        var error = assertThrows(BusinessException.class,
            () -> com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle.publish(this, context::publishEvent));
        assertTrue(error.getMessage().contains(MODEL + ".actions.submit.binding-unavailable"));
        assertEquals(0, ready.calls);
        var availability = withAuthority(AUTHORITY, () -> runtime.checkAvailability(invocation(Map.of("comment", "ok"))));
        assertEquals(BusinessActionAvailability.Status.BLOCKED, availability.status());
        assertEquals(BusinessActionAvailability.Blockage.CONTRACT, availability.blockage());
        assertEquals(0, transactions.starts);
      } finally {
        com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle.publish(this, ignored -> { });
      }
    }
  }

  static class ReadyListener {
    private int calls;

    @org.springframework.context.event.EventListener
    @org.springframework.core.annotation.Order(0)
    public void onReady(com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent event) {
      calls++;
    }
  }

  @Test
  void relationSchemaParametersEnterTheHandlerAsAuthorizedServerState() {
    var owner = org.mockito.Mockito.mock(com.ouroboros.businessmodel.DefaultBusinessModel.class);
    var relation = org.mockito.Mockito.mock(com.ouroboros.businessmodel.BusinessModelRelation.class);
    org.mockito.Mockito.when(owner.getRelation("previousOrder")).thenReturn(Optional.of(relation));
    org.mockito.Mockito.when(owner.getFullName()).thenReturn(MODEL);
    org.mockito.Mockito.when(owner.isEnabled()).thenReturn(true);
    org.mockito.Mockito.when(relation.getType()).thenReturn("reference");
    org.mockito.Mockito.when(relation.getRelatedModel()).thenReturn(owner);
    var action = handlerAction();
    action.setInputSchema(dataSchema(Map.of("type", "object", "properties", Map.of("previousOrder", Map.of(
        "x-business-relation", Map.of("modelFullName", MODEL, "relationName", "previousOrder"))))));
    var handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    var runtime = runtime(action, new RecordingTransactionManager(), handler);
    try (var center = org.mockito.Mockito.mockStatic(com.ouroboros.businessmodel.BusinessModelCenter.class);
         var data = DataModelSnapshot.publish(MODEL)) {
      center.when(() -> com.ouroboros.businessmodel.BusinessModelCenter.getBusinessModel(MODEL)).thenReturn(Optional.of(owner));
      withAuthorities(List.of(AUTHORITY, MODEL + ".search"),
          () -> runtime.invoke(invocation(Map.of("previousOrder", "ORD-1"))));
      assertEquals("ORD-1", handler.lastInput.get("previousOrder"));
      var state = handler.lastContext.objectStates().get("/data/previousOrder");
      assertEquals("ORD-1", state.get("id"));
      assertThrows(BusinessException.class,
          () -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("previousOrder", "ORD-1")))));
      assertEquals(1, handler.calls);
    }
  }

  @Test
  void theActualActionEntryPinsTheContextForItsWholeSynchronousExecution() throws Exception {
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var refreshStarted = new java.util.concurrent.CountDownLatch(1);
    var generation = new java.util.concurrent.atomic.AtomicInteger(1);
    BusinessActionHandler handler = new BusinessActionHandler() {
      public String getName() { return "submit-handler"; }
      public Object handle(BusinessActionContext context, com.ouroboros.businessmodel.action.BusinessActionInput<?, ?> actionInput) {
      Map<String, Object> input = com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(actionInput.data()));
        entered.countDown();
        try {
          assertTrue(release.await(3, java.util.concurrent.TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(failure);
        }
        assertEquals(1, generation.get());
        return Map.of("accepted", true);
      }
    };
    var runtime = runtime(handlerAction(), new RecordingTransactionManager(), handler);
    try (var threads = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var action = threads.submit(() -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "ok")))));
      assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
      var refresh = threads.submit(() -> {
        refreshStarted.countDown();
        com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle.publish(this, event -> generation.set(2));
      });
      try {
        assertTrue(refreshStarted.await(2, java.util.concurrent.TimeUnit.SECONDS));
        assertThrows(java.util.concurrent.TimeoutException.class,
            () -> refresh.get(150, java.util.concurrent.TimeUnit.MILLISECONDS));
      } finally {
        release.countDown();
      }
      assertEquals(Map.of("accepted", true), action.get(2, java.util.concurrent.TimeUnit.SECONDS));
      refresh.get(2, java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(2, generation.get());
    }
  }

  @Test
  void descriptionsAreAuthorizedWithoutRunningTheBindingOrOpeningTransactions() {
    var transactions = new RecordingTransactionManager();
    var handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    var runtime = runtime(handlerAction(), transactions, handler);
    assertTrue(withAuthority("sales.Order.search", () -> runtime.describe(MODEL)).isEmpty());
    var descriptions = withAuthority(AUTHORITY, () -> runtime.describe(MODEL));
    assertEquals(1, descriptions.size());
    assertEquals(ACTION, descriptions.get(0).name());
    assertEquals("Submit order", descriptions.get(0).title());
    assertTrue(descriptions.get(0).enabled());
    assertEquals(0, handler.calls);
    assertEquals(0, transactions.starts);
  }

  @Test
  void missingAndAsynchronousBindingsAreRejectedAtPublicationBeforeInvocation() {
    var transactions = new RecordingTransactionManager();
    var missing = new DefaultBusinessActionRuntime(provider(handlerAction()), (flow, inputs) -> Try.success(null),
        List.of(), transactions, ignored -> { });
    var failure = assertThrows(BusinessException.class, () -> missing.validatePublishedBindings(
        new com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent(this)));
    assertTrue(failure.getMessage().contains(MODEL + ".actions.submit.binding-unavailable"));

    var async = runtime(handlerAction(), transactions, new AsynchronousHandler());
    assertThrows(BusinessException.class, () -> async.validatePublishedBindings(
        new com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent(this)));
    assertEquals(BusinessActionAvailability.Status.BLOCKED,
        withAuthority(AUTHORITY, () -> async.checkAvailability(invocation(Map.of("comment", "ok")))).status());
    assertEquals(0, transactions.starts);
  }

  @Test
  void failedRefreshDisablesTheActualActionEntryUntilAFullSuccessfulRefresh() {
    var lifecycle = com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle.class;
    var transactions = new RecordingTransactionManager();
    var handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    var runtime = runtime(handlerAction(), transactions, handler);
    try {
      assertThrows(IllegalStateException.class, () -> com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle.publish(
          lifecycle, event -> { throw new IllegalStateException("refresh failed"); }));
      assertThrows(BusinessException.class,
          () -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "ok")))));
      assertEquals(BusinessActionAvailability.Status.BLOCKED,
          withAuthority(AUTHORITY, () -> runtime.checkAvailability(invocation(Map.of("comment", "ok")))).status());
      assertEquals(0, handler.calls);
      assertEquals(0, transactions.starts);
    } finally {
      com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle.publish(lifecycle, ignored -> { });
    }
    withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "ok"))));
    assertEquals(1, handler.calls);
  }

  public static class AsynchronousHandler implements BusinessActionHandler {
    public String getName() { return "submit-handler"; }
    @org.springframework.scheduling.annotation.Async
    public Object handle(BusinessActionContext context, com.ouroboros.businessmodel.action.BusinessActionInput<?, ?> actionInput) {
      Map<String, Object> input = com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(actionInput.data()));
      throw new AssertionError("asynchronous handler must not execute");
    }
  }

  @Test
  void aHandlerCannotReturnSuccessAfterItsSourceTransactionWasInvalidated() {
    var transactions = new RecordingTransactionManager();
    var audits = new ArrayList<BusinessActionExecutionAuditRecord>();
    BusinessActionHandler handler = new BusinessActionHandler() {
      public String getName() { return "submit-handler"; }
      public Object handle(BusinessActionContext context, com.ouroboros.businessmodel.action.BusinessActionInput<?, ?> actionInput) {
      Map<String, Object> input = com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(actionInput.data()));
        DataModelExecutionContext.markRollbackOnly();
        return Map.of("accepted", true);
      }
    };
    var runtime = runtime(handlerAction(), transactions, handler, audits::add);
    assertThrows(BusinessException.class,
        () -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "confirmed")))));
    assertEquals(0, transactions.commits);
    assertEquals(1, transactions.rollbacks);
    assertEquals("ROLLED_BACK", audits.get(0).transactionResult());
  }

  @Test
  void authorizedHandlerActionValidatesInputAndOutputAndCommits() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    RecordingHandler handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    java.util.ArrayList<BusinessActionExecutionAuditRecord> audits = new java.util.ArrayList<>();
    DefaultBusinessActionRuntime runtime = runtime(handlerAction(), transactions, handler, audits::add);

    Object result = withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "confirmed"))));

    assertEquals(Map.of("accepted", true), result);
    assertEquals(1, handler.calls);
    assertEquals(1, transactions.commits);
    assertEquals(0, transactions.rollbacks);
    assertEquals(1, audits.size());
    assertFalse(audits.get(0).executionId().isBlank());
    assertEquals(MODEL, audits.get(0).modelFullName());
    assertEquals(ACTION, audits.get(0).actionName());
    assertEquals("test-user", audits.get(0).actorRef());
    assertEquals(null, audits.get(0).parentExecutionId());
  }

  @Test
  void missingActionAuthorityRejectsBeforeStartingExecution() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    RecordingHandler handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    DefaultBusinessActionRuntime runtime = runtime(handlerAction(), transactions, handler);

    BusinessException error = assertThrows(BusinessException.class,
        () -> withAuthority("sales.Order.read", () -> runtime.invoke(invocation(Map.of("comment", "confirmed")))));

    assertEquals("业务 Action 未授权", error.getMessage());
    assertEquals(0, handler.calls);
    assertEquals(0, transactions.starts);
  }

  @Test
  void instanceActionReadsItsTargetThroughTheObjectReadAuthority() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    ActionDefinition instanceAction = instanceAction();
    RecordingHandler handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    DefaultBusinessActionRuntime runtime = runtime(instanceAction, transactions, handler);
    BusinessActionInvocation instanceInvocation = instanceInvocation();

    try (DataModelSnapshot snapshot = DataModelSnapshot.publish(MODEL)) {
      // 只授予 Action 权限、未授予对象读取权限：实例目标不可解析，且不泄露对象是否存在。
      BusinessException denied = assertThrows(BusinessException.class,
          () -> withAuthority(AUTHORITY, () -> runtime.invoke(instanceInvocation)));
      assertEquals("业务对象不可用", denied.getMessage());
      assertEquals(0, handler.calls);

      BusinessActionAvailability availability = withAuthority(AUTHORITY,
          () -> runtime.checkAvailability(instanceInvocation));
      assertEquals(BusinessActionAvailability.Status.BLOCKED, availability.status());
      assertEquals(List.of("业务对象不可用"), availability.reasons());

      // 同时授予对象读取权限后，目标可解析并进入执行。
      Object result = withAuthorities(List.of(AUTHORITY, MODEL + ".search"),
          () -> runtime.invoke(instanceInvocation));
      assertEquals(Map.of("accepted", true), result);
      assertEquals(1, handler.calls);
    }
  }

  @Test
  void declaredObjectReferenceInputIsResolvedAndReauthorizedBeforeTheHandlerRuns() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    RecordingHandler handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    ActionDefinition action = handlerAction();
    action.setInputSchema(dataSchema(Map.of(
        "type", "object",
        "properties", Map.of("order", Map.of(
            "type", "object",
            "x-business-object-ref", MODEL,
            "properties", Map.of(
                "modelFullName", Map.of("type", "string"),
                "id", Map.of("type", "string")),
            "required", List.of("id"))),
        "required", List.of("order"),
        "additionalProperties", false)));
    DefaultBusinessActionRuntime runtime = runtime(action, transactions, handler);

    try (DataModelSnapshot snapshot = DataModelSnapshot.publish(MODEL)) {
      Object result = withAuthorities(List.of(AUTHORITY, MODEL + ".search"),
          () -> runtime.invoke(invocation(Map.of("order", Map.of("modelFullName", MODEL, "id", "ORD-1")))));

      assertEquals(Map.of("accepted", true), result);
      assertEquals(Map.of("id", "ORD-1"), handler.lastInput.get("order"));
      assertEquals("ORD-1", handler.lastContext.objectStates().get("/data/order").get("id"));

      RecordingHandler deniedHandler = new RecordingHandler("submit-handler", Map.of("accepted", true));
      DefaultBusinessActionRuntime deniedRuntime = runtime(action, transactions, deniedHandler);
      BusinessException denied = assertThrows(BusinessException.class,
          () -> withAuthority(AUTHORITY, () -> deniedRuntime.invoke(
              invocation(Map.of("order", Map.of("modelFullName", MODEL, "id", "ORD-1"))))));
      assertEquals("业务对象不可用", denied.getMessage());
      assertEquals(0, deniedHandler.calls);
    }
  }

  @Test
  void missingAuditStorageRejectsBeforeRunningTheAction() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    RecordingHandler handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    BusinessActionExecutionAuditRecorder unavailableAudit = new BusinessActionExecutionAuditRecorder() {
      @Override
      public void begin(BusinessActionExecutionAuditRecord execution) {
        throw new BusinessException("audit unavailable");
      }

      @Override
      public void record(BusinessActionExecutionAuditRecord execution) {
      }
    };
    DefaultBusinessActionRuntime runtime = runtime(handlerAction(), transactions, handler, unavailableAudit);

    assertThrows(BusinessException.class,
        () -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "confirmed")))));

    assertEquals(0, handler.calls);
    assertEquals(0, transactions.starts);
  }

  @Test
  void invalidInputSchemaRejectsBeforeExecution() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    RecordingHandler handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    DefaultBusinessActionRuntime runtime = runtime(handlerAction(), transactions, handler);

    BusinessException error = assertThrows(BusinessException.class,
        () -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("unexpected", true)))));

    assertInstanceOf(BusinessException.class, error);
    assertEquals(0, handler.calls);
    assertEquals(0, transactions.starts);
  }

  @Test
  void logicFlowFailureRollsBackTheActionTransaction() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    ActionDefinition definition = handlerAction();
    definition.getBinding().setKind("logicflow");
    definition.getBinding().setHandlerName(null);
    definition.getBinding().setFlowName("sales.Order.submit");
    FlowModel failedFlowModel = flowModel("sales.Order.submit", "flow-revision-failed",
        inputs -> Try.failure(new IllegalStateException("flow failed")));
    LogicFlowExecutionFacade failedFlow = (flowName, inputs) -> Try.success(null);
    java.util.ArrayList<BusinessActionExecutionAuditRecord> audits = new java.util.ArrayList<>();
    DefaultBusinessActionRuntime runtime = new DefaultBusinessActionRuntime(
        provider(definition), failedFlow, List.of(), transactions, audits::add);

    BusinessException error = assertThrows(BusinessException.class,
        () -> withFlowModels(Map.of("sales.Order.submit", failedFlowModel),
            () -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "confirmed"))))));

    assertEquals("业务 Action 执行失败", error.getMessage());
    assertEquals(1, transactions.starts);
    assertEquals(0, transactions.commits);
    assertEquals(1, transactions.rollbacks);
    assertEquals(1, audits.size());
    assertEquals("ROLLED_BACK", audits.get(0).transactionResult());
    assertEquals(BusinessException.class.getName(), audits.get(0).failureType());
  }

  @Test
  void availabilityCheckDoesNotStartATransactionOrCallTheHandler() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    RecordingHandler handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    DefaultBusinessActionRuntime runtime = runtime(handlerAction(), transactions, handler);

    BusinessActionAvailability availability = withAuthority(AUTHORITY,
        () -> runtime.checkAvailability(invocation(Map.of("comment", "confirmed"))));

    assertEquals(BusinessActionAvailability.Status.EXECUTABLE, availability.status());
    assertEquals(0, handler.calls);
    assertEquals(0, transactions.starts);
  }

  @Test
  void readOnlyActionScopesDataModelWriteGuardToTheHandlerExecution() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    RecordingHandler handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    ActionDefinition action = handlerAction();
    action.setReadOnly(true);
    DefaultBusinessActionRuntime runtime = runtime(action, transactions, handler);

    withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "confirmed"))));

    assertEquals(true, handler.readOnlyContext);
    assertEquals(false, DataModelExecutionContext.isReadOnly());
  }

  @Test
  void invalidDataModelWriteSetPreventsActionCommitEvenWhenTheHandlerReturns() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    BusinessActionHandler handler = new BusinessActionHandler() {
      @Override
      public String getName() {
        return "submit-handler";
      }

      @Override
      public Object handle(BusinessActionContext context, com.ouroboros.businessmodel.action.BusinessActionInput<?, ?> actionInput) {
      Map<String, Object> input = com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(actionInput.data()));
        DataModelExecutionContext.recordMutationFailure(new IllegalStateException("row lock unavailable"));
        return Map.of("accepted", true);
      }
    };
    java.util.ArrayList<BusinessActionExecutionAuditRecord> audits = new java.util.ArrayList<>();
    DefaultBusinessActionRuntime runtime = runtime(handlerAction(), transactions, handler, audits::add);

    assertThrows(BusinessException.class,
        () -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "confirmed")))));

    assertEquals(0, transactions.commits);
    assertEquals(1, transactions.rollbacks);
    assertEquals("ROLLED_BACK", audits.get(0).transactionResult());
  }

  @Test
  void readOnlyActionCannotInvokeNestedWritableAction() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    RecordingHandler childHandler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    DefaultBusinessActionRuntime writableChild = runtime(handlerAction(), transactions, childHandler);
    ActionDefinition parentAction = handlerAction();
    parentAction.setReadOnly(true);
    DefaultBusinessActionRuntime readOnlyParent = new DefaultBusinessActionRuntime(
        provider(parentAction), (flowName, inputs) -> Try.success(null), List.of(new BusinessActionHandler() {
          @Override
          public String getName() {
            return "submit-handler";
          }

          @Override
          public Object handle(BusinessActionContext context, com.ouroboros.businessmodel.action.BusinessActionInput<?, ?> actionInput) {
      Map<String, Object> input = com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(actionInput.data()));
            return writableChild.invoke(invocation(Map.of("comment", "nested")));
          }
        }), transactions, ignored -> { });

    BusinessException error = assertThrows(BusinessException.class,
        () -> withAuthority(AUTHORITY, () -> readOnlyParent.invoke(invocation(Map.of("comment", "parent")))));

    assertEquals("只读业务 Action 不能嵌套调用可写业务 Action", error.getMessage());
    assertEquals(0, childHandler.calls);
    assertEquals(1, transactions.rollbacks);
  }

  @Test
  void logicFlowBindingAppliesDeclaredMappingsAndPinsTheResolvedFlowRevision() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    ActionDefinition definition = handlerAction();
    definition.getBinding().setKind("logicflow");
    definition.getBinding().setHandlerName(null);
    definition.getBinding().setFlowName("sales.Order.submit");
    definition.getBinding().setInputMapping(Map.of("payload", "$.input.data"));
    definition.getBinding().setOutputMapping("$.result");
    FlowModel flowModel = flowModel("sales.Order.submit", "flow-revision-1", inputs -> {
      assertEquals(Map.of("payload", Map.of("comment", "confirmed")), inputs[0]);
      return Try.success(Map.of("result", Map.of("accepted", true)));
    });
    LogicFlowExecutionFacade flow = (flowName, inputs) ->
        Try.failure(new AssertionError("must use the pinned flow model"));
    java.util.ArrayList<BusinessActionExecutionAuditRecord> audits = new java.util.ArrayList<>();
    DefaultBusinessActionRuntime runtime = new DefaultBusinessActionRuntime(
        provider(definition), flow, List.of(), transactions, audits::add);

    Object result = withFlowModels(Map.of("sales.Order.submit", flowModel),
        () -> {
          var implementation = runtime.inspectImplementation(MODEL, ACTION).orElseThrow();
          assertEquals("logicflow:sales.Order.submit@flow-revision-1", implementation.implementationRef());
          assertEquals("flow-revision-1", implementation.implementationVersion());
          return withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "confirmed"))));
        });

    assertEquals(Map.of("accepted", true), result);
    assertEquals(1, transactions.commits);
    assertEquals("flow-revision-1", audits.get(0).modelRevision().substring(audits.get(0).modelRevision().lastIndexOf(':') + 1));
    assertTrue(audits.get(0).implementationRef().endsWith("@flow-revision-1"));
  }

  @Test
  void refreshDuringActionExecutionCannotSwapTheResolvedLogicFlow() {
    String flowName = "sales.Order.submit";
    int[] originalCalls = {0};
    int[] refreshedCalls = {0};
    FlowModel original = flowModel(flowName, "revision-before-refresh", inputs -> {
      originalCalls[0]++;
      return Try.success(Map.of("accepted", true));
    });
    FlowModel refreshed = flowModel(flowName, "revision-after-refresh", inputs -> {
      refreshedCalls[0]++;
      return Try.success(Map.of("accepted", false));
    });
    Map<String, FlowModel> flowModels = new HashMap<>(Map.of(flowName, original));
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    transactions.onStart(() -> flowModels.put(flowName, refreshed));
    ActionDefinition action = handlerAction();
    action.getBinding().setKind("logicflow");
    action.getBinding().setHandlerName(null);
    action.getBinding().setFlowName(flowName);
    java.util.ArrayList<BusinessActionExecutionAuditRecord> audits = new java.util.ArrayList<>();
    DefaultBusinessActionRuntime runtime = new DefaultBusinessActionRuntime(
        provider(action), (name, inputs) -> Try.success(null), List.of(), transactions, audits::add);

    Object result = withFlowModels(flowModels,
        () -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "confirmed")))));

    assertEquals(Map.of("accepted", true), result);
    assertEquals(1, originalCalls[0]);
    assertEquals(0, refreshedCalls[0]);
    assertTrue(audits.get(0).modelRevision().endsWith("revision-before-refresh"));
    assertTrue(audits.get(0).implementationRef().endsWith("@revision-before-refresh"));
  }

  @Test
  void malformedPreconditionIsRejectedBeforeExecutionAndReportedAsBlocked() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    RecordingHandler handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    ActionDefinition action = handlerAction();
    action.setPrecondition(Map.of("field", "status", "operator", "eq", "value", "APPROVED"));
    DefaultBusinessActionRuntime runtime = runtime(action, transactions, handler);

    BusinessException error = assertThrows(BusinessException.class,
        () -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "confirmed")))));

    assertEquals("业务 Action 前置条件配置无效", error.getMessage());
    assertEquals(0, handler.calls);
    assertEquals(0, transactions.starts);

    BusinessActionAvailability availability = withAuthority(AUTHORITY,
        () -> runtime.checkAvailability(invocation(Map.of("comment", "confirmed"))));
    assertEquals(BusinessActionAvailability.Status.BLOCKED, availability.status());
  }

  @Test
  void availabilityReportsBlockedWhenTheDeclaredLogicFlowCannotBeResolved() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    ActionDefinition definition = handlerAction();
    definition.getBinding().setKind("logicflow");
    definition.getBinding().setHandlerName(null);
    definition.getBinding().setFlowName("sales.Order.missingFlow");
    DefaultBusinessActionRuntime runtime = runtime(definition, transactions,
        new RecordingHandler("submit-handler", Map.of("accepted", true)));

    BusinessActionAvailability availability = withAuthority(AUTHORITY,
        () -> runtime.checkAvailability(invocation(Map.of("comment", "confirmed"))));

    assertEquals(BusinessActionAvailability.Status.BLOCKED, availability.status());
    assertEquals(List.of("Action 实现不可用"), availability.reasons());
  }

  @Test
  void asyncLogicFlowBindingIsBlockedAndRejectedBeforeExecution() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    ActionDefinition definition = handlerAction();
    definition.getBinding().setKind("logicflow");
    definition.getBinding().setHandlerName(null);
    definition.getBinding().setFlowName("sales.Order.asyncFlow");
    int[] flowCalls = {0};
    FlowModel asyncFlow = flowModel("sales.Order.asyncFlow", "async-revision", inputs -> {
      flowCalls[0]++;
      return Try.success(Map.of("accepted", true));
    }, List.of(flowNode(true)));
    LogicFlowExecutionFacade facade = (flowModel, inputs) -> {
      flowCalls[0]++;
      return Try.success(Map.of("accepted", true));
    };
    DefaultBusinessActionRuntime runtime = new DefaultBusinessActionRuntime(
        provider(definition), facade, List.of(), transactions, ignored -> { });

    withFlowModels(Map.of("sales.Order.asyncFlow", asyncFlow), () -> {
      BusinessActionAvailability availability = withAuthority(AUTHORITY,
          () -> runtime.checkAvailability(invocation(Map.of("comment", "confirmed"))));
      assertEquals(BusinessActionAvailability.Status.BLOCKED, availability.status());
      assertEquals(List.of("Action 实现不可用"), availability.reasons());

      BusinessException error = assertThrows(BusinessException.class,
          () -> withAuthority(AUTHORITY,
              () -> runtime.invoke(invocation(Map.of("comment", "confirmed")))));
      assertEquals("业务 Action 实现不可用", error.getMessage());
      return null;
    });

    assertEquals(0, flowCalls[0]);
    assertEquals(0, transactions.starts);
  }

  @Test
  void runtimeResolvesAnnotatedHandlerAfterSpringSingletonInitialization() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.registerBean("annotatedHandlerBean", AnnotatedHandlerBean.class, AnnotatedHandlerBean::new);
    context.registerBean("businessActionAnnotationCatalog", BusinessActionAnnotationCatalog.class,
        () -> new BusinessActionAnnotationCatalog(context.getBeanFactory()));
    context.registerBean("businessActionRuntime", BusinessActionRuntime.class, () -> {
      BusinessActionAnnotationCatalog catalog = context.getBean(BusinessActionAnnotationCatalog.class);
      return new DefaultBusinessActionRuntime(provider(handlerAction()), (flowName, inputs) -> Try.success(null),
          catalog::getHandlers, new RecordingTransactionManager(), ignored -> { });
    });
    context.refresh();
    try {
      BusinessActionRuntime runtime = context.getBean(BusinessActionRuntime.class);

      BusinessActionAvailability availability = withAuthority(AUTHORITY,
          () -> runtime.checkAvailability(invocation(Map.of("comment", "confirmed"))));

      assertEquals(BusinessActionAvailability.Status.EXECUTABLE, availability.status());
      assertTrue(context.getBean(BusinessActionAnnotationCatalog.class).getHandlers().stream()
          .anyMatch(handler -> "submit-handler".equals(handler.getName())));
    } finally {
      context.close();
    }
  }

  @Test
  void nestedActionExecutionLinksChildAuditToItsParentAndRollsBackTogether() {
    RecordingTransactionManager transactions = new RecordingTransactionManager();
    java.util.ArrayList<BusinessActionExecutionAuditRecord> audits = new java.util.ArrayList<>();
    DefaultBusinessActionRuntime runtime = runtime(handlerAction(), transactions,
        new RecordingHandler("submit-handler", Map.of("accepted", true)), audits::add);
    java.util.concurrent.atomic.AtomicReference<String> observedExecutionId =
        new java.util.concurrent.atomic.AtomicReference<>();
    DefaultBusinessActionRuntime nestedRuntime = new DefaultBusinessActionRuntime(
        provider(handlerAction()), (flowName, inputs) -> Try.success(null),
        List.of(new BusinessActionHandler() {
      @Override
      public String getName() {
        return "submit-handler";
      }

      @Override
      public Object handle(BusinessActionContext context, com.ouroboros.businessmodel.action.BusinessActionInput<?, ?> actionInput) {
      Map<String, Object> input = com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(actionInput.data()));
        // 同步嵌套调用沿用外层执行身份与事务边界，不新开身份。
        observedExecutionId.set(BusinessActionExecutionScope.currentExecutionId().orElse(null));
        assertEquals(null, context.parentExecutionId());
        // 同步嵌套调用必须经过同一运行时，并沿用当前身份与事务边界。
        return runtime.invoke(new BusinessActionInvocation(MODEL, ACTION, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("comment", "nested"))));
      }
    }), transactions, audits::add);

    Object result = withAuthority(AUTHORITY,
        () -> nestedRuntime.invoke(new BusinessActionInvocation(MODEL, ACTION, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(Map.of("comment", "parent")))));

    assertEquals(Map.of("accepted", true), result);
    assertEquals(2, audits.size());
    BusinessActionExecutionAuditRecord parent = audits.stream()
        .filter(record -> record.parentExecutionId() == null).findFirst().orElseThrow();
    BusinessActionExecutionAuditRecord child = audits.stream()
        .filter(record -> record.parentExecutionId() != null).findFirst().orElseThrow();
    assertEquals(parent.executionId(), child.parentExecutionId());
    assertEquals(parent.executionId(), observedExecutionId.get());
  }

  @Test
  void joiningAnOuterTransactionPersistsOnlyTheFinalTransactionResult() {
    SynchronizationTransactionManager transactions = new SynchronizationTransactionManager();
    java.util.ArrayList<BusinessActionExecutionAuditRecord> audits = new java.util.ArrayList<>();
    DefaultBusinessActionRuntime runtime = runtime(handlerAction(), transactions,
        new RecordingHandler("submit-handler", Map.of("accepted", true)), audits::add);

    Object result = withAuthority(AUTHORITY, () -> new TransactionTemplate(transactions).execute(status ->
        {
          Object actionResult = runtime.invoke(invocation(Map.of("comment", "confirmed")));
          // 成功态更新先加入业务事务，最终回滚状态由最外层事务决定。
          assertEquals(1, audits.size());
          assertEquals("COMMITTED", audits.get(0).transactionResult());
          status.setRollbackOnly();
          return actionResult;
        }));

    assertEquals(Map.of("accepted", true), result);
    assertEquals(1, transactions.outerTransactions);
    assertEquals(0, transactions.commits);
    assertEquals(1, transactions.rollbacks);
    assertEquals(2, audits.size());
    assertEquals("ROLLED_BACK", audits.get(1).transactionResult());
  }

  @Test
  void successAuditFailureRollsBackBusinessWorkAndLeavesRollbackDiagnostic() {
    SynchronizationTransactionManager transactions = new SynchronizationTransactionManager();
    RecordingHandler handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    List<String> auditStates = new ArrayList<>();
    BusinessActionExecutionAuditRecorder recorder = new BusinessActionExecutionAuditRecorder() {
      @Override
      public void record(BusinessActionExecutionAuditRecord execution) {
        auditStates.add(execution.transactionResult());
        if ("COMMITTED".equals(execution.transactionResult())) {
          throw new BusinessException("audit completion failed");
        }
      }
    };
    DefaultBusinessActionRuntime runtime = runtime(handlerAction(), transactions, handler, recorder);

    assertThrows(BusinessException.class,
        () -> withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "confirmed")))));

    assertEquals(1, handler.calls);
    assertEquals(0, transactions.commits);
    assertEquals(1, transactions.rollbacks);
    assertEquals(List.of("COMMITTED", "ROLLED_BACK"), auditStates);
  }

  private static Map<String, Object> dataSchema(Map<String, Object> schema) {
    return Map.of("type", "object", "required", List.of("data"), "additionalProperties", false, "properties", Map.of("data", schema));
  }

  @Test
  void allObjectRolesAndArrayEntriesAreAuthorizedBeforeTheSingleExecution() {
    var action = handlerAction();
    var orderRef = Map.of("type", "object", "x-business-object-ref", MODEL, "properties", Map.of("id", Map.of("type", "string")), "required", List.of("id"));
    var reviewerRef = Map.of("type", "object", "x-business-object-ref", "sales.Reviewer", "properties", Map.of("id", Map.of("type", "string")), "required", List.of("id"));
    action.setInputSchema(Map.of("type", "object", "properties", Map.of(
        "params", Map.of("type", "object", "properties", Map.of("orders", Map.of("type", "array", "items", orderRef))),
        "data", Map.of("type", "object", "properties", Map.of("reviewer", reviewerRef)))));
    var handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    var runtime = runtime(action, new RecordingTransactionManager(), handler);
    var input = BusinessActionInput.of(Map.of("orders", List.of(Map.of("id", "one"), Map.of("id", "two"))),
        Map.of("reviewer", Map.of("id", "reviewer")));
    try (var orders = DataModelSnapshot.publish(MODEL); var reviewers = DataModelSnapshot.publish("sales.Reviewer")) {
      assertThrows(BusinessException.class, () -> withAuthorities(List.of(AUTHORITY, MODEL + ".search"),
          () -> runtime.invoke(MODEL, ACTION, input)));
      assertEquals(0, handler.calls);
      withAuthorities(List.of(AUTHORITY, MODEL + ".search", "sales.Reviewer.search"), () -> runtime.invoke(MODEL, ACTION, input));
      assertEquals(1, handler.calls);
      assertEquals(java.util.Set.of("/params/orders/0", "/params/orders/1", "/data/reviewer"), handler.lastContext.objectStates().keySet());
      assertEquals("two", handler.lastContext.objectStates().get("/params/orders/1").get("id"));
      assertEquals(Map.of("id", "reviewer"), handler.lastInput.get("reviewer"));
    }
  }

  @Test
  void contextUsesTrustedRequestAndDetachedExecutionCannotInheritIt() {
    var action = handlerAction();
    var handler = new RecordingHandler("submit-handler", Map.of("accepted", true));
    var runtime = runtime(action, new RecordingTransactionManager(), handler);
    Map<String, Object> request = Map.of("headers", Map.of("trace", "live-request"));
    runtime.setRequestProvider(() -> request);
    withAuthority(AUTHORITY, () -> runtime.invoke(invocation(Map.of("comment", "explicit-input"))));
    org.junit.jupiter.api.Assertions.assertSame(request, handler.lastContext.request());
    assertEquals("explicit-input", handler.lastInput.get("comment"));
    withAuthority(AUTHORITY, () -> BusinessActionExecutionScope.detached(
        () -> runtime.invoke(invocation(Map.of("comment", "event-input")))));
    org.junit.jupiter.api.Assertions.assertNull(handler.lastContext.request());
    assertEquals("event-input", handler.lastInput.get("comment"));
    action.getBinding().setRequiresHttpRequest(true);
    var required = runtime(action, new RecordingTransactionManager(), handler);
    int calls = handler.calls;
    assertThrows(BusinessException.class, () -> withAuthority(AUTHORITY,
        () -> required.invoke(invocation(Map.of("comment", "missing-request")))));
    assertEquals(calls, handler.calls);
  }

  private static DefaultBusinessActionRuntime runtime(ActionDefinition action,
                                                       PlatformTransactionManager transactions,
                                                       BusinessActionHandler handler) {
    return runtime(action, transactions, handler, ignored -> { });
  }

  private static DefaultBusinessActionRuntime runtime(ActionDefinition action,
                                                       PlatformTransactionManager transactions,
                                                       BusinessActionHandler handler,
                                                       BusinessActionExecutionAuditRecorder auditRecorder) {
    return new DefaultBusinessActionRuntime(provider(action), (flowName, inputs) -> Try.success(null),
        List.of(handler), transactions, auditRecorder);
  }

  private static BusinessActionInvocation invocation(Map<String, Object> input) {
    return new BusinessActionInvocation(MODEL, ACTION, com.ouroboros.businessmodel.action.BusinessActionInput.dataOnly(input));
  }

  private static BusinessActionInvocation instanceInvocation() {
    return new BusinessActionInvocation(MODEL, ACTION, com.ouroboros.businessmodel.action.BusinessActionInput.of(Map.of("record", new BusinessObjectReference(MODEL, "ORD-1")), Map.of("comment", "confirmed")));
  }

  private static FlowModel flowModel(String name, String revision, FlowExecution execution) {
    return new StubFlowModel(name, revision, execution);
  }

  private static FlowModel flowModel(String name, String revision, FlowExecution execution, List<FlowNode> nodes) {
    return new StubFlowModel(name, revision, execution, nodes);
  }

  private static FlowNode flowNode(boolean async) {
    return new FlowNode() {
      @Override
      public String getName() {
        return "test-node";
      }

      @Override
      public String getDescription() {
        return "test node";
      }

      @Override
      public String getType() {
        return "test";
      }

      @Override
      public List<com.ouroboros.logicflow.model.FlowNodePort> getInputPorts() {
        return List.of();
      }

      @Override
      public List<com.ouroboros.logicflow.model.FlowNodePort> getOutputPorts() {
        return List.of();
      }

      @Override
      public Boolean isAsync() {
        return async;
      }

      @Override
      public Boolean isEnabled() {
        return true;
      }

      @Override
      public Boolean isValid() {
        return true;
      }

      @Override
      public Try<List<Object>> execute(List<Object> inputs) {
        return Try.success(List.of());
      }

      @Override
      public List<FlowConnection> getIncomingConnections() {
        return List.of();
      }

      @Override
      public List<FlowConnection> getOutgoingConnections() {
        return List.of();
      }

      @Override
      public List<com.ouroboros.logicflow.exception.NodeDesignException> getErrors() {
        return List.of();
      }
    };
  }

  private static <T> T withFlowModels(Map<String, FlowModel> flowModels, Supplier<T> operation) {
    try {
      java.lang.reflect.Field providersField = FlowModelCenter.class.getDeclaredField("providers");
      providersField.setAccessible(true);
      Object previous = providersField.get(null);
      FlowModelProvider provider = new FlowModelProvider() {
        @Override
        public Optional<FlowModel> getFlowModel(String flowName) {
          return flowModels.entrySet().stream()
              .filter(entry -> entry.getKey().equalsIgnoreCase(flowName))
              .map(Map.Entry::getValue)
              .findFirst();
        }

        @Override
        public Map<String, FlowModel> getFlowModelMap() {
          return new HashMap<>(flowModels);
        }
      };
      providersField.set(null, List.of(provider));
      try {
        return operation.get();
      } finally {
        providersField.set(null, previous);
      }
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("无法替换测试逻辑流快照", error);
    }
  }

  @FunctionalInterface
  private interface FlowExecution {
    Try<Object> execute(Object[] inputs);
  }

  private static final class StubFlowModel implements FlowModel {
    private final String name;
    private final String revision;
    private final FlowExecution execution;
    private final List<FlowNode> nodes;

    private StubFlowModel(String name, String revision, FlowExecution execution) {
      this(name, revision, execution, List.of());
    }

    private StubFlowModel(String name, String revision, FlowExecution execution, List<FlowNode> nodes) {
      this.name = name;
      this.revision = revision;
      this.execution = execution;
      this.nodes = List.copyOf(nodes);
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getRevision() {
      return revision;
    }

    @Override
    public String getDescription() {
      return "test flow";
    }

    @Override
    public List<FlowNode> getNodes() {
      return nodes;
    }

    @Override
    public List<FlowConnection> getConnections() {
      return List.of();
    }

    @Override
    public Boolean isEnabled() {
      return true;
    }

    @Override
    public Boolean isValid() {
      return true;
    }

    @Override
    public DataTypeEnum getInputDataType() {
      return DataTypeEnum.ANY;
    }

    @Override
    public DataTypeEnum getOutputDataType() {
      return DataTypeEnum.ANY;
    }

    @Override
    public Try<Object> execute(Object... inputs) {
      return execution.execute(inputs);
    }

    @Override
    public FlowNode getEntryNode() {
      return null;
    }

    @Override
    public FlowNode getExitNode() {
      return null;
    }

    @Override
    public FlowNode getNode(String nodeName) {
      return null;
    }

    @Override
    public List<DesignException> getErrors() {
      return List.of();
    }
  }

  private static ActionDefinition instanceAction() {
    ActionDefinition action = handlerAction();
    action.setInputSchema(Map.of("type", "object", "required", List.of("params", "data"), "properties", Map.of(
        "data", Map.of("type", "object", "required", List.of("comment"), "properties", Map.of("comment", Map.of("type", "string"))),
        "params", Map.of("type", "object", "required", List.of("record"), "properties", Map.of("record", Map.of(
            "type", "object", "x-business-object-ref", MODEL, "required", List.of("id"), "properties", Map.of("id", Map.of("type", "string"))))))));
    return action;
  }

  private static ActionDefinition handlerAction() {
    ActionDefinition action = new ActionDefinition();
    action.setName(ACTION);
    action.setTitle("Submit order");

    action.setIsEnabled(true);
    action.setInputSchema(dataSchema(Map.of(
        "type", "object",
        "properties", Map.of("comment", Map.of("type", "string")),
        "required", List.of("comment"),
        "additionalProperties", false)));
    action.setOutputSchema(Map.of(
        "type", "object",
        "properties", Map.of("accepted", Map.of("type", "boolean")),
        "required", List.of("accepted"),
        "additionalProperties", false));
    ActionBinding binding = new ActionBinding();
    binding.setKind("handler");
    binding.setHandlerName("submit-handler");
    action.setBinding(binding);
    return action;
  }

  private static BusinessModelBehaviorProvider provider(ActionDefinition action) {
    BusinessModelBehavior behavior = new BusinessModelBehavior(MODEL, List.of(action), List.of(), List.of());
    return provider(behavior);
  }

  private static BusinessModelBehaviorProvider provider(BusinessModelBehavior behavior) {
    return new BusinessModelBehaviorProvider() {
      @Override
      public Optional<BusinessModelBehavior> getBusinessModelBehavior(String modelFullName) {
        return MODEL.equalsIgnoreCase(modelFullName) ? Optional.of(behavior) : Optional.empty();
      }

      @Override
      public Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap() {
        return Map.of(MODEL, behavior);
      }
    };
  }

  private static <T> T withAuthority(String authorityName, java.util.function.Supplier<T> action) {
    return withAuthorities(List.of(authorityName), action);
  }

  private static <T> T withAuthorities(List<String> authorityNames, java.util.function.Supplier<T> action) {
    List<OuroborosGrantedAuthority> grants = authorityNames.stream()
        .map(DefaultBusinessActionRuntimeTest::grant)
        .toList();
    OuroborosAuthentication authentication = new OuroborosAuthentication() {
      @Override
      public String getPrincipal() {
        return "test-user";
      }

      @Override
      public Object getCredentials() {
        return null;
      }

      @Override
      public Object getDetails() {
        return null;
      }

      @Override
      public boolean isAuthenticated() {
        return true;
      }

      @Override
      public void setAuthenticated(boolean authenticated) {
      }

      @Override
      public User getUserDetail() {
        return new User() {
          public String getUserIdentity() { return "test-user"; }
          public List<Role> getRoles() { return List.of(new Role() {
            public String getId() { return "test-role"; }
            public String getName() { return "test-role"; }
            public List<? extends OuroborosGrantedAuthority> getAuthorities() { return grants; }
          }); }
        };
      }

      @Override
      public List<? extends Role> getRoles() {
        return getUserDetail().getRoles();
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all();
      }

      @Override
      public List<? extends OuroborosGrantedAuthority> getAuthorities() {
        return grants;
      }
    };
    CurrentSecurityContext securityContext = new CurrentSecurityContext() {
      @Override
      public Optional<SecurityIdentity> findIdentity() {
        return Optional.empty();
      }

      @Override
      public Optional<OuroborosAuthentication> findAuthentication() {
        return Optional.of(authentication);
      }
    };
    return CurrentSecurityContextHolder.withContext(securityContext, action);
  }

  private static OuroborosGrantedAuthority grant(String authorityName) {
    return new OuroborosGrantedAuthority() {
      @Override
      public String getAuthority() {
        return authorityName;
      }

      @Override
      public Authority getAuthorityObject() {
        BaseAuthority authority = new BaseAuthority();
        authority.setFullName(authorityName);
        return authority;
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all();
      }
    };
  }

  private static final class RecordingHandler implements BusinessActionHandler {
    private final String name;
    private final Object result;
    private int calls;
    private boolean readOnlyContext;
    private Map<String, Object> lastInput = Map.of();
    private BusinessActionContext lastContext;

    private RecordingHandler(String name, Object result) {
      this.name = name;
      this.result = result;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Object handle(BusinessActionContext context, com.ouroboros.businessmodel.action.BusinessActionInput<?, ?> actionInput) {
      Map<String, Object> input = com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(actionInput.data()));
      calls++;
      assertEquals(AUTHORITY, context.authorityName());
      readOnlyContext = DataModelExecutionContext.isReadOnly();
      lastInput = input;
      lastContext = context;
      return result;
    }
  }

  static class AnnotatedHandlerBean {
    @BusinessAction(modelFullName = MODEL, name = ACTION, handlerName = "submit-handler")
    public Map<String, Object> submit(BusinessActionContext context, com.ouroboros.businessmodel.action.BusinessActionInput<?, ?> actionInput) {
      Map<String, Object> input = com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(actionInput.data()));
      return Map.of("accepted", true);
    }
  }

  private static final class RecordingTransactionManager implements PlatformTransactionManager {
    private int starts;
    private int commits;
    private int rollbacks;
    private Runnable onStart = () -> { };

    private void onStart(Runnable action) {
      onStart = action;
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      starts++;
      onStart.run();
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      if (status.isRollbackOnly()) {
        rollbacks++;
      } else {
        commits++;
      }
    }

    @Override
    public void rollback(TransactionStatus status) {
      rollbacks++;
    }
  }

  /** 启用 Spring 事务同步，用于验证加入外层事务后的完成态审计时机。 */
  private static final class SynchronizationTransactionManager implements PlatformTransactionManager {
    private int outerTransactions;
    private int commits;
    private int rollbacks;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
        // PROPAGATION_REQUIRED 加入外层事务：内层结束不驱动完成回调。
        return new ParticipatingTransactionStatus();
      }
      TransactionSynchronizationManager.initSynchronization();
      outerTransactions++;
      return new NewTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      if (status instanceof ParticipatingTransactionStatus || status.isRollbackOnly()) {
        if (!(status instanceof ParticipatingTransactionStatus)) {
          complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        return;
      }
      complete(TransactionSynchronization.STATUS_COMMITTED);
    }

    @Override
    public void rollback(TransactionStatus status) {
      if (status instanceof ParticipatingTransactionStatus) {
        return;
      }
      complete(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    private void complete(int status) {
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(synchronization -> synchronization.afterCompletion(status));
        TransactionSynchronizationManager.clearSynchronization();
      }
      if (status == TransactionSynchronization.STATUS_COMMITTED) {
        commits++;
      } else {
        rollbacks++;
      }
    }

    private static final class NewTransactionStatus extends SimpleTransactionStatus {
    }

    /** 参与外层事务的状态：结束时不驱动完成回调，由最外层所有者决定结果。 */
    private static final class ParticipatingTransactionStatus extends SimpleTransactionStatus {
    }
  }

  /** 临时发布一个带单 id 主键的模型快照，测试结束后恢复原有快照。 */
  private static final class DataModelSnapshot implements AutoCloseable {
    private final java.util.Map<String, com.ouroboros.data.model.DataModel> previous;

    private DataModelSnapshot(java.util.Map<String, com.ouroboros.data.model.DataModel> previous) {
      this.previous = previous;
    }

    static DataModelSnapshot publish(String modelFullName) {
      java.util.Map<String, com.ouroboros.data.model.DataModel> previous = DataModelCenter.getDataModelMap();
      String[] parts = modelFullName.split("\\.", 2);
      DataModelFieldMeta id = new DataModelFieldMeta();
      id.setName("id");
      id.setType("String");
      DataModelMeta meta = new DataModelMeta();
      meta.setNamespace(parts[0]);
      meta.setName(parts.length > 1 ? parts[1] : parts[0]);
      meta.setFields(List.of(id));
      meta.setPrimaryKeys(List.of("id"));
      java.util.Map<String, com.ouroboros.data.model.DataModel> models = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      models.putAll(previous);
      models.put(modelFullName, new SingleRecordDataModel(meta));
      publish(models);
      return new DataModelSnapshot(previous);
    }

    @Override
    public void close() {
      publish(previous);
    }

    private static void publish(java.util.Map<String, com.ouroboros.data.model.DataModel> models) {
      try {
        java.lang.reflect.Field field = DataModelCenter.class.getDeclaredField("dataModelMap");
        field.setAccessible(true);
        field.set(null, models.isEmpty() ? null : models);
      } catch (ReflectiveOperationException error) {
        throw new IllegalStateException("无法发布测试数据模型快照", error);
      }
    }
  }

  /** 只支持按主键读取一条固定记录的最小模型，用于验证实例目标解析。 */
  private static final class SingleRecordDataModel extends com.ouroboros.data.model.AbstractDataModel {
    private SingleRecordDataModel(DataModelMeta meta) {
      super(meta);
    }

    @Override
    public com.ouroboros.data.adapter.DataAdapter getAdapter() {
      return null;
    }

    @Override
    public com.ouroboros.data.station.DataStation<?> getDataStation() {
      return null;
    }

    @Override
    public Try<com.ouroboros.data.record.Record> get(Object id, java.util.Map<String, Object> statement) {
      return Try.success(com.ouroboros.data.record.Record.of(java.util.Map.of("id", id)));
    }

    @Override
    public Try<com.ouroboros.data.record.RecordList> query(com.ouroboros.data.dsl.statement.QueryStatement statement) {
      return Try.success(com.ouroboros.data.record.RecordList.of(List.of(Map.of("id", "ORD-1"))));
    }

    @Override
    public Try<com.ouroboros.data.record.RecordList> query(java.util.Map<String, Object> statement) {
      return Try.success(com.ouroboros.data.record.RecordList.of(List.of(Map.of("id", "ORD-1"))));
    }

    @Override
    public Try<com.ouroboros.data.record.Record> insert(java.util.Map<String, Object> data) {
      return Try.failure(new UnsupportedOperationException("test model is read-only"));
    }

    @Override
    public Try<com.ouroboros.data.record.RecordList> batchInsert(java.util.List<java.util.Map<String, Object>> dataList) {
      return Try.failure(new UnsupportedOperationException("test model is read-only"));
    }

    @Override
    public Try<Long> update(java.util.Map<String, Object> where, java.util.Map<String, Object> data) {
      return Try.failure(new UnsupportedOperationException("test model is read-only"));
    }

    @Override
    public Try<Long> delete(java.util.Map<String, Object> where) {
      return Try.failure(new UnsupportedOperationException("test model is read-only"));
    }

    @Override
    public Try<Long> count(java.util.Map<String, Object> where) {
      return Try.success(0L);
    }

    @Override
    public com.ouroboros.data.model.DataModel withPlugins(java.util.Collection<com.ouroboros.data.model.PluginDescriptor> descriptors) {
      return this;
    }

    @Override
    public com.ouroboros.data.model.DataModel withoutPlugins(java.util.Collection<String> pluginNames) {
      return this;
    }

    @Override
    public com.ouroboros.data.model.DataModel withoutPlugins() {
      return this;
    }

    @Override
    public boolean hasPlugin(String name) {
      return false;
    }
  }

  private static com.ouroboros.businessmodel.BusinessActionAnnotationCatalog registerBusinessActionAnnotationCatalog(org.springframework.context.support.GenericApplicationContext context) {
    var catalog = new com.ouroboros.businessmodel.BusinessActionAnnotationCatalog(context.getBeanFactory());
    context.getBeanFactory().registerSingleton(BusinessActionAnnotationCatalog.class.getName(), catalog);
    return catalog;
  }
  private static org.springframework.context.support.GenericApplicationContext annotationContext() {
    var context = new org.springframework.context.support.GenericApplicationContext();
    context.registerBean(com.ouroboros.common.app.util.SpringContextUtil.class);
    return context;
  }
}
