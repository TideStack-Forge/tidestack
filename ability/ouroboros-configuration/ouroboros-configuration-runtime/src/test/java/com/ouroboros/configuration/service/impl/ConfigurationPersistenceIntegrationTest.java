package com.ouroboros.configuration.service.impl;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.configuration.ConfigurationRevision;
import com.ouroboros.configuration.center.ConfigurationMetadataCenter;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.model.RuntimeConfigurationValueModel;
import com.ouroboros.data.model.BaseTypedDataModel;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

/** Original typed persistence, transaction and Spring cache contracts, including a shared Redis deployment. */
class ConfigurationPersistenceIntegrationTest {
  private static final String KEY = "notification.integration.host";
  private Map<String, ConfigurationMetadata> previous;
  private ConfigurationMetadata metadata;

  @BeforeEach
  void installDefinition() {
    previous = ConfigurationMetadataCenter.getConfigurationMetadataMap();
    metadata = new ConfigurationMetadata();
    metadata.setKey(KEY); metadata.setModifiable(true); metadata.setNullable(true);
    metadata.setValueType(ConfigValueType.STRING);
    metadata.setExtraProp("restoreDefaultRemovesOverride", true);
    ReflectionTestUtils.setField(ConfigurationMetadataCenter.class, "metadataMap", Map.of(KEY, metadata));
  }
  @AfterEach
  void restoreDefinition() { ReflectionTestUtils.setField(ConfigurationMetadataCenter.class, "metadataMap", previous); }

  @Test
  void lockingAConfigurationPreservesTheFullRevisionTokenIncludingItsGeneration() {
    var fixture = new Fixture(false);
    try (var context = fixture.context(new ConcurrentMapCacheManager())) {
      var runtime = context.getBean(RuntimeConfigurationServiceImpl.class);
      var values = context.getBean(ConfigurationValueServiceImpl.class);
      var created = runtime.updateManaged(Map.of(KEY, new ConfigurationMutation.Replace("first")), "operator",
          Map.of(KEY, ConfigurationRevision.defaultRevision(KEY, null))).get(KEY);
      var tx = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
      tx.executeWithoutResult(status -> {
        var locked = values.getForUpdate(KEY);
        assertNotNull(locked.getGeneration(), "Locking must retain the row incarnation used by management CAS");
        assertEquals(created.getRevision(), ConfigurationRevision.storedRevision(
            locked.getKey(), locked.getValue(), locked.getRevision(), locked.getGeneration()));
        assertEquals(values.get(KEY).getGeneration(), locked.getGeneration());
      });
    }
  }

  @Test
  void realConditionalDeletesRejectStaleTokensAndRecreatedRowsAndRollbackAtomically() {
    var fixture = new Fixture(false);
    try (var context = fixture.context(new ConcurrentMapCacheManager())) {
      var values = context.getBean(ConfigurationValueServiceImpl.class);
      var runtime = context.getBean(RuntimeConfigurationServiceImpl.class);
      var created = runtime.updateManaged(Map.of(KEY, new ConfigurationMutation.Replace("first")), "operator",
          Map.of(KEY, ConfigurationRevision.defaultRevision(KEY, null))).get(KEY);
      var updated = runtime.updateManaged(Map.of(KEY, new ConfigurationMutation.Replace("second")), "operator",
          Map.of(KEY, created.getRevision())).get(KEY);
      assertFalse(values.removeOverride(KEY, created.getRevision(), "unused"));
      var tx = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
      tx.executeWithoutResult(status -> {
        assertTrue(values.removeOverride(KEY, updated.getRevision(), "unused"));
        status.setRollbackOnly();
      });
      assertEquals("second", values.get(KEY).getValue());
      runtime.updateManaged(Map.of(KEY, ConfigurationMutation.RestoreDefaults.INSTANCE), "operator", Map.of(KEY, updated.getRevision()));
      assertNull(values.get(KEY));
      var recreated = runtime.updateManaged(Map.of(KEY, new ConfigurationMutation.Replace("first")), "operator",
          Map.of(KEY, ConfigurationRevision.defaultRevision(KEY, null))).get(KEY);
      assertNotEquals(created.getRevision(), recreated.getRevision());
      assertThrows(BusinessException.class, () -> runtime.updateManaged(Map.of(KEY, new ConfigurationMutation.Replace("stale")),
          "operator", Map.of(KEY, created.getRevision())));
      assertFalse(values.removeOverride(KEY, created.getRevision(), "unused"));
    }
  }

