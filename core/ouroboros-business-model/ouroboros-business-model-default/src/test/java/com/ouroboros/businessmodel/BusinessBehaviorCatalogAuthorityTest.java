package com.ouroboros.businessmodel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.behavior.BusinessBehaviorAuthorities;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.authority.BehaviorActionMPS;
import com.ouroboros.security.authority.Authority;

import static org.junit.jupiter.api.Assertions.*;

class BusinessBehaviorCatalogAuthorityTest {
  @Test
  void modelingInspectionIsAnExplicitProjectedPermissionAndDoesNotGrantInvocationOrFactAccess() {
    var source = (DefaultBusinessModelMeta) BusinessModelMetaParser.parse("""
        {"modelType":"default","namespace":"catalog","name":"Order","title":"订单"}
        """).get();
    var model = new DefaultBusinessModel(source, new BusinessModelProvider() {
      public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
      public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
    });
    var context = new ModelProjectionPipelineContext<>(model);
    new BehaviorActionMPS().process(context);
    var authority = context.getModel(Authority.class, BusinessBehaviorAuthorities.inspect("catalog.Order")).orElseThrow();
    assertEquals(List.of("GET /api/business-model-behaviors/catalog.Order/catalog"), authority.getApis());
    assertEquals(1, context.getModelMap(Authority.class).size());
    assertTrue(source.getActions().isEmpty());
  }
}
