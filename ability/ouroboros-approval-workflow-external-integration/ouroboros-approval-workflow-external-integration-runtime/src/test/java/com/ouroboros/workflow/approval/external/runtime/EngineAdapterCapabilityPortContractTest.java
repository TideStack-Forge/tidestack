package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.workflow.approval.external.Capability;

/**
 * 能力枚举与端口派生表的对应关系。
 *
 * <p>约定（见 {@link Capability} javadoc）：
 * <ul>
 *   <li>有专属端口的取值必须在 {@code PORT_BY_CAPABILITY} 中登记，声明后由启动期校验端口实现；</li>
 *   <li>无专属端口的取值必须显式列为豁免，且不得悄悄新增第三个；</li>
 *   <li>POLLING 必须登记端口（声明后校验 {@code ExternalInstanceReader}），但不参与派生。</li>
 * </ul>
 *
 * <p>这条测试的存在是为了防止"新增一个能力枚举但忘了登记端口"再次造成
 * descriptor 声明与端口实现各说各话（审查 C8）。
 */
class EngineAdapterCapabilityPortContractTest {

  /**
   * 无专属端口的取值：语义由规范动作与结果映射表达，没有可校验的端口。
   *
   * <p>前两者目前没有任何 provider 声明，属未落地的预留项；新增此类豁免必须同时更新
   * {@link Capability} 的 javadoc 与外部集成设计文档。
   */
  private static final Set<Capability> CAPABILITIES_WITHOUT_PORT =
      EnumSet.of(
          Capability.EXTERNAL_FINAL_RESULT,
          Capability.EXTERNAL_INSTANCE_ACTION,
          Capability.EXTERNAL_INSTANCE_CANCEL);

  /** 有端口但不参与派生：必须显式声明后才校验端口。 */
  private static final Set<Capability> DECLARATION_ONLY_CAPABILITIES =
      EnumSet.of(Capability.POLLING);

  @Test
  void everyCapabilityIsEitherMappedToAPortOrExplicitlyExempt() {
    Set<Capability> accounted = EnumSet.noneOf(Capability.class);
    accounted.addAll(EngineAdapterCapabilities.PORT_BY_CAPABILITY.keySet());
    accounted.addAll(CAPABILITIES_WITHOUT_PORT);
    accounted.addAll(DECLARATION_ONLY_CAPABILITIES);

    assertThat(accounted)
        .as("每个能力枚举都必须登记端口或显式豁免，不允许第三种状态")
        .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Capability.class));
  }

  @Test
  void exemptionSetsDoNotOverlapThePortMapping() {
    assertThat(EngineAdapterCapabilities.PORT_BY_CAPABILITY.keySet())
        .doesNotContainAnyElementsOf(CAPABILITIES_WITHOUT_PORT);
    assertThat(EngineAdapterCapabilities.PORT_BY_CAPABILITY).containsKey(Capability.POLLING);
  }
}