  @Test
  void aConflictOnTheSecondKeyRollsBackTheFirstKeyAndPreservesBothRevisions() {
    String secondKey = "notification.integration.secret";
    var secondMetadata = new ConfigurationMetadata();
    secondMetadata.setKey(secondKey); secondMetadata.setModifiable(true); secondMetadata.setNullable(true);
    secondMetadata.setValueType(ConfigValueType.STRING);
    ReflectionTestUtils.setField(ConfigurationMetadataCenter.class, "metadataMap", Map.of(KEY, metadata, secondKey, secondMetadata));
    var fixture = new Fixture(false);
    try (var context = fixture.context(new ConcurrentMapCacheManager())) {
      var values = context.getBean(ConfigurationValueServiceImpl.class);
      var runtime = context.getBean(RuntimeConfigurationServiceImpl.class);
      var initial = runtime.updateManaged(Map.of(KEY, new ConfigurationMutation.Replace("connection-v1"),
          secondKey, new ConfigurationMutation.Replace("credential-v1")), "operator",
          Map.of(KEY, ConfigurationRevision.defaultRevision(KEY, null),
              secondKey, ConfigurationRevision.defaultRevision(secondKey, null)));
      var peer = runtime.updateManaged(Map.of(secondKey, new ConfigurationMutation.Replace("credential-v2")), "peer",
          Map.of(secondKey, initial.get(secondKey).getRevision())).get(secondKey);
      assertEquals("connection-v1", runtime.getRequired(KEY));
      var connectionRevision = initial.get(KEY).getRevision();
      assertThrows(BusinessException.class, () -> runtime.updateManaged(Map.of(KEY, new ConfigurationMutation.Replace("connection-v3"),
          secondKey, new ConfigurationMutation.Replace("credential-v3")), "operator",
          Map.of(KEY, connectionRevision, secondKey, initial.get(secondKey).getRevision())));
      assertEquals("connection-v1", values.get(KEY).getValue());
      assertEquals(connectionRevision, runtime.getSnapshot(KEY).orElseThrow().revision());
      assertEquals("credential-v2", values.get(secondKey).getValue());
      assertEquals(peer.getRevision(), runtime.getSnapshot(secondKey).orElseThrow().revision());
      assertEquals("connection-v1", runtime.getRequired(KEY));
      assertEquals("credential-v2", runtime.getRequired(secondKey));
      runtime.updateManaged(Map.of(KEY, new ConfigurationMutation.Replace("connection-v3"),
          secondKey, new ConfigurationMutation.Replace("credential-v3")), "operator",
          Map.of(KEY, connectionRevision, secondKey, peer.getRevision()));
      assertEquals("connection-v3", values.get(KEY).getValue());
      assertEquals("credential-v3", values.get(secondKey).getValue());
      assertNotEquals(connectionRevision, runtime.getSnapshot(KEY).orElseThrow().revision());
      assertNotEquals(peer.getRevision(), runtime.getSnapshot(secondKey).orElseThrow().revision());
    }
  }

