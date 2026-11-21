package com.ouroboros.businessmodel.projection.authority;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.model.BusinessModelAuthority;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.security.authority.Authority;

class BusinessModelDelegationProjectionTest {
  @Test
  void usesTheOriginalPermissionProjectionAndRequiresExplicitBooleanOptIn() {
    for (Object value : new Object[] { false, "true", true }) {
      Authority authority = project(Map.of("delegable", value));
      assertEquals(Boolean.TRUE.equals(value), authority.isDelegable());
      assertEquals("fixture.record.search", authority.getFullName());
      assertTrue(authority.getApis().contains("GET /api/fixture/record"));
      if (Boolean.TRUE.equals(value)) {
        var field = authority.getAccessControlDefine().getCommonFields().getFirst();
        assertEquals("String", field.getType());
        assertEquals("名称", field.getLabel());
      }
    }
    assertFalse(project(Map.of()).isDelegable());
  }

  @Test
  void delegationAlwaysInstallsTheOriginalDataAuthorizationPlugin() {
    var metadata = new com.ouroboros.data.model.DataModelMeta();
    metadata.setNamespace("fixture");
    metadata.setName("record");
    metadata.getExtraProps().put("accessControl", Map.of("search", Map.of("delegable", true, "isEnabled", false)));
    var decorated = new com.ouroboros.businessmodel.data.decorators.AuthorityFieldsDecorator().decorate(metadata);
    var plugin = decorated.getPluginDescriptors().stream().filter(p -> p.getName().equals("AccessControl")).findFirst().orElseThrow();
    assertEquals("fixture.record.search", plugin.getConfig().get("readAuthority"));
    assertFalse(plugin.getConfig().containsKey("createAuthority"));
  }

  @Test
  void sealingPreventsChangesToTheDelegationCapability() {
    var authority = (BusinessModelAuthority) project(Map.of("delegable", true));
    authority.seal();
    assertThrows(IllegalStateException.class, () -> authority.setDelegable(false));
    assertTrue(authority.isDelegable());
  }

  private Authority project(Map<String, Object> accessControl) {
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("fixture");
    meta.setName("record");
    meta.setTitle("Fixture Record");
    meta.setIsEnabled(true);
    var field = new com.ouroboros.businessmodel.meta.BusinessModelFieldMeta();
    field.setName("name"); field.setTitle("名称"); field.setType("String");
    meta.setFields(java.util.List.of(field));
    meta.setExtraProp("apis", Map.of("search", Map.of("isEnabled", true)));
    meta.setExtraProp("accessControl", Map.of("search", accessControl));
    var context = new ModelProjectionPipelineContext<>(new DefaultBusinessModel(meta, mock(BusinessModelProvider.class)));
    context.setAttribute("hasDataModel", true);
    new SearchMPS().process(context);
    return context.getModel(Authority.class, "fixture.record.search").orElseThrow();
  }
}
