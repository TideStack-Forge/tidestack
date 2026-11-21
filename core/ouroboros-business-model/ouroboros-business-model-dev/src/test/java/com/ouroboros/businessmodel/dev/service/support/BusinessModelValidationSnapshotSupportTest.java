package com.ouroboros.businessmodel.dev.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelBuilder;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.CompositeViewBusinessModel;
import com.ouroboros.businessmodel.DashboardBusinessModel;
import com.ouroboros.businessmodel.dev.vo.BusinessModelValidationState;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.businessmodel.meta.DashboardBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelBundle;
import com.ouroboros.businessmodel.validation.ValidationResult;

class BusinessModelValidationSnapshotSupportTest {

  @Test
  void shouldBuildValidSnapshotWhenModelHasNoDiagnostics() {
    var meta = businessModelMeta("valid-model");
    var support = support(new StubBusinessModel(Collections.emptyList(), Collections.emptyList()));

    var snapshot = support.snapshot(meta);

    assertEquals(BusinessModelValidationState.VALID, snapshot.getValidationState());
    assertNull(snapshot.getValidationSummary());
    assertTrue(snapshot.getValidationErrors().isEmpty());
    assertTrue(snapshot.getValidationDiagnostics().isEmpty());
    assertNotNull(snapshot.getLastValidatedTime());
  }

  @Test
  void shouldProjectModelValidationErrorsWithoutDependingOnMessageText() {
    var meta = businessModelMeta("invalid-model");
    var support = support(new StubBusinessModel(
        Collections.singletonList(new CustomValidationException("任意模型错误")),
        Collections.emptyList()
    ));

    var snapshot = support.snapshot(meta);

    assertEquals(BusinessModelValidationState.INVALID, snapshot.getValidationState());
    assertEquals("任意模型错误", snapshot.getValidationSummary());
    assertEquals(Collections.singletonList("任意模型错误"), snapshot.getValidationErrors());
    assertEquals("custom_validation", snapshot.getValidationDiagnostics().get(0).getDiagnosticKind());
    assertEquals("model", snapshot.getValidationDiagnostics().get(0).getScope());
  }

  @Test
  void shouldProjectRelationValidationErrorsWithRelationScope() {
    var meta = businessModelMeta("relation-model");
    var support = support(new StubBusinessModel(
        Collections.emptyList(),
        Collections.singletonList(new CustomRelationException("任意关系错误"))
    ));

    var snapshot = support.snapshot(meta);

    assertEquals(BusinessModelValidationState.INVALID, snapshot.getValidationState());
    assertEquals("任意关系错误", snapshot.getValidationSummary());
    assertEquals("custom_relation", snapshot.getValidationDiagnostics().get(0).getDiagnosticKind());
    assertEquals("relation", snapshot.getValidationDiagnostics().get(0).getScope());
  }

  @Test
  void shouldFailClosedWhenNoBuilderSupportsModelType() {
    var meta = businessModelMeta("unsupported-model");
    var support = new BusinessModelValidationSnapshotSupport(Collections.singletonList(new UnsupportedBuilder()), new EmptyProvider());

    var snapshot = support.snapshot(meta);

    assertEquals(BusinessModelValidationState.INVALID, snapshot.getValidationState());
    assertEquals("unsupported_business_model_type", snapshot.getValidationDiagnostics().get(0).getDiagnosticKind());
    assertEquals("model", snapshot.getValidationDiagnostics().get(0).getScope());
    assertEquals(snapshot.getValidationDiagnostics().get(0).getMessage(), snapshot.getValidationSummary());
  }

  @Test
  void shouldUseStableFallbackWhenThrowableMessageIsBlank() {
    var meta = businessModelMeta("blank-message-model");
    var support = support(new StubBusinessModel(
        Collections.singletonList(new BlankMessageException()),
        Collections.emptyList()
    ));

    var snapshot = support.snapshot(meta);

    assertEquals(BusinessModelValidationState.INVALID, snapshot.getValidationState());
    assertEquals("BlankMessageException", snapshot.getValidationSummary());
    assertEquals("blank_message", snapshot.getValidationDiagnostics().get(0).getDiagnosticKind());
  }

  @Test
  void shouldProjectDashboardValidationErrorsThroughBusinessModelBuilder() {
    var meta = dashboardMetaWithInvalidWidget();
    var support = new BusinessModelValidationSnapshotSupport(
        Collections.singletonList(new DashboardBusinessModel.Builder()),
        new EmptyProvider()
    );

    var snapshot = support.snapshot(meta);

    assertEquals(BusinessModelValidationState.INVALID, snapshot.getValidationState());
    assertTrue(snapshot.getValidationErrors().stream()
        .anyMatch(message -> message.contains("dashboard.widgets[0].widgetName-invalid")));
    assertEquals("business_define_feature_error", snapshot.getValidationDiagnostics().get(0).getDiagnosticKind());
    assertEquals("model", snapshot.getValidationDiagnostics().get(0).getScope());
  }

  @Test
  void shouldProjectCompositeViewValidationErrorsThroughBusinessModelBuilder() {
    var meta = compositeViewMetaWithInvalidSource();
    var support = new BusinessModelValidationSnapshotSupport(
        Collections.singletonList(new CompositeViewBusinessModel.Builder()),
        new EmptyProvider()
    );

    var snapshot = support.snapshot(meta);

    assertEquals(BusinessModelValidationState.INVALID, snapshot.getValidationState());
    assertTrue(snapshot.getValidationErrors().stream()
        .anyMatch(message -> message.contains("composite-view.source-missing")));
    assertEquals("business_define_feature_error", snapshot.getValidationDiagnostics().get(0).getDiagnosticKind());
    assertEquals("model", snapshot.getValidationDiagnostics().get(0).getScope());
  }

