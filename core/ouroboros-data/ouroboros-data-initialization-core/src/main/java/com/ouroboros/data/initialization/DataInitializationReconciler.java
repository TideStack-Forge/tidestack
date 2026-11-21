package com.ouroboros.data.initialization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.ouroboros.util.Json;

public class DataInitializationReconciler {
  public static final String DEFAULT_LOCK_KEY = "data-init";

  private final List<InitializationPlanProvider> planProviders;
  private final TargetDatasetGateway targetDatasetGateway;
  private final InitializationLedger ledger;
  private final InitializationLock lock;
  private final InitializationTransaction transaction;

  public DataInitializationReconciler(List<InitializationPlanProvider> planProviders,
                                      TargetDatasetGateway targetDatasetGateway,
                                      InitializationLedger ledger,
                                      InitializationLock lock) {
    this(planProviders, targetDatasetGateway, ledger, lock, new NoOpInitializationTransaction());
  }

  public DataInitializationReconciler(List<InitializationPlanProvider> planProviders,
                                      TargetDatasetGateway targetDatasetGateway,
                                      InitializationLedger ledger,
                                      InitializationLock lock,
                                      InitializationTransaction transaction) {
    this.planProviders = List.copyOf(Objects.requireNonNull(planProviders, "planProviders"));
    this.targetDatasetGateway = Objects.requireNonNull(targetDatasetGateway, "targetDatasetGateway");
    this.ledger = Objects.requireNonNull(ledger, "ledger");
    this.lock = Objects.requireNonNull(lock, "lock");
    this.transaction = Objects.requireNonNull(transaction, "transaction");
  }

  public InitializationResult reconcile() {
    return reconcile(DEFAULT_LOCK_KEY);
  }

  public InitializationResult reconcile(String lockKey) {
    return lock.execute(lockKey, this::reconcileUnlocked);
  }

  private InitializationResult reconcileUnlocked() {
    InitializationResult result = InitializationResult.empty();
    for (InitializationPlanProvider provider : planProviders) {
      for (DataInitializationAsset asset : provider.loadPlans()) {
        result = result.withPlan();
        result = reconcilePlan(asset, result);
      }
    }
    return result;
  }

  private InitializationResult reconcilePlan(DataInitializationAsset asset, InitializationResult result) {
    DataInitializationPlan plan = asset.plan();
    for (InitializationBlock block : plan.blocks()) {
      LedgerKey key = ledgerKey(plan, block);
      InitializationRun run = ledger.begin(asset, block, key);
      try {
        InitializationBlockSummary summary = transaction.execute(() -> {
          InitializationBlockSummary blockSummary = applyBlock(block);
          ledger.succeed(run, blockSummary);
          return blockSummary;
        });
        result = result.withAppliedBlock(summary);
      } catch (RuntimeException ex) {
        ledger.fail(run, ex);
        throw ex;
      }
    }
    return result;
  }

  private LedgerKey ledgerKey(DataInitializationPlan plan, InitializationBlock block) {
    String assetVersion = block.version() == null ? plan.version() : block.version();
    return new LedgerKey(plan.id(), block.id(), block.targetModel(), assetVersion, hash(plan.id(), block, assetVersion));
  }

  private InitializationBlockSummary applyBlock(InitializationBlock block) {
    InitializationBlockSummary summary = new InitializationBlockSummary(0, 0);
    for (Map<String, Object> record : block.records()) {
      Map<String, Object> matchValues = matchValues(block, record);
      var existing = targetDatasetGateway.findOne(block.targetModel(), matchValues);

      if (existing.isPresent()) {
        summary = summary.plus(new InitializationBlockSummary(0, 1));
        continue;
      }

      targetDatasetGateway.insert(block.targetModel(), record);
      summary = summary.plus(new InitializationBlockSummary(1, 0));
    }
    return summary;
  }

  private Map<String, Object> matchValues(InitializationBlock block, Map<String, Object> record) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (String field : block.matchFields()) {
      if (!record.containsKey(field)) {
        throw new IllegalArgumentException(
            "初始化记录缺少匹配字段: targetModel=" + block.targetModel() + ", field=" + field);
      }
      values.put(field, record.get(field));
    }
    return values;
  }

  private String hash(String planId, InitializationBlock block, String assetVersion) {
    Map<String, Object> material = new LinkedHashMap<>();
    material.put("planId", planId);
    material.put("blockId", block.id());
    material.put("targetModel", block.targetModel());
    material.put("assetVersion", assetVersion);
    material.put("matchFields", block.matchFields());
    material.put("records", block.records());
    return sha256(Json.toJsonString(material));
  }

  private String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + java.util.stream.IntStream.range(0, digest.length)
          .mapToObj(index -> String.format("%02x", digest[index] & 0xff))
          .collect(Collectors.joining());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 digest is unavailable", e);
    }
  }
}
