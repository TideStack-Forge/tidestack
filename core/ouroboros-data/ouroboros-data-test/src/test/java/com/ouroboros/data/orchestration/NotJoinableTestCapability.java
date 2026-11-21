package com.ouroboros.data.orchestration;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ouroboros.data.model.DataModel;

/**
 * 测试用 JoinCapability：可通过静态配置将指定模型对标记为 notJoinable。
 */
public class NotJoinableTestCapability implements JoinCapability {

  private static final Set<String> NOT_JOINABLE_PAIRS =
      Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

  public static void configureNotJoinablePairs(Collection<String> pairKeys) {
    clear();
    if (pairKeys != null) {
      NOT_JOINABLE_PAIRS.addAll(pairKeys);
    }
  }

  public static void markNotJoinable(String sourceModel, String targetModel) {
    NOT_JOINABLE_PAIRS.add(buildPairKey(sourceModel, targetModel));
  }

  public static void clear() {
    NOT_JOINABLE_PAIRS.clear();
  }

  public static String buildPairKey(String sourceModel, String targetModel) {
    String left = normalize(sourceModel);
    String right = normalize(targetModel);
    if (left.compareTo(right) <= 0) {
      return left + "->" + right;
    }
    return right + "->" + left;
  }

  @Override
  public JoinCapabilityResult canJoin(DataModel source, DataModel target) {
    String sourceName = safeModelName(source);
    String targetName = safeModelName(target);

    if (sourceName == null || targetName == null) {
      return JoinCapabilityResult.notJoinable("test: null model name");
    }

    String pairKey = buildPairKey(sourceName, targetName);
    if (NOT_JOINABLE_PAIRS.contains(pairKey)) {
      return JoinCapabilityResult.notJoinable("test: configured not joinable pair " + pairKey);
    }

    return JoinCapabilityResult.joinable();
  }

  @Override
  public int getPriority() {
    return 1;
  }

  private static String safeModelName(DataModel model) {
    if (model == null) {
      return null;
    }

    String name = model.getName();
    if (name == null || name.trim().isEmpty()) {
      name = model.getFullName();
    }
    return normalize(name);
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
