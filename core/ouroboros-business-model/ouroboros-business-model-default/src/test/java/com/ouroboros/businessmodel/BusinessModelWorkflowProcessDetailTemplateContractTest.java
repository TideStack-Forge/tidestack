package com.ouroboros.businessmodel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class BusinessModelWorkflowProcessDetailTemplateContractTest {

  @Test
  void processDetailTemplateUsesWorkflowDiagramViewerForApprovalRecordTab() {
    String template = readResource("META-INF/prototype-templates/ui-schema/workflow/processDetail.json.ftl");

    assertTrue(template.contains("\"type\": \"page\""));
    assertTrue(template.contains("\"type\": \"service\""));
    assertTrue(template.contains("/api/approval-workflow/instance/${instanceId}/diagram"));
    assertTrue(template.contains("\"type\": \"workflow-diagram-viewer\""));
    assertTrue(template.contains("\"activeNodeIds\": \"${activeNodeIds}\""));
    assertTrue(template.contains("\"type\": \"workflow-timeline\""));
    assertFalse(template.contains("\"type\": \"workflow-chart\""));
  }

  @Test
  void approvalFormTemplateUsesWorkflowTimelineForBusinessModelTaskPage() {
    String template = readResource("META-INF/prototype-templates/ui-schema/workflow/approvalForm.json.ftl");

    assertTrue(template.contains("\"type\": \"page\""));
    assertTrue(template.contains("\"initApi\": \"/api</#noparse>${modelPath}<#noparse>${id}/process/instance/${instanceId}/task/${taskId}/init-form\""));
    assertTrue(template.contains("\"id\": \"default-approval-form\""));
    assertTrue(template.contains("\"type\": \"workflow-timeline\""));
    assertTrue(template.contains("\"type\": \"user-picker\""));
    assertTrue(template.contains("\"defaultCurrentUser\": true"));
    assertFalse(template.contains("\"type\": \"employee-picker\""));
    assertTrue(template.contains("/process/instance/${instanceId}/task/${taskId}/PASS"));
    assertTrue(template.contains("\"componentId\": \"default-approval-form\""));
    assertFalse(template.contains("closeOpenPage"));
  }

  @Test
  void formTemplateDoesNotHandleCustomHostSystemTabOpenMode() {
    String template = readResource("META-INF/prototype-templates/ui-schema/form.json.ftl");

    assertFalse(template.contains("openPage"));
    assertFalse(template.contains("closeOpenPage"));
  }

  @Test
  void workflowSearchTemplateAttachesProcessInfoForListOperations() {
    String template = readResource("META-INF/prototype-templates/logic-flow/search.json.ftl");

    assertTrue(template.contains("\"type\" : \"attachProcessInfo\""));
    assertTrue(template.contains("\"modelPath\": \"${modelPath!}\""));
  }

  private String readResource(String resourcePath) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    assertNotNull(classLoader, "classLoader should exist");

    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      assertNotNull(inputStream, "resource should exist: " + resourcePath);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new AssertionError("failed to read resource: " + resourcePath, e);
    }
  }
}
