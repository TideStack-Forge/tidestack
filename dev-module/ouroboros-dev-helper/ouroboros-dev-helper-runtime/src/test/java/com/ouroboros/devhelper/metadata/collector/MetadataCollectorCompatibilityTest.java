package com.ouroboros.devhelper.metadata.collector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import jakarta.annotation.Priority;
import org.junit.jupiter.api.Test;

import com.ouroboros.annotation.LowCodeProvider;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.AppModuleProvider;
import com.ouroboros.appmodule.BaseAppModule;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;
import com.ouroboros.dev.metadata.MetadataSourceType;
import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowModelMetaProvider;
import com.ouroboros.menu.BaseMenuModel;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelProvider;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.security.authority.BaseAuthority;
import com.ouroboros.uimodel.SimpleUIModel;
import com.ouroboros.uimodel.UIModel;
import com.ouroboros.uimodel.UIModelProvider;
import com.ouroboros.uischema.UISchemaProvider;
import com.ouroboros.util.SPI;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIModelProvider;

/** Original metadata refresh exports remain available alongside online capture commands. */
class MetadataCollectorCompatibilityTest {
  @Test
  void flowExportKeepsProviderKeyDescriptionAndExecutableDefinition() {
    var source = new LinkedHashMap<String, FlowModelMeta>();
    source.put("flow-key", FlowModelMeta.builder().name("orders").description("订单流").entryNode("start").build());
    var provider = mock(FlowModelMetaProvider.class);
    when(provider.getFlowModelMetas()).thenReturn(source);
    assertRefresh(FlowModelMetaProvider.class, provider, LogicflowMetadataCollector::new, "logic-flow",
        "flow-key", "订单流", "entryNode", "start", source::clear);
  }

  @Test
  void webApiExportPreservesSummaryMethodAndPath() {
    var source = new LinkedHashMap<String, WebAPIModel>();
    source.put("orders-api", SimpleWebAPIModel.builder().get("/orders").summary("订单查询").build());
    WebAPIModelProvider provider = () -> source;
    assertRefresh(WebAPIModelProvider.class, provider, WebAPIModelMetadataCollector::new, "webapi",
        "orders-api", "订单查询", "path", "/orders", source::clear);
  }

  @Test
  void uiExportPreservesRouteAndRenderer() {
    var model = new SimpleUIModel();
    model.setPath("/orders");
    model.setTitle("订单页面");
    model.setType("amis");
    var source = new LinkedHashMap<String, UIModel>(Map.of("orders-view", model));
    var provider = mock(UIModelProvider.class);
    when(provider.getUIModelMap()).thenReturn(source);
    assertRefresh(UIModelProvider.class, provider, UIModelMetadataCollector::new, "ui-model",
        "orders-view", "订单页面", "type", "amis", source::clear);
  }

  @Test
  void schemaExportPreservesLowCodePriorityAndNestedSchema() {
    var provider = new SchemaFixture();
    try (var spi = mockStatic(SPI.class)) {
      spi.when(() -> SPI.getSortedServiceStream(UISchemaProvider.class)).thenAnswer(call -> Stream.of(provider));
      var collector = new UISchemaMetadataCollector();
      var exported = collector.getAllMetadata().getFirst();
      assertEquals("ui-schema", exported.getType());
      assertEquals("/orders", exported.getName());
      assertEquals("/orders", exported.getLabel());
      assertEquals(7, exported.getPriority());
      assertEquals(MetadataSourceType.LOW_CODE, exported.getSourceType());
      assertEquals(SchemaFixture.class.getName(), exported.getSourceClassName());
      assertEquals(Map.of("type", "page", "body", Map.of("type", "table")), exported.getDetails());
      provider.schemas.clear();
      assertTrue(collector.getAllMetadata().isEmpty());
    }
  }

