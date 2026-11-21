package com.ouroboros.workflow.approval.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.AppContext;
import com.ouroboros.fieldtype.*;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;

class CustomApprovalFormRuntimeTest {
  @TempDir Path home;

  @Test
  void validatesStartupAndMetadataRefreshAndReadsCurrentApplicationDirectory() throws Exception {
    var configuration = new ApprovalWorkflowAdapterAutoConfiguration();
    var catalog = new DefaultFieldTypeCatalog(List.of(new JarFieldTypeDefinitionProvider(new FieldTypeDefinitionReader())), new DefaultFieldTypeCompiler());
    AppContext app = new AppContext() { @Override public String getHomePath() { return home.toString(); } };
    var provider = configuration.customFlowFormProvider(app, catalog);
    assertTrue(provider.search(null).isEmpty());
    Path file = Files.createDirectories(home.resolve("approval-form")).resolve("purchase.approval-form.json");
    Files.writeString(file, description("金额"));
    assertEquals("金额", provider.get("purchase").orElseThrow().getFields().get(0).getLabel());
    Files.writeString(file, description("含税金额"));
    configuration.approvalFormMetadataValidator(provider).onApplicationEvent(new org.springframework.context.PayloadApplicationEvent<>(this, new OuroborosMetadataRefreshingEvent(this)));
    assertEquals("含税金额", provider.get("purchase").orElseThrow().getFields().get(0).getLabel());
    Files.writeString(file, description("金额").replace("定点数字", "未知类型"));
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> configuration.customFlowFormProvider(app, catalog));
    assertThrows(com.ouroboros.exception.BusinessException.class, () -> configuration.approvalFormMetadataValidator(provider)
        .onApplicationEvent(new org.springframework.context.PayloadApplicationEvent<>(this, new OuroborosMetadataRefreshingEvent(this))));
    assertThrows(com.ouroboros.exception.InternalException.class, () -> configuration.customFlowFormProvider(new AppContext() {
      @Override public String getHomePath() { return ""; }
    }, catalog));
  }

  private String description(String label) {
    return """
        {"formatVersion":1,"name":"purchase","title":"采购申请","fields":[
        {"name":"amount","label":"%s","type":"定点数字","params":{"size":12,"precision":2}}]}
        """.formatted(label);
  }
}