  @Test
  void shouldReturnDatasetRelationDiagnosticsForCompositeViewDrafts() {
    var meta = compositeViewMetaWithInvalidSource();
    meta.setDatasetName("missing.dataset");
    var column = new CompositeViewBusinessModelMeta.ColumnDef();
    column.setField("id");
    column.setTitle("编号");
    meta.setColumns(List.of(column));
    var support = new BusinessModelValidationSnapshotSupport(
        List.of(new CompositeViewBusinessModel.Builder()), new EmptyProvider());

    var snapshot = support.snapshot(meta);

    assertEquals(BusinessModelValidationState.INVALID, snapshot.getValidationState());
    assertTrue(snapshot.getValidationDiagnostics().stream().anyMatch(diagnostic ->
        "relation".equals(diagnostic.getScope()) && diagnostic.getMessage().contains("datasetName")));
    assertEquals("missing.dataset", meta.getDatasetName());
    assertEquals("id", meta.getColumns().getFirst().getField());
  }

  private BusinessModelValidationSnapshotSupport support(BusinessModel businessModel) {
    return new BusinessModelValidationSnapshotSupport(
        Collections.singletonList(new SupportedBuilder(businessModel)),
        new EmptyProvider()
    );
  }

  private static BusinessModelMeta businessModelMeta(String name) {
    var meta = new BusinessModelMeta();
    meta.setName(name);
    meta.setNamespace("demo");
    meta.setTitle(name);
    return meta;
  }

  private static DashboardBusinessModelMeta dashboardMetaWithInvalidWidget() {
    var meta = new DashboardBusinessModelMeta();
    meta.setName("salesDashboard");
    meta.setNamespace("demo");
    meta.setTitle("销售仪表板");
    var widget = new DashboardBusinessModelMeta.WidgetDef();
    widget.setId("salesChart");
    widget.setWidgetName("unsupported-widget");
    widget.setChartName("demo.salesChart");
    meta.setWidgets(Collections.singletonList(widget));
    return meta;
  }

  private static CompositeViewBusinessModelMeta compositeViewMetaWithInvalidSource() {
    var meta = new CompositeViewBusinessModelMeta();
    meta.setName("salesList");
    meta.setNamespace("demo");
    meta.setTitle("销售列表");
    return meta;
  }

  private static class CustomValidationException extends RuntimeException {
    CustomValidationException(String message) {
      super(message);
    }
  }

  private static class CustomRelationException extends RuntimeException {
    CustomRelationException(String message) {
      super(message);
    }
  }

  private static class BlankMessageException extends RuntimeException {
    @Override
    public String getMessage() {
      return " ";
    }
  }

  private static class SupportedBuilder implements BusinessModelBuilder<BusinessModelMeta, BusinessModel> {
    private final BusinessModel businessModel;

    private SupportedBuilder(BusinessModel businessModel) {
      this.businessModel = businessModel;
    }

    @Override
    public Boolean support(BusinessModelMeta businessModelMeta) {
      return true;
    }

    @Override
    public BusinessModel build(BusinessModelMeta businessModelMeta, BusinessModelProvider businessModelProvider) {
      return businessModel;
    }
  }

  private static class UnsupportedBuilder implements BusinessModelBuilder<BusinessModelMeta, BusinessModel> {
    @Override
    public Boolean support(BusinessModelMeta businessModelMeta) {
      return false;
    }

    @Override
    public BusinessModel build(BusinessModelMeta businessModelMeta, BusinessModelProvider businessModelProvider) {
      return null;
    }
  }

  private static class EmptyProvider implements BusinessModelProvider {
    @Override
    public Optional<BusinessModel> getBusinessModel(String fullName) {
      return Optional.empty();
    }

    @Override
    public java.util.Map<String, BusinessModel> getBusinessModelMap() {
      return Collections.emptyMap();
    }
  }

  private static class StubBusinessModel implements BusinessModel {
    private final List<Throwable> modelErrors;
    private final List<Throwable> relationErrors;

    private StubBusinessModel(List<Throwable> modelErrors, List<Throwable> relationErrors) {
      this.modelErrors = modelErrors;
      this.relationErrors = relationErrors;
    }

    @Override
    public ValidationResult getValidationResult() {
      return new ValidationResult(modelErrors);
    }

    @Override
    public Boolean isValid() {
      return modelErrors.isEmpty();
    }

    @Override
    public ValidationResult getRelationValidationResult() {
      return new ValidationResult(relationErrors);
    }

    @Override
    public Boolean isRelationValid() {
      return relationErrors.isEmpty();
    }

    @Override
    public ModelBundle buildModelBundle() {
      return new ModelBundle();
    }

    @Override
    public String getFormatVersion() {
      return null;
    }

    @Override
    public String getName() {
      return "stub";
    }

    @Override
    public String getNamespace() {
      return "demo";
    }

    @Override
    public String getFullName() {
      return "demo.stub";
    }

    @Override
    public String getTitle() {
      return "Stub";
    }

    @Override
    public String getDescription() {
      return null;
    }

    @Override
    public String getIcon() {
      return null;
    }

    @Override
    public Boolean isEnabled() {
      return true;
    }

    @Override
    public Boolean isAttachment() {
      return false;
    }

    @Override
    public java.util.Map<String, Object> getExtraProps() {
      return Collections.emptyMap();
    }

    @Override
    public Optional<Object> getExtraProp(String key) {
      return Optional.empty();
    }
  }
}
