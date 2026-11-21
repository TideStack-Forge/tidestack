package com.ouroboros.enterprise.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.enterprise.EnterpriseApiBudget;
import com.ouroboros.exception.BusinessException;

/** A conservative per-node share of one deployment budget, shared by every installed consumer. */
public final class ConfiguredEnterpriseApiBudget implements EnterpriseApiBudget {
  public static final String KEY = "enterprise.api-budgets";
  private final ConfigurationService configuration;
  private final int executionNodes;
  private final LongSupplier nanos;
  private final Map<String, Long> lastRequests = new HashMap<>();

  public ConfiguredEnterpriseApiBudget(ConfigurationService configuration, int executionNodes) {
    this(configuration, executionNodes, System::nanoTime);
  }

  ConfiguredEnterpriseApiBudget(ConfigurationService configuration, int executionNodes, LongSupplier nanos) {
    if (executionNodes < 1) { throw new IllegalArgumentException("企业 API 执行节点数必须为正数"); }
    this.configuration = configuration; this.executionNodes = executionNodes; this.nanos = nanos;
  }

  @Override
  public void acquire(String provider) {
    Object raw = configuration.get(KEY).orElse(null);
    if (!(raw instanceof Map<?, ?> budgets) || !(budgets.get(provider) instanceof Number rate)) {
      throw new com.ouroboros.enterprise.EnterpriseApiRateLimitException("请先核实并配置企业渠道的部署总调用预算");
    }
    int perSecond;
    try { perSecond = new java.math.BigDecimal(rate.toString()).intValueExact(); }
    catch (ArithmeticException | NumberFormatException error) { throw new BusinessException("企业 API 预算必须为正整数", error); }
    if (perSecond < executionNodes || perSecond > 1_000_000) {
      throw new com.ouroboros.enterprise.EnterpriseApiRateLimitException("企业 API 总预算不足以分配给已配置执行节点");
    }
    long spacing = (1_000_000_000L * executionNodes + perSecond - 1) / perSecond;
    synchronized (lastRequests) {
      long now = nanos.getAsLong();
      Long previous = lastRequests.get(provider);
      if (previous != null && now - previous < spacing) {
        throw new com.ouroboros.enterprise.EnterpriseApiRateLimitException("企业渠道共享调用预算已耗尽，请稍后重试");
      }
      lastRequests.put(provider, now);
    }
  }
}
