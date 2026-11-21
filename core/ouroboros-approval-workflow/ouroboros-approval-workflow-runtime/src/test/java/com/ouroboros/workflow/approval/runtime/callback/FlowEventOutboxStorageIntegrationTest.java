package com.ouroboros.workflow.approval.runtime.callback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.workflow.approval.event.FlowEventOutbox;
import com.ouroboros.workflow.approval.event.FlowEventOutboxService;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;

/** Existing approval responsibilities keep their identity and leases when the schema is checked again. */
@EnabledIfEnvironmentVariable(named = "OUROBOROS_BEHAVIOR_MYSQL_URL", matches = ".+")
class FlowEventOutboxStorageIntegrationTest {
  @Test
  void pendingAndExpiredApprovalFactsSurviveMigrationWithoutRecreationOrDuplicateDelivery() throws Exception {
    var source = new DriverManagerDataSource(System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL"),
        System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"), System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD"));
    var metadata = TypedModelMetaHelper.scanModelMate("com.ouroboros.workflow.approval.event").values().stream()
        .filter(model -> "FlowEventOutbox".equals(model.getFullName())).findFirst().orElseThrow();
    metadata.setRawName(metadata.getRawName() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
    var station = new SqlDataStation("approval-upgrade", source, java.util.List.of(metadata), MigrationStrategy.AUTO, "MySQL");
    var initialSql = station.generateMigrationSql(station.getDataModelList()).getOrElseThrow(error -> new IllegalStateException(error));
    assertFalse(initialSql.isEmpty());
    var jdbc = new JdbcTemplate(source);
    initialSql.forEach(jdbc::execute);
    var model = station.getDataModelList().getFirst();
    var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    try (var models = mockStatic(DataModelCenter.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
      models.when(() -> DataModelCenter.getDataModel(anyString())).thenAnswer(call ->
          "FlowEventOutbox".equals(call.getArgument(0)) ? Optional.of(model) : Optional.empty());
      models.when(DataModelCenter::hasSnapshot).thenReturn(true);
      var previous = new FlowEventOutboxService();
      FlowEventOutbox pending = transaction.execute(status -> previous.enqueue(event("approval-pending")));
      FlowEventOutbox expired = transaction.execute(status -> previous.enqueue(event("approval-expired")));
      var oldClaim = transaction.execute(status -> previous.claim(expired.getId(), "old-worker", 60).orElseThrow());
      model.update(expired.getId(), Map.of("claimUntil", LocalDateTime.now().minusMinutes(1))).get();

      var upgradeSql = station.generateMigrationSql(station.getDataModelList()).get();
      assertTrue(upgradeSql.isEmpty(), upgradeSql.toString());
      var restarted = new FlowEventOutboxService();
      assertEquals(FlowEventOutbox.STATUS_PENDING, restarted.findById(pending.getId()).orElseThrow().getStatus());
      assertEquals(oldClaim.claimToken(), restarted.findById(expired.getId()).orElseThrow().getClaimToken());
      assertEquals(pending.getEventJson(), restarted.findById(pending.getId()).orElseThrow().getEventJson());
      var claims = transaction.execute(status -> restarted.claimDue(10, "new-worker", 60));
      assertEquals(java.util.Set.of(pending.getId(), expired.getId()),
          claims.stream().map(claim -> claim.outbox().getId()).collect(java.util.stream.Collectors.toSet()));
      assertFalse(transaction.execute(status -> restarted.markSuccess(expired.getId(), oldClaim.claimToken())).booleanValue());
      for (var claim : claims) {
        assertTrue(transaction.execute(status -> restarted.markSuccess(claim.outbox().getId(), claim.claimToken())).booleanValue());
      }
      assertTrue(transaction.execute(status -> restarted.claimDue(10, "next-worker", 60)).isEmpty());
      assertEquals(2, model.count(Map.of()).get());
      assertEquals(2, model.count(Map.of("status", FlowEventOutbox.STATUS_SUCCESS)).get());
      assertEquals(pending.getEventFactId(), restarted.findById(pending.getId()).orElseThrow().getEventFactId());
      assertEquals(expired.getEventFactId(), restarted.findById(expired.getId()).orElseThrow().getEventFactId());
      String evidence = System.getProperty("ouroboros.behavior.migration.output");
      if (evidence != null) {
        Path output = Path.of(evidence);
        Files.createDirectories(output.getParent());
        var lines = new ArrayList<>(initialSql);
        lines.add("-- upgrade: no DDL; original pending/expired identities recovered and acknowledged once");
        Files.write(output, lines);
      }
    }
  }

  private static FlowInstanceEvent event(String id) {
    var event = new FlowInstanceEvent();
    event.setEventFactId(id);
    event.setFlowKey("approval-upgrade");
    event.setInstanceId(id + "-instance");
    event.setEventType(FlowInstanceEvent.EventType.APPROVED);
    return event;
  }
}
