package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapter;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterDescriptor;
import com.ouroboros.workflow.approval.external.ApprovalWorkflowEngineAdapterProvider;
import com.ouroboros.workflow.approval.external.Capability;
import com.ouroboros.workflow.approval.external.ExternalHealth;
import com.ouroboros.workflow.approval.external.ExternalInstanceDetailReader;
import com.ouroboros.workflow.approval.external.ExternalInstancePage;
import com.ouroboros.workflow.approval.external.ExternalInstanceQuery;
import com.ouroboros.workflow.approval.external.ExternalInstanceReader;
import com.ouroboros.workflow.approval.external.ExternalInstanceRef;
import com.ouroboros.workflow.approval.external.ExternalInstanceSnapshot;
import com.ouroboros.workflow.approval.external.ExternalStartCommand;
import com.ouroboros.workflow.approval.external.ExternalStartResult;
import com.ouroboros.workflow.approval.external.ExternalWorkflowStarter;

import io.vavr.control.Try;

/**
 * M2 Registry 规则测试：SPI+Bean 聚合去重、启动失败语义、能力派生。
 */
class WorkflowEngineAdapterRegistryTest {

  private WorkflowEngineAdapterRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new WorkflowEngineAdapterRegistry(new WorkflowEngineAdapterProviderBeanBridge(List.of()));
  }

  @Test
  void registersProviderAndDerivesCapabilitiesFromPorts() {
    registry.initialize(List.of(new StarterAndReaderProvider("test-adapter-a")));

    assertThat(registry.hasAdapter("test-adapter-a")).isTrue();
    assertThat(registry.capabilitiesOf("test-adapter-a"))
        .containsExactlyInAnyOrder(Capability.EXTERNAL_START, Capability.EXTERNAL_INSTANCE_QUERY);
    assertThat(registry.capabilitiesOf("test-adapter-a"))
        .doesNotContain(Capability.EXTERNAL_TASK_QUERY);
    assertThat(registry.listAdapters()).hasSize(1);
  }

  @Test
  void detailReaderDoesNotImplyIncrementalPolling() {
    var provider = new ApprovalWorkflowEngineAdapterProvider() {
      @Override
      public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
        return WorkflowEngineAdapterRegistryTest.descriptor(
            "detail-only", "detail only", Set.of());
      }

      @Override
      public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
        return new DetailOnlyAdapter();
      }
    };

    registry.initialize(List.of(provider));

    assertThat(registry.capabilitiesOf("detail-only"))
        .containsExactly(Capability.EXTERNAL_INSTANCE_QUERY)
        .doesNotContain(Capability.POLLING);
  }

  @Test
  void mergesDescriptorExtraCapabilities() {
    var provider = new StarterAndReaderProvider("test-adapter-extra",
        Set.of(Capability.POLLING, Capability.EXTERNAL_INSTANCE_CANCEL));
    registry.initialize(List.of(provider));

    assertThat(registry.capabilitiesOf("test-adapter-extra"))
        .containsExactlyInAnyOrder(
            Capability.EXTERNAL_START,
            Capability.EXTERNAL_INSTANCE_QUERY,
            Capability.POLLING,
            Capability.EXTERNAL_INSTANCE_CANCEL);
  }

  @Test
  void duplicateAdapterIdFailsStartup() {
    assertThatThrownBy(() -> registry.initialize(List.of(
        new StarterAndReaderProvider("dup-adapter"),
        new StarterAndReaderProvider("dup-adapter"))))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("adapterId 重复");
  }

  @Test
  void duplicateProviderFailsStartupInsteadOfSelectingFirstAdapter() {
    ApprovalWorkflowEngineAdapterProvider first = new StarterAndReaderProvider("provider-adapter-a") {
      @Override
      public String provider() {
        return "shared-provider";
      }
    };
    ApprovalWorkflowEngineAdapterProvider second = new StarterAndReaderProvider("provider-adapter-b") {
      @Override
      public String provider() {
        return "shared-provider";
      }
    };

    assertThatThrownBy(() -> registry.initialize(List.of(first, second)))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("provider 重复注册")
        .hasMessageContaining("shared-provider");
  }

  @Test
  void invalidDescriptorFailsStartup() {
    var invalidId = new ProviderWithDescriptor(
        descriptor("Bad_Id!", "name", Set.of()));
    assertThatThrownBy(() -> registry.initialize(List.of(invalidId)))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("adapterId 非法");
  }

  @Test
  void blankDisplayNameFailsStartup() {
    var invalid = new ProviderWithDescriptor(
        descriptor("ok-id", "  ", Set.of()));
    assertThatThrownBy(() -> registry.initialize(List.of(invalid)))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("displayName");
  }

  @Test
  void nullAdapterInstanceFailsStartup() {
    var nullAdapter = new ApprovalWorkflowEngineAdapterProvider() {
      @Override
      public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
        return WorkflowEngineAdapterRegistryTest.descriptor("null-adapter", "null", Set.of());
      }

      @Override
      public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
        return null;
      }
    };
    assertThatThrownBy(() -> registry.initialize(List.of(nullAdapter)))
        .isInstanceOf(PlatformException.class);
  }

  @Test
  void extraCapabilityWithoutPortFailsStartup() {
    var provider = new ApprovalWorkflowEngineAdapterProvider() {
      @Override
      public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
        return new ApprovalWorkflowEngineAdapterDescriptor("bad-cap", "bad", "1.0", Set.of(), Set.of(Capability.EXTERNAL_START), Set.of());
      }

      @Override
      public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
        return new HealthOnlyAdapter();
      }
    };

    assertThatThrownBy(() -> registry.initialize(List.of(provider)))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("EXTERNAL_START")
        .hasMessageContaining("bad-cap");
  }

  @Test
  void catalogViewExposesCapabilitiesAndActions() {
    registry.initialize(List.of(new StarterAndReaderProvider("test-adapter-a")));
    Map<String, Object> view = registry.getAdapter("test-adapter-a").orElseThrow().catalogView();

    assertThat(view.get("adapterId")).isEqualTo("test-adapter-a");
    assertThat(view).containsKey("canonicalActions").containsKey("disabledActions")
        .doesNotContainKeys("applicationTypes", "integrationModes",
            "fullProcessActionApplicationTypes", "connectionProfiles");
    assertThat(view.get("disabledActions")).isEqualTo(Set.of());
    assertThat(view.get("capabilities")).asString().contains("EXTERNAL_START");
    assertThat(view.get("canonicalActions")).asString().contains("approve");
  }

  /** 测试 provider：实现启动与实例读取端口。 */
  static class StarterAndReaderProvider implements ApprovalWorkflowEngineAdapterProvider {
    private final String adapterId;
    private final Set<Capability> extraCapabilities;

    StarterAndReaderProvider(String adapterId) {
      this(adapterId, Set.of());
    }

    StarterAndReaderProvider(String adapterId, Set<Capability> extraCapabilities) {
      this.adapterId = adapterId;
      this.extraCapabilities = extraCapabilities;
    }

    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return new ApprovalWorkflowEngineAdapterDescriptor(adapterId, "测试适配器 " + adapterId, "1.0", Set.of("approve", "reject"), extraCapabilities, Set.of());
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new FakeEngineAdapter();
    }
  }

  /** 测试适配器：实现 ExternalWorkflowStarter + ExternalInstanceReader 端口。 */
  static final class FakeEngineAdapter
      implements ApprovalWorkflowEngineAdapter, ExternalWorkflowStarter, ExternalInstanceReader {

    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalStartResult> start(ExternalStartCommand command) {
      return Try.success(new ExternalStartResult("ext-" + command.businessRecord().recordId(), null, null));
    }

    @Override
    public Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance) {
      return Try.success(new ExternalInstanceSnapshot(
          instance.externalInstanceId(), "RUNNING", null, null, null, Map.of()));
    }

    @Override
    public Try<ExternalInstancePage> queryChangedInstances(ExternalInstanceQuery query) {
      return Try.success(new ExternalInstancePage(List.of(), null));
    }
  }

  /** 测试 provider：直接携带指定 descriptor。 */
  /** 仅实现健康检查的适配器（用于能力声明与端口不符的启动期校验）。 */
  static final class HealthOnlyAdapter implements ApprovalWorkflowEngineAdapter {
    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }
  }

  static final class DetailOnlyAdapter
      implements ApprovalWorkflowEngineAdapter, ExternalInstanceDetailReader {
    @Override
    public Try<ExternalHealth> health() {
      return Try.success(new ExternalHealth(true, "ok"));
    }

    @Override
    public Try<ExternalInstanceSnapshot> getInstance(ExternalInstanceRef instance) {
      return Try.success(new ExternalInstanceSnapshot(
          instance.externalInstanceId(), "RUNNING", null, null, null, Map.of()));
    }
  }

  static final class ProviderWithDescriptor implements ApprovalWorkflowEngineAdapterProvider {
    private final ApprovalWorkflowEngineAdapterDescriptor descriptor;

    ProviderWithDescriptor(ApprovalWorkflowEngineAdapterDescriptor descriptor) {
      this.descriptor = descriptor;
    }

    @Override
    public ApprovalWorkflowEngineAdapterDescriptor descriptor() {
      return descriptor;
    }

    @Override
    public ApprovalWorkflowEngineAdapter create(ResolvedEnterpriseConnection connection) {
      return new FakeEngineAdapter();
    }
  }

  private static ApprovalWorkflowEngineAdapterDescriptor descriptor(
      String adapterId, String displayName, Set<String> canonicalActions) {
    return new ApprovalWorkflowEngineAdapterDescriptor(adapterId, displayName, "1.0", canonicalActions, Set.of(), Set.of());
  }
}