  @Test
  void menuExportPreservesFullNameTitleAndNavigationUrl() {
    var menu = new BaseMenuModel();
    menu.setFullName("sales.orders");
    menu.setTitle("订单菜单");
    menu.setUrl("/orders");
    var source = new LinkedHashMap<String, MenuModel>(Map.of("menu-key", menu));
    MenuModelProvider provider = () -> source;
    assertRefresh(MenuModelProvider.class, provider, MenuMetadataCollector::new, "menu-model",
        "menu-key", "订单菜单", "url", "/orders", source::clear);
  }

  @Test
  void authorityExportPreservesApiConstraints() {
    var authority = new BaseAuthority();
    authority.setFullName("orders.read");
    authority.setTitle("查询订单");
    authority.setApis(List.of("GET /orders"));
    var source = new ArrayList<Authority>(List.of(authority));
    var provider = mock(AuthorityProvider.class);
    when(provider.getAllAuthorities()).thenReturn(source);
    assertRefresh(AuthorityProvider.class, provider, AuthorityMetadataCollector::new, "authority",
        "orders.read", "查询订单", "apis", List.of("GET /orders"), source::clear);
  }

  @Test
  void moduleExportKeepsHierarchyAndMenuAssociation() {
    var module = new BaseAppModule().setFullName("sales.orders").setTitle("订单模块").setMenu("sales.orders");
    var source = new ArrayList<AppModule>(List.of(module));
    AppModuleProvider provider = () -> source;
    assertRefresh(AppModuleProvider.class, provider, AppModuleMetadataCollector::new, "app-module",
        "orders", "订单模块", "menu", "sales.orders", source::clear);
  }

  @Test
  void configurationExportKeepsDefaultAndModifiabilityContract() {
    var setting = new ConfigurationMetadata();
    setting.setKey("defaultTheme");
    setting.setDescription("默认主题");
    setting.setDefaultValue("light");
    setting.setModifiable(true);
    var source = new ArrayList<>(List.of(setting));
    ConfigurationMetadataProvider provider = () -> source;
    assertRefresh(ConfigurationMetadataProvider.class, provider, ConfigurationMetadataCollector::new, "configuration",
        "defaultTheme", "默认主题", "defaultValue", "light", source::clear);
  }

  private <T> void assertRefresh(Class<T> providerType, T provider, Supplier<MetadataCollector> factory,
      String type, String name, String label, String detailKey, Object detailValue, Runnable removeSource) {
    try (var spi = mockStatic(SPI.class)) {
      spi.when(() -> SPI.getSortedServiceStream(providerType)).thenAnswer(call -> Stream.of(provider));
      var collector = factory.get();
      var exported = collector.getAllMetadata();
      assertEquals(1, exported.size());
      var metadata = exported.getFirst();
      assertEquals(type, collector.getMetadataType());
      assertEquals(type, metadata.getType());
      assertEquals(name, metadata.getName());
      assertEquals(label, metadata.getLabel());
      assertEquals(detailValue, metadata.getDetails().get(detailKey));
      assertEquals(provider.getClass().getName(), metadata.getSourceClassName());
      assertEquals(MetadataSourceType.PRO_CODE, metadata.getSourceType());
      assertEquals(Integer.MAX_VALUE, metadata.getPriority());
      removeSource.run();
      assertTrue(collector.getAllMetadata().isEmpty(), "refresh must remove deleted metadata");
      assertEquals(detailValue, metadata.getDetails().get(detailKey), "previous exported snapshot remains readable");
    }
  }

  @LowCodeProvider
  @Priority(7)
  static class SchemaFixture implements UISchemaProvider {
    final Map<String, Object> schemas = new LinkedHashMap<>(Map.of("/orders", Map.of("type", "page", "body", Map.of("type", "table"))));
    @Override public Optional<Object> getUISchema(String path) { return Optional.ofNullable(schemas.get(path)); }
    @Override public Map<String, Object> getUISchemaMap() { return schemas; }
  }
}
