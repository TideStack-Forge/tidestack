package com.ouroboros.businessmodel.projection.authority;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.security.authority.Authority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParentMPSTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void featureReadAuthoritiesDoNotRemoveTheExistingPageEntry(boolean searchable) {
    var meta = new DefaultBusinessModelMeta();
    meta.setNamespace("uws");
    meta.setName("weightTicket");
    meta.setTitle("磅单管理");
    var id = new BusinessModelFieldMeta();
    id.setName("id");
    id.setType("String");
    id.setIsPrimaryKey(true);
    meta.setFields(List.of(id));
    meta.setExtraProp("apis", Map.of("search", Map.of("isEnabled", searchable)));
    meta.setExtraProp("features", Map.of("isEnabledSoftDelete", true));
    var provider = new BusinessModelProvider() {
      public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
      public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
    };
    var model = new DefaultBusinessModel(meta, provider);
    assertTrue(model.getBehavior().getActions().containsKey("searchDeleted"));
    var projection = new ModelProjectionPipelineContext<>(model);
    projection.setAttribute("hasDataModel", true);

    new SearchMPS().process(projection);
    new ParentMPS().process(projection);

    var parent = projection.getModel(Authority.class, "uws.weightTicket").orElseThrow();
    var search = projection.getModel(Authority.class, "uws.weightTicket.search").orElseThrow();
    var entryOwner = searchable ? search : parent;
    var other = searchable ? parent : search;
    assertEquals(List.of("uws", "uws.weightTicket"), entryOwner.getMenuItems());
    assertEquals(List.of("/uws/weight-ticket"), entryOwner.getUiViews());
    assertTrue(other.getMenuItems().isEmpty());
    if (!searchable) {
      assertTrue(search.getApis().isEmpty(), "the disabled CRUD query must stay disabled");
    }
  }
}
