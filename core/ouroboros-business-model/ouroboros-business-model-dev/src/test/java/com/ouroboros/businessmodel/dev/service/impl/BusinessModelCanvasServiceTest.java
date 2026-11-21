package com.ouroboros.businessmodel.dev.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ouroboros.businessmodel.dev.vo.BusinessModelCanvas;
import com.ouroboros.businessmodel.dev.vo.BusinessModelCanvas.*;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.file.VCSFile;

class BusinessModelCanvasServiceTest {
  @Test
  void validatesLegacyCanvasBeforeItCanBecomeCollaborative() {
    var service = new BusinessModelCanvasService();
    var layout = new Layout(Map.of("Order", new Point(40, 50)), Map.of(), null);
    var canvas = new BusinessModelCanvas("orders", "订单", false, false, List.of("Order"), layout, layout, "old-revision");
    assertDoesNotThrow(() -> service.validateCollaborationSeed("orders", Json.toJsonString(canvas)));
    assertThrows(ValidationException.class, () -> service.validateCollaborationSeed("../orders", Json.toJsonString(canvas)));
    assertThrows(ValidationException.class, () -> service.validateCollaborationSeed("another", Json.toJsonString(canvas)));
    var invalid = new Layout(Map.of("Order", new Point(Double.NaN, 20)), Map.of(), null);
    var malformed = new BusinessModelCanvas("orders", "订单", false, false, List.of("Order"), invalid, layout, "");
    assertThrows(RuntimeException.class, () -> service.validateCollaborationSeed("orders", Json.toJsonString(malformed)));
  }

  @Test
  void validatesNestedModuleGeometryAndViewportAndExposesTheExistingFileLocation() {
    var service = new BusinessModelCanvasService();
    assertEquals("business-model-canvas", service.collaborationType());
    assertEquals("business-model/canvases", service.collaborationDirectory());
    assertEquals(".canvas.json", service.collaborationSuffix());
    var layout = new Layout(Map.of("Order", new Point(40, 70)),
        Map.of("sales", new Group(0, 0, 240, 200, "销售", null)), new Viewport(1, new Point(0, 0)));
    var canvas = new BusinessModelCanvas("overview", "全局", true, true, List.of(), layout, layout, "");
    assertDoesNotThrow(() -> service.validateCollaborationSeed("overview", Json.toJsonString(canvas)));
    var invalid = new Layout(layout.nodes(), Map.of("sales", new Group(0, 0, -1, 200, "销售", null)), null);
    assertThrows(ValidationException.class, () -> service.validateCollaborationSeed("overview", Json.toJsonString(
        new BusinessModelCanvas("overview", "全局", true, true, List.of(), layout, invalid, ""))));
    var zoom = new Layout(layout.nodes(), layout.groups(), new Viewport(0, new Point(0, 0)));
    assertThrows(ValidationException.class, () -> service.validateCollaborationSeed("overview", Json.toJsonString(
        new BusinessModelCanvas("overview", "全局", true, true, List.of(), zoom, layout, ""))));
  }

  @Test
  void graphIncludesPolymorphicRelationsAndLockOwnership() {
    var relation = new BusinessModelRelationMeta();
    relation.setName("parent"); relation.setModel("sales.Order"); relation.setType("master");
    var model = new DefaultBusinessModelMeta() {
      @Override public List<BusinessModelRelationMeta> relationDefinitions() { return List.of(relation); }
    };
    model.setName("Order"); model.setNamespace("sales");
    var locked = mock(VCSFile.class);
    when(locked.getLockOwner()).thenReturn("other");
    when(locked.getLockOwnerName()).thenReturn("另一位开发者");
    var graphService = new BusinessModelDevService() {
      @Override public List<BusinessModelMeta> getAll() { return List.of(model); }
      @Override protected VCSFile getExistingOrPrimaryModelVCSFile(String name) { return locked; }
    };
    var graph = graphService.getGraph();
    assertEquals(1, graph.getEdges().size());
    assertEquals("sales.Order", graph.getEdges().getFirst().getTarget());
    assertEquals("other", graph.getNodes().getFirst().getLockUser());
    assertEquals("另一位开发者", graph.getNodes().getFirst().getLockUserName());
    assertEquals("sales", Json.toMap(Json.toJsonString(graph.getNodes().getFirst())).get("namespace"));
  }
}
