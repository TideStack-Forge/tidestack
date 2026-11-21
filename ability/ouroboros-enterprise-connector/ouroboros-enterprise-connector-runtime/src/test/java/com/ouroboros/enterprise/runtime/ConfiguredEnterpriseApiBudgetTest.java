package com.ouroboros.enterprise.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.enterprise.EnterpriseApiRateLimitException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfiguredEnterpriseApiBudgetTest {
  @Test void allConsumersOfOneProviderShareAdmissionAndConfigurationChangesDoNotResetIt() {
    var configuration = mock(ConfigurationService.class); var time = new AtomicLong();
    when(configuration.get(ConfiguredEnterpriseApiBudget.KEY)).thenReturn(Optional.of(Map.of("feishu", 10, "wecom", 5)));
    var budget = new ConfiguredEnterpriseApiBudget(configuration, 1, time::get);
    budget.acquire("feishu");
    assertThatThrownBy(() -> budget.acquire("feishu")).isInstanceOf(EnterpriseApiRateLimitException.class);
    budget.acquire("wecom");
    time.set(99_999_999);
    assertThatThrownBy(() -> budget.acquire("feishu")).isInstanceOf(EnterpriseApiRateLimitException.class);
    time.incrementAndGet(); budget.acquire("feishu");
    when(configuration.get(ConfiguredEnterpriseApiBudget.KEY)).thenReturn(Optional.of(Map.of("feishu", 5)));
    time.set(299_999_999);
    assertThatThrownBy(() -> budget.acquire("feishu")).isInstanceOf(EnterpriseApiRateLimitException.class);
    time.incrementAndGet(); budget.acquire("feishu");
  }

  @Test void eachNodeConsumesOnlyItsConservativeShareAndUnknownBudgetsNeverOpenTraffic() {
    var configuration = mock(ConfigurationService.class); var time = new AtomicLong();
    when(configuration.get(ConfiguredEnterpriseApiBudget.KEY)).thenReturn(Optional.of(Map.of("feishu", 10)));
    var one = new ConfiguredEnterpriseApiBudget(configuration, 2, time::get);
    var two = new ConfiguredEnterpriseApiBudget(configuration, 2, time::get);
    one.acquire("feishu"); two.acquire("feishu");
    time.set(100_000_000);
    assertThatThrownBy(() -> one.acquire("feishu")).isInstanceOf(EnterpriseApiRateLimitException.class);
    assertThatThrownBy(() -> two.acquire("feishu")).isInstanceOf(EnterpriseApiRateLimitException.class);
    time.set(200_000_000); one.acquire("feishu"); two.acquire("feishu");
    assertThatThrownBy(() -> one.acquire("dingtalk")).hasMessageContaining("核实");
    when(configuration.get(ConfiguredEnterpriseApiBudget.KEY)).thenReturn(Optional.of(Map.of("feishu", 1)));
    assertThatThrownBy(() -> one.acquire("feishu")).hasMessageContaining("不足");
    when(configuration.get(ConfiguredEnterpriseApiBudget.KEY)).thenReturn(Optional.of(Map.of("feishu", 1.5)));
    assertThatThrownBy(() -> one.acquire("feishu")).hasMessageContaining("整数");
  }

  @Test void simultaneousConsumersCannotSpendTheSameSlot() throws Exception {
    var configuration = mock(ConfigurationService.class);
    when(configuration.get(ConfiguredEnterpriseApiBudget.KEY)).thenReturn(Optional.of(Map.of("feishu", 10)));
    var budget = new ConfiguredEnterpriseApiBudget(configuration, 1, () -> 0);
    try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
      for (int i = 0; i < 20; i++) {
        results.add(workers.submit(() -> {
          try { budget.acquire("feishu"); return true; }
          catch (EnterpriseApiRateLimitException limited) { return false; }
        }));
      }
      int admitted = 0;
      for (var result : results) { if (result.get()) { admitted++; } }
      assertThat(admitted).isEqualTo(1);
    }
  }
}
