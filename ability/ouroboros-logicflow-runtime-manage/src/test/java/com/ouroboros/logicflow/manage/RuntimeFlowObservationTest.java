package com.ouroboros.logicflow.manage;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.manage.observation.FlowObservation.*;
import com.ouroboros.logicflow.manage.observation.FlowObservationConfiguration;
import com.ouroboros.logicflow.manage.web.RuntimeFlowAccess;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.util.Json;

class RuntimeFlowObservationTest {
  @TempDir Path home;
  static final Set<String> OPERATOR = Set.of(RuntimeFlowAccess.READ, RuntimeFlowAccess.OBSERVE_READ, RuntimeFlowAccess.OBSERVE_MANAGE);
  static Command command(String revision, boolean monitor, boolean diagnostics) { return new Command(revision, monitor, diagnostics, 15, "incident-123"); }

  @Test void actualConfigurationCasMergesPerFlowAndAuditRollsBackWithFailure() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var o = f.observation;
      CurrentSecurityContextHolder.withContextThrowing(RuntimeFlowHttpTest.identity("operator", OPERATOR), () -> {
        var original = o.service.read("child");
        assertFalse(original.policy().monitorEnabled()); assertFalse(original.policy().diagnosticEnabled());
        var after = o.service.save("child", command(original.revision(), true, false));
        assertEquals("operator", after.policy().actor()); assertNotEquals(original.revision(), after.revision());
        assertEquals(1, o.store.history("child").size());
        var conflict = assertThrows(BusinessException.class, () -> o.service.save("parent", command(original.revision(), true, true)));
        assertEquals(409, conflict.getErrorCode()); assertTrue(o.store.history("parent").isEmpty());
        var both = o.service.save("parent", command(after.revision(), true, true));
        assertTrue(o.service.read("child").policy().monitorEnabled());
        assertTrue(both.policy().diagnosticEnabled());
        assertEquals(1, o.store.history("parent").size());
        var retained = o.service.save("parent", command(both.revision(), true, true));
        assertEquals(both.policy().captureId(), retained.policy().captureId());
        assertEquals(both.policy().diagnosticExpiresAt(), retained.policy().diagnosticExpiresAt());
        assertThrows(BusinessException.class, () -> o.configuration.mutateManagementConfigurations(
            Map.of(FlowObservationConfiguration.KEY, new ConfigurationMutation.Replace(Map.of())),
            Map.of(FlowObservationConfiguration.KEY, retained.revision())));
        assertThrows(BusinessException.class, () -> o.service.save("child", new Command(retained.revision(), false, true, 15, "bad")));
        assertThrows(BusinessException.class, () -> o.service.save("child", new Command(retained.revision(), true, true, 2, "bad")));
        assertThrows(BusinessException.class, () -> o.service.save("child", new Command(retained.revision(), true, false, 15, "")));
        assertEquals(1, o.store.history("child").size());
        assertEquals(retained.revision(), o.service.read("child").revision());
        return null;
      });
    }
  }

  @Test void realEngineUsesIndependentPolicyWithoutRebuildingAndReporterPersistsRetrySafeResults() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var o = f.observation;
      CurrentSecurityContextHolder.withContextThrowing(RuntimeFlowHttpTest.identity("operator", OPERATOR), () -> {
        var publication = f.runtime.publication().orElseThrow();
        var child = f.runtime.getFlowModel("child").orElseThrow();
        o.start();
        assertEquals("default", f.runtime.getFlowModel("parent").orElseThrow().execute("input").get());
        o.runtime.tick(); assertFalse(o.service.metrics("child", 15, null, null).collected());
        var before = o.service.read("child");
        o.service.save("child", command(before.revision(), true, true)); o.runtime.tick();
        assertEquals("default", f.runtime.getFlowModel("parent").orElseThrow().execute("sensitive-input").get());
        o.runtime.tick(); o.runtime.tick();
        assertEquals(1, o.service.metrics("child", 15, null, null).completed());
        assertFalse(o.service.metrics("parent", 15, null, null).collected());
        assertNull(o.service.metrics("parent", 15, null, null).failureRate());
        var runs = o.service.runs("child"); assertEquals(1, runs.size()); assertNull(runs.getFirst().parentInvocationId());
        assertEquals(2, runs.getFirst().nodes().size()); assertEquals(runs.getFirst(), o.service.run("CHILD", runs.getFirst().id()));
        assertFalse(Json.toJsonString(runs).contains("sensitive-input"));
        assertThrows(BusinessException.class, () -> o.service.run("parent", runs.getFirst().id()));
        assertSame(publication, f.runtime.publication().orElseThrow()); assertSame(child, f.runtime.getFlowModel("child").orElseThrow());
        o.service.save("child", command(o.service.read("child").revision(), false, false)); o.runtime.tick();
        child.execute("input").get(); o.runtime.tick();
        assertEquals(1, o.service.metrics("child", 15, null, null).completed()); assertEquals(1, o.service.runs("child").size());
        assertEquals(0, o.service.metrics("child", 15, "absent-boot", null).completed());
        assertThrows(BusinessException.class, () -> o.service.metrics("child", 10, null, null));
        assertFalse(o.service.statuses("child").isEmpty());
        return null;
      });
    }
  }

  @Test void configurationFailureStopsAdmissionAndRecoveryDoesNotResurrectExpiredDiagnostics() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var o = f.observation;
      CurrentSecurityContextHolder.withContextThrowing(RuntimeFlowHttpTest.identity("operator", OPERATOR), () -> {
        o.service.save("child", command(o.service.read("child").revision(), true, true)); o.start();
        var storage = com.ouroboros.data.model.TypedDataModelCenter.getDataModel(String.class,
            com.ouroboros.configuration.model.RuntimeConfigurationValueModel.class).orElseThrow();
        var row = storage.get(FlowObservationConfiguration.KEY).get(); String original = row.getValue();
        storage.update(FlowObservationConfiguration.KEY, Map.of("value", "{broken" )).get();
        o.runtime.tick(); assertNotNull(o.runtime.localStatus().error());
        assertEquals("default", f.runtime.getFlowModel("child").orElseThrow().execute("input").get());
        assertTrue(o.store.windows("child", 0).isEmpty());
        var parsed = FlowObservationConfiguration.parse(original);
        var old = parsed.flows().get("child");
        var expired = new Policy(true, true, System.currentTimeMillis() - 1000, old.captureId(), old.actor(), old.reason(), old.updatedAt());
        storage.update(FlowObservationConfiguration.KEY, Map.of("value", Json.toJsonString(new Policies(1, Map.of("child", expired))))).get();
        o.runtime.tick(); o.runtime.tick(); assertNull(o.runtime.localStatus().error());
        assertEquals("expired", o.runtime.localStatus().diagnostics().get("child"));
        f.runtime.getFlowModel("child").orElseThrow().execute("input").get(); o.runtime.tick();
        assertEquals(1, o.service.metrics("child", 15, null, null).completed()); assertTrue(o.service.runs("child").isEmpty());
        assertEquals(2, o.store.history("child").size());
        assertEquals(1, o.store.history("child").stream().filter(change -> "system-expiry".equals(((Map<?, ?>) change.get("after")).get("actor"))).count());
        return null;
      });
    }
  }

  @Test void auditAndPolicyShareActualTransactionWhenLaterValidationFails() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var o = f.observation;
      var target = org.springframework.test.util.AopTestUtils.getTargetObject(o.configuration);
      org.springframework.test.util.ReflectionTestUtils.setField(target, "valueValidators", List.of(
          new com.ouroboros.configuration.validation.ConfigurationValueValidator() {
            @Override public boolean supports(String key) { return key.equals(FlowObservationConfiguration.KEY); }
            @Override public void validate(Object value) { throw new BusinessException(400, "fixture rejection after audit"); }
          }));
      CurrentSecurityContextHolder.withContextThrowing(RuntimeFlowHttpTest.identity("operator", OPERATOR), () -> {
        String revision = o.service.read("child").revision();
        assertThrows(BusinessException.class, () -> o.service.save("child", command(revision, true, true)));
        assertEquals(revision, o.service.read("child").revision()); assertTrue(o.store.history("child").isEmpty());
        return null;
      });
    }
  }

  @Test void malformedOrFutureConfigurationFailsClosed() {
    assertTrue(FlowObservationConfiguration.parse(Map.of()).flows().isEmpty());
    assertThrows(BusinessException.class, () -> FlowObservationConfiguration.parse(Map.of("schemaVersion", 2, "flows", Map.of())));
    assertThrows(BusinessException.class, () -> FlowObservationConfiguration.parse("{broken"));
    assertThrows(BusinessException.class, () -> FlowObservationConfiguration.parse(Map.of("schemaVersion", 1, "flows", Map.of("A", Policy.off()))));
    var invalid = Map.of("schemaVersion", 1, "flows", Map.of("child", Map.of("monitorEnabled", true, "unknown", "value")));
    assertThrows(BusinessException.class, () -> FlowObservationConfiguration.parse(invalid));
  }

  @Test void aggregateMergesHistogramAndRetentionKeepsAudit() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var o = f.observation; long now = System.currentTimeMillis();
      var histogram = new java.util.ArrayList<>(java.util.Collections.nCopies(31, 0L)); histogram.set(3, 1L);
      var first = new Window("one", "child", "boot-1", "model-1", "policy-1", now, now, 1, 1, 0, histogram);
      o.store.save(first); o.store.save(first);
      histogram.set(3, 0L); histogram.set(5, 1L);
      o.store.save(new Window("two", "child", "boot-2", "model-2", "policy-2", now, now, 1, 0, 0, histogram));
      var metrics = o.service.metrics("child", 15, null, null);
      assertEquals(2, metrics.completed()); assertEquals(50, metrics.failureRate()); assertEquals(32, metrics.p95Millis());
      assertEquals(1, o.service.metrics("child", 15, null, "model-1").completed());
      o.store.save(new Run("old", "child", "boot-1", null, null, "model-1", "policy-1", now - 7_200_000, 3, "failed", "Failure", false, List.of()));
      assertEquals(1, o.store.runs("child", 0).size());
      o.store.cleanup(now, 24, 1); assertTrue(o.store.runs("child", 0).isEmpty());
      assertEquals(2, o.store.windows("child", 0).size());
    }
  }

  @Test void observationHttpPermissionAndStrictCommandBoundary() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var mvc = RuntimeFlowHttpTest.mvc(f); String url = RuntimeFlowHttpTest.BASE + "/models/child";
      mvc.perform(get(url + "/observation-policy")).andExpect(status().isUnauthorized());
      CurrentSecurityContextHolder.withContextThrowing(RuntimeFlowHttpTest.identity("reader", Set.of(RuntimeFlowAccess.READ)), () -> {
        mvc.perform(get(url + "/observation-policy")).andExpect(status().isOk()).andExpect(jsonPath("$.data.policy.monitorEnabled").value(false));
        mvc.perform(get(url + "/metrics")).andExpect(status().isForbidden());
        mvc.perform(put(url + "/observation-policy").contentType("application/json").content("{}")) .andExpect(status().isForbidden());
        mvc.perform(get(url + "/observation-status")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("child")).andExpect(jsonPath("$.data.expectedRevision").isNotEmpty());
        mvc.perform(get(url + "/operations-view")).andExpect(status().isOk()); return null;
      });
      CurrentSecurityContextHolder.withContextThrowing(RuntimeFlowHttpTest.identity("operator", OPERATOR), () -> {
        var revision = f.observation.service.read("child").revision();
        mvc.perform(put(url + "/observation-policy").contentType("application/json").content(Json.toJsonString(command(revision, true, true))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(put(url + "/observation-policy").contentType("application/json").content(Json.toJsonString(command(revision, true, false))))
            .andExpect(status().isConflict());
        mvc.perform(put(url + "/observation-policy").contentType("application/json").content("{\"application\":\"foreign\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get(url + "/metrics")).andExpect(status().isOk()).andExpect(jsonPath("$.data.collected").value(false));
        mvc.perform(get(url + "/diagnostic-runs")).andExpect(status().isOk());
        mvc.perform(get(url + "/diagnostic-runs/missing")).andExpect(status().isNotFound());
        mvc.perform(get(url + "/observation-changes")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get(RuntimeFlowHttpTest.BASE + "/models/missing/observation-policy")).andExpect(status().isNotFound());
        return null;
      });
    }
  }
}
