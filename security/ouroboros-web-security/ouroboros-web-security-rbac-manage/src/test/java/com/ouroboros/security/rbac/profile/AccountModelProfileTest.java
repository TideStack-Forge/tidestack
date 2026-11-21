package com.ouroboros.security.rbac.profile;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshingEvent;
import org.springframework.context.ApplicationContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;
import com.ouroboros.security.rbac.Application;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.service.UserService;

@SpringBootTest(classes = Application.class, properties = {
    "ouroboros.security.rbac.autoCreateUser.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:account_profile;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
class AccountModelProfileTest {
  @Autowired UserService users;
  @Autowired TypedDataModel<String, UserEntity> model;
  @Autowired ApplicationContext applicationContext;
  @Autowired javax.sql.DataSource dataSource;
  @Autowired AccountIdentityMigration migration;

  @org.junit.jupiter.api.BeforeEach
  void refreshModels() {
    if (com.ouroboros.data.model.TypedDataModelCenter.getDataModel(String.class, UserEntity.class).isEmpty()) {
      applicationContext.publishEvent(new OuroborosModelRefreshingEvent(applicationContext));
      applicationContext.publishEvent(new OuroborosModelRefreshedEvent(applicationContext));
    }
  }

  @Test
  void persistsUniqueSubjectsForSingleAndBatchWritesAndRejectsForgedProtectedValues() {
    var first = add(account("profile-model-a"));
    var others = asSystem(() -> users.add(List.of(account("profile-model-b"), account("profile-model-c"))));
    var subjects = new java.util.HashSet<String>();
    for (var user : List.of(first, others.get(0), others.get(1))) {
      var persisted = users.getByUserIdentity(user.getUserIdentity()).orElseThrow();
      assertTrue(AccountProtectionDataModelPlugin.validSubject(persisted.getPublicSubjectId()));
      assertTrue(subjects.add(persisted.getPublicSubjectId()));
      assertEquals(0L, persisted.getProfileRevision());
      assertFalse(persisted.getEmailVerified()); assertFalse(persisted.getPhoneNumberVerified());
    }
    assertTrue(model.update(first.getUserIdentity(), Map.of("emailVerified", true)).isFailure());
    assertTrue(model.update(first.getUserIdentity(), Map.of("publicSubjectId", java.util.UUID.randomUUID().toString())).isFailure());
    var forged = account("profile-model-forged"); forged.setPublicSubjectId(first.getPublicSubjectId());
    assertThrows(RuntimeException.class, () -> users.add(forged));
    assertTrue(users.getByUserIdentity("profile-model-forged").isEmpty());
  }

  @Test
  void databaseRevisionAllowsOnlyOneSaveAndPasswordChangePreservesProfile() {
    var inserted = add(account("profile-cas"));
    String subject = users.getByUserIdentity(inserted.getUserIdentity()).orElseThrow().getPublicSubjectId();
    CurrentSecurityContextHolder.withIdentity(SystemSecurityIdentities.system(), () -> {
      users.saveProfile(inserted.getUserIdentity(), subject, 0, Map.of("nickname", "第一次", "profileValues", Map.of("custom", "值")));
      assertThrows(RuntimeException.class, () -> users.saveProfile(inserted.getUserIdentity(), subject, 0, Map.of("nickname", "旧写入")));
      return null;
    });
    assertTrue(users.modifyPassword(inserted.getUserIdentity(), "replacement"));
    var persisted = users.getByUserIdentity(inserted.getUserIdentity()).orElseThrow();
    assertEquals("第一次", persisted.getNickname()); assertEquals(Map.of("custom", "值"), persisted.getProfileValues());
    assertEquals(subject, persisted.getPublicSubjectId()); assertEquals(1L, persisted.getProfileRevision());
  }

  @Test
  void nicknameUsesTheExistingProfileNameColumnWithoutLosingStoredValues() throws Exception {
    var inserted = add(account("profile-name-storage"));
    try (var connection = dataSource.getConnection(); var fixture = connection.createStatement()) {
      assertEquals(1, fixture.executeUpdate("UPDATE t_sys_user SET display_name = '原昵称' "
          + "WHERE user_identity = 'profile-name-storage'"));
    }
    var loaded = users.getByUserIdentity(inserted.getUserIdentity()).orElseThrow();
    assertEquals("原昵称", loaded.getNickname());
    asSystem(() -> {
      users.saveProfile(loaded.getUserIdentity(), loaded.getPublicSubjectId(), loaded.getProfileRevision(),
          Map.of("nickname", "新昵称"));
      return null;
    });
    assertEquals("新昵称", users.getByUserIdentity(loaded.getUserIdentity()).orElseThrow().getNickname());
    try (var connection = dataSource.getConnection(); var fixture = connection.createStatement();
        var row = fixture.executeQuery("SELECT display_name FROM t_sys_user WHERE user_identity = 'profile-name-storage'")) {
      assertTrue(row.next());
      assertEquals("新昵称", row.getString(1));
    }
  }

  @Test
  void migratesExistingAndLogicallyDeletedAccountsInResumableBatches() throws Exception {
    add(account("migration-active"));
    add(account("migration-deleted"));
    // Simulate rows from a schema installed before the new fields and insert protection.
    var physical = model.getDataModel();
    var station = assertInstanceOf(com.ouroboros.data.station.Migratable.class, physical.getDataStation());
    try (var connection = dataSource.getConnection(); var columns = connection.getMetaData()
        .getColumns(null, "PUBLIC", "T_SYS_USER", "PUBLIC_SUBJECT_ID")) {
      assertTrue(columns.next());
      assertEquals("NO", columns.getString("IS_NULLABLE"));
    }
    assertTrue(station.hasRequiredUniqueColumn(physical, "public_subject_id").get());
    try (var connection = dataSource.getConnection(); var fixture = connection.createStatement()) {
      fixture.executeUpdate("ALTER TABLE t_sys_user ALTER COLUMN public_subject_id DROP NOT NULL");
      fixture.executeUpdate("ALTER TABLE t_sys_user ALTER COLUMN profile_revision DROP NOT NULL");
      fixture.executeUpdate("ALTER TABLE t_sys_user ALTER COLUMN email_verified DROP NOT NULL");
      fixture.executeUpdate("ALTER TABLE t_sys_user ALTER COLUMN phone_number_verified DROP NOT NULL");
      assertEquals(2, fixture.executeUpdate("UPDATE t_sys_user SET public_subject_id = NULL, profile_revision = NULL, "
          + "email_verified = NULL, phone_number_verified = NULL WHERE user_identity LIKE 'migration-%'"));
      assertEquals(1, fixture.executeUpdate("UPDATE t_sys_user SET is_deleted = TRUE WHERE user_identity = 'migration-deleted'"));
    }
    assertTrue(station.migrate().isRight());
    assertFalse(station.hasRequiredUniqueColumn(physical, "public_subject_id").get());
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> migration.batch("migration-", 1));
    var first = asSystem(() -> migration.batch("migration-", 1));
    assertEquals("migration-active", first.get("lastIdentity"));
    assertEquals(1, first.get("updated"));
    var second = asSystem(() -> migration.batch((String) first.get("lastIdentity"), 1));
    assertEquals("migration-deleted", second.get("lastIdentity"));
    assertEquals(1, second.get("updated"));
    var active = users.getByUserIdentity("migration-active").orElseThrow();
    assertTrue(AccountProtectionDataModelPlugin.validSubject(active.getPublicSubjectId()));
    assertEquals(0L, active.getProfileRevision());
    assertFalse(active.getEmailVerified());
    assertFalse(active.getPhoneNumberVerified());
    assertTrue(users.getByUserIdentity("migration-deleted").isEmpty());
    assertEquals(0, asSystem(() -> migration.batch("migration-", 2)).get("updated"));
    assertEquals(active.getPublicSubjectId(),
        users.getByUserIdentity("migration-active").orElseThrow().getPublicSubjectId());
    assertTrue(station.migrate().isRight());
    assertTrue(station.hasRequiredUniqueColumn(physical, "public_subject_id").get());
  }

  @Test
  void fillsMissingProfileStateWithoutReplacingAnExistingPublicSubject() throws Exception {
    add(account("profile-state-only"));
    String subject = users.getByUserIdentity("profile-state-only").orElseThrow().getPublicSubjectId();
    try (var connection = dataSource.getConnection(); var fixture = connection.createStatement()) {
      fixture.executeUpdate("ALTER TABLE t_sys_user ALTER COLUMN profile_revision DROP NOT NULL");
      fixture.executeUpdate("ALTER TABLE t_sys_user ALTER COLUMN email_verified DROP NOT NULL");
      fixture.executeUpdate("ALTER TABLE t_sys_user ALTER COLUMN phone_number_verified DROP NOT NULL");
      fixture.executeUpdate("UPDATE t_sys_user SET profile_revision = NULL, email_verified = NULL, "
          + "phone_number_verified = NULL WHERE user_identity = 'profile-state-only'");
    }
    var result = asSystem(() -> migration.batch("profile-state-onlx", 1));
    assertEquals(1, result.get("updated"));
    var persisted = users.getByUserIdentity("profile-state-only").orElseThrow();
    assertEquals(subject, persisted.getPublicSubjectId());
    assertEquals(0L, persisted.getProfileRevision());
    assertFalse(persisted.getEmailVerified());
    assertFalse(persisted.getPhoneNumberVerified());
    try (var connection = dataSource.getConnection(); var fixture = connection.createStatement();
        var row = fixture.executeQuery("SELECT email_verified IS NULL, phone_number_verified IS NULL "
            + "FROM t_sys_user WHERE user_identity = 'profile-state-only'")) {
      assertTrue(row.next());
      assertFalse(row.getBoolean(1));
      assertFalse(row.getBoolean(2));
    }
    assertEquals(0, asSystem(() -> migration.batch("profile-state-onlx", 1)).get("updated"));
  }

  private UserEntity add(UserEntity account) { return asSystem(() -> users.add(account)); }

  private <T> T asSystem(java.util.function.Supplier<T> action) {
    try { return CurrentSecurityContextHolder.withIdentity(SystemSecurityIdentities.system(), action); }
    catch (com.ouroboros.exception.ValidationException error) { throw new AssertionError(error.getErrors().toString(), error); }
  }

  private UserEntity account(String identity) {
    var account = new UserEntity(); account.setUserIdentity(identity); account.setPassword("initial-password"); return account;
  }
}
