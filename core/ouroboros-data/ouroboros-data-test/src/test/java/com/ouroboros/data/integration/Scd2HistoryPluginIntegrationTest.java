package com.ouroboros.data.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.data.TestApplication;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.model.Scd2HistoryTestModels;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.data.station.DataStation;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.station.StaticDataStationProvider;

@SpringBootTest(classes = {
    TestApplication.class,
    Scd2HistoryPluginIntegrationTest.Scd2HistoryTestConfiguration.class
})
@ActiveProfiles("scd2-history")
@Transactional
class Scd2HistoryPluginIntegrationTest {

  private static final Map<String, DataStation<?>> ORIGINAL_STATIONS = captureRegisteredStations();
  private static final Object ORIGINAL_DATA_MODEL_MAP = captureDataModelMap();

  @Autowired
  @Qualifier("scd2HistoryDataStation")
  private DataStation<?> scd2HistoryDataStation;

  @AfterAll
  static void tearDownClass() throws IllegalAccessException {
    restoreRegisteredStations(ORIGINAL_STATIONS);
    getDataModelMapField().set(null, ORIGINAL_DATA_MODEL_MAP);
  }

  @Test
  void insertShouldCreateCurrentHistoryRow() {
    Record inserted = userWithHistory().insert(record(
        "name", "Alice",
        "status", "ACTIVE",
        "updatedAt", LocalDateTime.parse("2026-03-18T08:00:00")
    )).get();

    Long businessId = asLong(inserted.get("ID"));
    RecordList historyRows = historyRows();

    assertEquals(1, historyRows.size());
    assertNotNull(field(historyRows.get(0), "id"));
    assertEquals(businessId, asLong(field(historyRows.get(0), "businessKey")));
    assertEquals("INSERT", field(historyRows.get(0), "opType"));
    assertEquals(Boolean.TRUE, field(historyRows.get(0), "isCurrent"));
  }

  @Test
  void updatingOnlyIgnoredFieldShouldNotCreateNewHistoryRow() {
    Record inserted = userWithHistory().insert(record(
        "name", "Alice",
        "status", "ACTIVE",
        "updatedAt", LocalDateTime.parse("2026-03-18T08:00:00")
    )).get();

    userWithHistory().update(
        Collections.<String, Object>singletonMap("id", inserted.get("ID")),
        Collections.<String, Object>singletonMap("updatedAt", LocalDateTime.parse("2026-03-18T09:00:00"))
    ).get();

    assertEquals(1, historyRows().size());
  }

  @Test
  void updatingBusinessFieldShouldCloseOldVersionAndInsertNewVersion() {
    Record inserted = userWithHistory().insert(record(
        "name", "Alice",
        "status", "ACTIVE",
        "updatedAt", LocalDateTime.parse("2026-03-18T08:00:00")
    )).get();

    userWithHistory().update(
        Collections.<String, Object>singletonMap("id", inserted.get("ID")),
        record(
            "name", "Alice Renamed",
            "updatedAt", LocalDateTime.parse("2026-03-18T09:00:00")
        )
    ).get();

    RecordList historyRows = historyRows();
    assertEquals(2, historyRows.size());
    assertEquals(Boolean.FALSE, field(historyRows.get(0), "isCurrent"));
    assertNotNull(field(historyRows.get(0), "validTo"));
    assertEquals(Boolean.TRUE, field(historyRows.get(1), "isCurrent"));
    assertEquals("UPDATE", field(historyRows.get(1), "opType"));
    assertEquals("Alice Renamed", field(historyRows.get(1), "name"));
  }

  @Test
  void deletingRecordShouldCreateDeleteHistoryAndClearPreviousCurrentFlag() {
    Record inserted = userWithHistory().insert(record(
        "name", "Alice",
        "status", "ACTIVE",
        "updatedAt", LocalDateTime.parse("2026-03-18T08:00:00")
    )).get();

    userWithHistory().delete(Collections.<String, Object>singletonMap("id", inserted.get("ID"))).get();

    RecordList historyRows = historyRows();
    assertEquals(2, historyRows.size());
    assertEquals(Boolean.FALSE, field(historyRows.get(0), "isCurrent"));
    assertEquals("DELETE", field(historyRows.get(1), "opType"));
  }