  @Test
  void modelDrivenMigrationPreservesLegacyTokensAndNullDefaultsFollowNewDeployments() {
    var fixture = new Fixture(true);
    try (var context = fixture.context(new ConcurrentMapCacheManager())) {
      var runtime = context.getBean(RuntimeConfigurationServiceImpl.class);
      var values = context.getBean(ConfigurationValueServiceImpl.class);
      assertEquals("3", runtime.getSnapshot(KEY).orElseThrow().revision());
      var restored = runtime.updateManaged(Map.of(KEY, ConfigurationMutation.RestoreDefaults.INSTANCE), "operator", Map.of(KEY, "3")).get(KEY);
      assertNull(restored.getValue()); assertNull(values.get(KEY));
      metadata.setDefaultValue("next-deployment.invalid"); runtime.clearCache();
      assertEquals("next-deployment.invalid", runtime.getRequired(KEY));
      assertTrue(runtime.getRevision(KEY).orElseThrow().startsWith("default-"));
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "notification.redis.port", matches = "[0-9]+")
  void twoApplicationContextsObserveChangesAndRestorationThroughTheOriginalSharedCache() {
    var fixture = new Fixture(false);
    var factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getProperty("notification.redis.port")));
    factory.afterPropertiesSet();
    String prefix = "notification-config-it-" + UUID.randomUUID() + ":";
    var cacheConfig = RedisCacheConfiguration.defaultCacheConfig().computePrefixWith(name -> prefix + name + ":")
        .entryTtl(java.time.Duration.ofSeconds(30));
    var firstCache = RedisCacheManager.builder(factory).cacheDefaults(cacheConfig).build(); firstCache.afterPropertiesSet();
    var secondCache = RedisCacheManager.builder(factory).cacheDefaults(cacheConfig).build(); secondCache.afterPropertiesSet();
    try (var first = fixture.context(firstCache); var second = fixture.context(secondCache)) {
      var a = first.getBean(RuntimeConfigurationServiceImpl.class);
      var b = second.getBean(RuntimeConfigurationServiceImpl.class);
      assertTrue(b.get(KEY).isEmpty()); // Warm the peer through the same @Cacheable entry used by senders.
      var saved = a.updateManaged(Map.of(KEY, new ConfigurationMutation.Replace("cluster.invalid")), "operator",
          Map.of(KEY, ConfigurationRevision.defaultRevision(KEY, null))).get(KEY);
      assertEquals("cluster.invalid", b.getRequired(KEY));
      assertEquals(saved.getRevision(), b.getRevision(KEY).orElseThrow());
      a.updateManaged(Map.of(KEY, ConfigurationMutation.RestoreDefaults.INSTANCE), "operator", Map.of(KEY, saved.getRevision()));
      assertTrue(b.get(KEY).isEmpty());
      assertEquals(ConfigurationRevision.defaultRevision(KEY, null), b.getRevision(KEY).orElseThrow());
    } finally {
      firstCache.getCache("ouroboros:configuration").clear();
      factory.destroy();
    }
  }

  @EnableCaching(proxyTargetClass = true)
  @EnableTransactionManagement(proxyTargetClass = true)
  static class Services {
    @Bean ConfigurationValueServiceImpl values() { return new ConfigurationValueServiceImpl(); }
    @Bean RuntimeConfigurationServiceImpl runtime() { return new RuntimeConfigurationServiceImpl(); }
  }

  private static class Fixture {
    final DataSource source;
    final BaseTypedDataModel<String, RuntimeConfigurationValueModel> model;
    Fixture(boolean legacy) {
      String mysql = System.getenv("OUROBOROS_BEHAVIOR_MYSQL_URL");
      boolean useMySql = mysql != null && !mysql.isBlank();
      if (useMySql) source = new DriverManagerDataSource(mysql, System.getenv("OUROBOROS_BEHAVIOR_MYSQL_USER"),
          System.getenv("OUROBOROS_BEHAVIOR_MYSQL_PASSWORD"));
      else {
        var h2 = new JdbcDataSource(); h2.setUser("sa");
        h2.setURL("jdbc:h2:mem:configuration_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"); source = h2;
      }
      var meta = TypedModelMetaHelper.scanModelMate("com.ouroboros.configuration.model").get("RuntimeConfigurationValueModel");
      meta.setRawName("t_config_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
      var fields = meta.getFields();
      if (legacy) meta.setFields(fields.stream().filter(field -> !field.getName().equals("generation")).toList());
      var jdbc = new JdbcTemplate(source);
      var station = new SqlDataStation("configuration-it", source, List.of(meta), MigrationStrategy.AUTO, useMySql ? "MySQL" : "H2");
      station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
      if (legacy) {
        station.getDataModelList().getFirst().insert(Map.of("key", KEY, "value", "legacy", "revision", 3L,
            "updateBy", "operator", "updateAt", java.time.LocalDateTime.now())).get();
        meta.setFields(fields);
        station = new SqlDataStation("configuration-it", source, List.of(meta), MigrationStrategy.AUTO, useMySql ? "MySQL" : "H2");
        station.generateMigrationSql(station.getDataModelList()).get().forEach(jdbc::execute);
      }
      model = new BaseTypedDataModel<>(station.getDataModelList().getFirst(), RuntimeConfigurationValueModel.class);
    }
    AnnotationConfigApplicationContext context(CacheManager cache) {
      var context = new AnnotationConfigApplicationContext();
      context.registerBean("dataModel", BaseTypedDataModel.class, () -> model);
      context.registerBean("transactionManager", DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(source));
      context.registerBean("cacheManager", CacheManager.class, () -> cache);
      context.register(Services.class); context.refresh();
      return context;
    }
  }
}
