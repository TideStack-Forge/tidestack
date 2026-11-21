package com.ouroboros.devhelper.metadata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.devhelper.command.CommandResult;

public final class MetadataRefreshProtocol {
  public static final int CURRENT_SCHEMA_VERSION = 2;
  public static final String SCHEMA_VERSION_KEY = "metadataSnapshotSchemaVersion";
  public static final String HELPER_INSTANCE_ID_KEY = "helperInstanceId";
  public static final String HELPER_STARTED_AT_EPOCH_MILLIS_KEY = "helperStartedAtEpochMillis";
  public static final String APP_INCARNATION_ID_KEY = "appIncarnationId";
  public static final String APP_INCARNATION_ID_ENV = "OUROBOROS_APP_INCARNATION_ID";
  public static final String REFRESH_OPERATION_ID_KEY = "refreshOperationId";

  private MetadataRefreshProtocol() {
  }

  public static void addHelperIdentity(Map<String, Object> target, HelperIdentity identity) {
    target.put(SCHEMA_VERSION_KEY, Integer.valueOf(CURRENT_SCHEMA_VERSION));
    target.put(HELPER_INSTANCE_ID_KEY, identity.getInstanceId());
    target.put(HELPER_STARTED_AT_EPOCH_MILLIS_KEY, Long.valueOf(identity.getStartedAtEpochMillis()));
    target.put(APP_INCARNATION_ID_KEY, identity.getAppIncarnationId());
  }

  public static Map<String, Object> refreshParameters(
      Long runtimeGeneration,
      HelperIdentity identity,
      String refreshOperationId
  ) {
    Map<String, Object> params = new LinkedHashMap<String, Object>();
    if (runtimeGeneration != null) {
      params.put("runtimeGeneration", runtimeGeneration);
    }
    addHelperIdentity(params, identity);
    params.put(REFRESH_OPERATION_ID_KEY, refreshOperationId);
    return params;
  }

  public static Optional<HelperIdentity> helperIdentity(CommandResult result) {
    if (result == null || !result.getSuccess() || !(result.getData() instanceof Map<?, ?> data)) {
      return Optional.empty();
    }
    Integer schemaVersion = integerValue(data.get(SCHEMA_VERSION_KEY));
    String instanceId = textValue(data.get(HELPER_INSTANCE_ID_KEY));
    Long startedAtEpochMillis = longValue(data.get(HELPER_STARTED_AT_EPOCH_MILLIS_KEY));
    String appIncarnationId = textValue(data.get(APP_INCARNATION_ID_KEY));
    if (!Integer.valueOf(CURRENT_SCHEMA_VERSION).equals(schemaVersion)
        || instanceId.isEmpty()
        || startedAtEpochMillis == null
        || startedAtEpochMillis.longValue() <= 0L
        || appIncarnationId.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new HelperIdentity(instanceId, startedAtEpochMillis.longValue(), appIncarnationId));
  }

  public static boolean canActivateHelper(HelperIdentity candidate, HelperIdentity current) {
    if (current == null) {
      return true;
    }
    if (!candidate.getAppIncarnationId().equals(current.getAppIncarnationId())) {
      return true;
    }
    if (candidate.getInstanceId().equals(current.getInstanceId())) {
      return candidate.getStartedAtEpochMillis() == current.getStartedAtEpochMillis();
    }
    return candidate.getStartedAtEpochMillis() > current.getStartedAtEpochMillis();
  }

  public static final class HelperRetirementFence {
    private static final HelperRetirementFence EMPTY = new HelperRetirementFence(null, 0L);
    private final String appIncarnationId;
    private final long retiredThroughStartedAtEpochMillis;

    private HelperRetirementFence(String appIncarnationId, long retiredThroughStartedAtEpochMillis) {
      this.appIncarnationId = appIncarnationId;
      this.retiredThroughStartedAtEpochMillis = retiredThroughStartedAtEpochMillis;
    }

    public static HelperRetirementFence empty() {
      return EMPTY;
    }

    public HelperRetirementFence retire(HelperIdentity identity) {
      if (identity == null) {
        return this;
      }
      long retiredThrough = identity.getAppIncarnationId().equals(appIncarnationId)
          ? Math.max(retiredThroughStartedAtEpochMillis, identity.getStartedAtEpochMillis())
          : identity.getStartedAtEpochMillis();
      return new HelperRetirementFence(identity.getAppIncarnationId(), retiredThrough);
    }

    public boolean rejects(HelperIdentity candidate) {
      return candidate != null
          && candidate.getAppIncarnationId().equals(appIncarnationId)
          && candidate.getStartedAtEpochMillis() <= retiredThroughStartedAtEpochMillis;
    }
  }

  public static boolean isCurrentSchema(Map<String, Object> params) {
    return params != null
        && Integer.valueOf(CURRENT_SCHEMA_VERSION).equals(integerValue(params.get(SCHEMA_VERSION_KEY)));
  }

  private static Integer integerValue(Object value) {
    if (value instanceof Number number) {
      return Integer.valueOf(number.intValue());
    }
    try {
      return value == null ? null : Integer.valueOf(value.toString());
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static Long longValue(Object value) {
    if (value instanceof Number number) {
      return Long.valueOf(number.longValue());
    }
    try {
      return value == null ? null : Long.valueOf(value.toString());
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static String textValue(Object value) {
    return value == null ? "" : value.toString().trim();
  }

  public static final class HelperIdentity {
    private final String instanceId;
    private final long startedAtEpochMillis;
    private final String appIncarnationId;

    public HelperIdentity(String instanceId, long startedAtEpochMillis, String appIncarnationId) {
      this.instanceId = instanceId;
      this.startedAtEpochMillis = startedAtEpochMillis;
      this.appIncarnationId = appIncarnationId;
    }

    public String getInstanceId() {
      return instanceId;
    }

    public long getStartedAtEpochMillis() {
      return startedAtEpochMillis;
    }

    public String getAppIncarnationId() {
      return appIncarnationId;
    }

    public boolean matches(
        String candidateInstanceId,
        long candidateStartedAtEpochMillis,
        String candidateAppIncarnationId
    ) {
      return instanceId.equals(candidateInstanceId)
          && startedAtEpochMillis == candidateStartedAtEpochMillis
          && appIncarnationId.equals(candidateAppIncarnationId);
    }
  }
}