  @Test
  void insertShouldReturnSourceRecordAfterHistoryIdsDrift() {
    Record deletedRecord = userWithHistory().insert(record(
        "name", "Alice",
        "status", "ACTIVE",
        "updatedAt", LocalDateTime.parse("2026-03-18T08:00:00")
    )).get();
    userWithHistory().delete(Collections.<String, Object>singletonMap("id", deletedRecord.get("ID"))).get();

    Record inserted = userWithHistory().insert(record(
        "name", "Bob",
        "status", "ACTIVE",
        "updatedAt", LocalDateTime.parse("2026-03-18T09:00:00")
    )).get();

    Record currentBobHistory = historyRows().stream()
        .filter(record -> Boolean.TRUE.equals(field(record, "isCurrent")))
        .filter(record -> "Bob".equals(field(record, "name")))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("current Bob history row not found"));

    assertEquals(asLong(field(currentBobHistory, "businessKey")), asLong(inserted.get("ID")));
    assertTrue(!asLong(field(currentBobHistory, "id")).equals(asLong(inserted.get("ID"))));
  }

  @Test
  void updatingBusinessFieldShouldPersistDiffWhenStoreDiffEnabled() {
    Record inserted = userWithDiffHistory().insert(record(
        "name", "Alice",
        "status", "ACTIVE",
        "updatedAt", LocalDateTime.parse("2026-03-18T08:00:00")
    )).get();

    userWithDiffHistory().update(
        Collections.<String, Object>singletonMap("id", inserted.get("ID")),
        record(
            "name", "Alice Renamed",
            "updatedAt", LocalDateTime.parse("2026-03-18T09:00:00")
        )
    ).get();

    Record latestHistory = diffHistoryRows().get(1);
    assertTrue(String.valueOf(field(latestHistory, "changedFields")).contains("name"));
    assertTrue(String.valueOf(field(latestHistory, "changeSet")).contains("Alice"));
    assertTrue(String.valueOf(field(latestHistory, "changeSet")).contains("Alice Renamed"));
  }

  @Test
  void batchUpdatingMultipleRecordsShouldCreateHistoryVersionsForEachAffectedRecord() {
    userWithHistory().insert(record(
        "name", "Alice",
        "status", "ACTIVE",
        "updatedAt", LocalDateTime.parse("2026-03-18T08:00:00")
    )).get();
    userWithHistory().insert(record(
        "name", "Bob",
        "status", "ACTIVE",
        "updatedAt", LocalDateTime.parse("2026-03-18T08:05:00")
    )).get();

    userWithHistory().update(
        Collections.<String, Object>singletonMap("status", "ACTIVE"),
        record(
            "name", "Batch Renamed",
            "updatedAt", LocalDateTime.parse("2026-03-18T09:00:00")
        )
    ).get();

    RecordList historyRows = historyRows();
    assertEquals(4, historyRows.size());
    assertEquals(2L, historyRows.stream().filter(record -> Boolean.TRUE.equals(field(record, "isCurrent"))).count());
    assertEquals(2L, historyRows.stream().filter(record -> "UPDATE".equals(field(record, "opType"))).count());
  }

  @Test
  void batchDeletingMultipleRecordsShouldCreateDeleteHistoryForEachAffectedRecord() {
    userWithHistory().insert(record(
        "name", "Alice",
        "status", "ACTIVE",
        "updatedAt", LocalDateTime.parse("2026-03-18T08:00:00")
    )).get();
    userWithHistory().insert(record(
        "name", "Bob",
        "status", "ACTIVE",
        "updatedAt", LocalDateTime.parse("2026-03-18T08:05:00")
    )).get();

    userWithHistory().delete(Collections.<String, Object>singletonMap("status", "ACTIVE")).get();

    RecordList historyRows = historyRows();
    assertEquals(4, historyRows.size());
    assertEquals(2L, historyRows.stream().filter(record -> "DELETE".equals(field(record, "opType"))).count());
    assertEquals(2L, historyRows.stream().filter(record -> Boolean.TRUE.equals(field(record, "isCurrent"))).count());
    assertEquals(2L, historyRows.stream().filter(record -> Boolean.FALSE.equals(field(record, "isCurrent"))).count());
  }

  private DataModel userWithHistory() {
    return scd2HistoryDataStation.getDataModel(Scd2HistoryTestModels.USER_WITH_HISTORY)
        .orElseThrow(() -> new IllegalArgumentException("model not found"));
  }

  private DataModel userWithHistoryHistory() {
    return scd2HistoryDataStation.getDataModel(Scd2HistoryTestModels.USER_WITH_HISTORY_HISTORY)
        .orElseThrow(() -> new IllegalArgumentException("history model not found"));
  }

  private DataModel userWithDiffHistory() {
    return scd2HistoryDataStation.getDataModel(Scd2HistoryTestModels.USER_WITH_DIFF_HISTORY)
        .orElseThrow(() -> new IllegalArgumentException("diff model not found"));
  }

  private DataModel userWithDiffHistoryHistory() {
    return scd2HistoryDataStation.getDataModel(Scd2HistoryTestModels.USER_WITH_DIFF_HISTORY_HISTORY)
        .orElseThrow(() -> new IllegalArgumentException("diff history model not found"));
  }

  private RecordList historyRows() {
    Map<String, Object> query = new LinkedHashMap<String, Object>();
    query.put("select", Arrays.asList("*"));
    query.put("order", Arrays.asList(Collections.singletonMap("id", "ASC")));
    return userWithHistoryHistory().query(query).get();
  }

  private RecordList diffHistoryRows() {
    Map<String, Object> query = new LinkedHashMap<String, Object>();
    query.put("select", Arrays.asList("*"));
    query.put("order", Arrays.asList(Collections.singletonMap("id", "ASC")));
    return userWithDiffHistoryHistory().query(query).get();
  }

  private Map<String, Object> record(Object... pairs) {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) {
      data.put((String) pairs[i], pairs[i + 1]);
    }
    return data;
  }

  private Long asLong(Object value) {
    if (value == null) {
      return null;
    }
    return ((Number) value).longValue();
  }

  private Object field(Record record, String fieldName) {
    if (record.containsKey(fieldName)) {
      return record.get(fieldName);
    }
    String rawName = toRawName(fieldName);
    if (record.containsKey(rawName)) {
      return record.get(rawName);
    }
    String upperRawName = rawName.toUpperCase();
    if (record.containsKey(upperRawName)) {
      return record.get(upperRawName);
    }
    String upperName = fieldName.toUpperCase();
    if (record.containsKey(upperName)) {
      return record.get(upperName);
    }
    return record.get(fieldName);
  }

  private String toRawName(String fieldName) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < fieldName.length(); i++) {
      char current = fieldName.charAt(i);
      if (Character.isUpperCase(current) && i > 0) {
        builder.append('_');
      }
      builder.append(Character.toLowerCase(current));
    }
    return builder.toString();
  }

  @TestConfiguration
  @Profile("scd2-history")
  static class Scd2HistoryTestConfiguration {
    private static final String STATION_NAME = "test-scd2-history";

    @Bean(name = "scd2HistoryDataStation")
    public DataStation<?> scd2HistoryDataStation(DataSource dataSource) {
      SqlDataStation station = new SqlDataStation(
          STATION_NAME,
          dataSource,
          Scd2HistoryTestModels.allMetas(),
          MigrationStrategy.AUTO
      );
      station.migrate();
      return station;
    }

    @Bean
    public String scd2HistoryDataStationRegistration(@Qualifier("scd2HistoryDataStation") DataStation<?> scd2HistoryDataStation) {
      StaticDataStationProvider.register(STATION_NAME, scd2HistoryDataStation);
      DataModelCenter.refresh();
      return STATION_NAME;
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, DataStation<?>> captureRegisteredStations() {
    try {
      Field field = StaticDataStationProvider.class.getDeclaredField("stations");
      field.setAccessible(true);
      return new HashMap<String, DataStation<?>>((Map<String, DataStation<?>>) field.get(null));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("failed to capture StaticDataStationProvider state", e);
    }
  }

  private static void restoreRegisteredStations(Map<String, DataStation<?>> stations) throws IllegalAccessException {
    Map<String, DataStation<?>> registeredStations = registeredStations();
    registeredStations.clear();
    registeredStations.putAll(stations);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, DataStation<?>> registeredStations() throws IllegalAccessException {
    try {
      Field field = StaticDataStationProvider.class.getDeclaredField("stations");
      field.setAccessible(true);
      return (Map<String, DataStation<?>>) field.get(null);
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException("failed to access StaticDataStationProvider state", e);
    }
  }

  private static Object captureDataModelMap() {
    try {
      return getDataModelMapField().get(null);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("failed to capture DataModelCenter state", e);
    }
  }

  private static Field getDataModelMapField() {
    try {
      Field field = DataModelCenter.class.getDeclaredField("dataModelMap");
      field.setAccessible(true);
      return field;
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException("failed to access DataModelCenter state", e);
    }
  }
}
