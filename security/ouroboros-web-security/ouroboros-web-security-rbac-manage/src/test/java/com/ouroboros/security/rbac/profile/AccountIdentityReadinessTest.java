package com.ouroboros.security.rbac.profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.station.DataStation;
import com.ouroboros.data.station.Migratable;
import com.ouroboros.security.rbac.entity.UserEntity;
import io.vavr.control.Either;

class AccountIdentityReadinessTest {
  final AccountIdentityMigration migration = mock(AccountIdentityMigration.class);
  final TypedDataModel<String, UserEntity> users = mock();
  final DataModel model = mock();
  final DataStation<?> station = mock(DataStation.class, withSettings().extraInterfaces(Migratable.class));

  AccountIdentityReadiness readiness(boolean maintenance, boolean constrained) {
    when(users.getDataModel()).thenReturn(model);
    var field = mock(DataModelField.class);
    when(field.getRawName()).thenReturn("public_subject_id");
    when(model.getField("publicSubjectId")).thenReturn(Optional.of(field));
    doReturn(station).when(model).getDataStation();
    when(model.getFullName()).thenReturn("security.UserEntity");
    when(((Migratable) station).hasRequiredUniqueColumn(model, "public_subject_id")).thenReturn(Either.right(constrained));
    return new AccountIdentityReadiness(migration, users, maintenance);
  }

  @Test
  void freshSchemaCanIssueWithoutEnablingMaintenanceBackfill() {
    var gate = readiness(false, true);
    assertFalse(gate.isReady()); gate.verify(); assertTrue(gate.isReady());
    verifyNoInteractions(migration);
  }

  @Test
  void legacySchemaRequiresMaintenanceAndPhysicalConstraintConvergence() {
    var off = readiness(false, false); off.verify(); assertFalse(off.isReady());
    verifyNoInteractions(migration);
    when(migration.batch(null, 500)).thenReturn(Map.of("updated", 2, "hasMore", false));
    var pending = readiness(true, false); pending.verify(); assertFalse(pending.isReady());
    var complete = readiness(true, true); complete.verify(); assertTrue(complete.isReady());
  }

  @Test
  void failedBackfillCannotLeaveTheIssuanceGateOpen() {
    var gate = readiness(true, true);
    when(migration.batch(null, 500)).thenReturn(Map.of("updated", 0, "hasMore", false));
    gate.verify(); assertTrue(gate.isReady());
    when(migration.batch(null, 500)).thenThrow(new IllegalStateException("database unavailable"));
    gate.verify(); assertFalse(gate.isReady());
  }
}
