package com.ouroboros.configuration.metadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Json;

public class ConfigurationMetadataTest {

  @Test
  public void emptyOptionalValuesUseRuntimeDefaults() {
    var metadata = Json.tryToBean(ConfigurationMetadata.class,
        "{\"key\":\"app.enabled\",\"label\":\"启用\",\"valueType\":\"boolean\",\"scope\":null,"
            + "\"obsolete\":null,\"modifiable\":null,\"nullable\":null}").get();
    assertSame(ConfigScope.BACKEND, metadata.getScope());
    assertFalse(metadata.isObsolete());
    assertFalse(metadata.isModifiable());
    assertFalse(metadata.isNullable());
  }

  @Test
  public void emptyScopeIsNotARuntimeDefault() {
    assertTrue(Json.tryToBean(ConfigurationMetadata.class,
        "{\"key\":\"app.enabled\",\"label\":\"启用\",\"valueType\":\"boolean\",\"scope\":\"\"}").isFailure());
  }

  @Test
  public void dedicatedManagementDeclarationSurvivesJsonAndMetadataMerging() {
    var metadata = Json.toBean(ConfigurationMetadata.class,
        "{\"key\":\"clients\",\"managementEntry\":\"/system-setting/auth-clients\"}");
    var ordinary = new ConfigurationMetadata();
    ordinary.setKey("clients");
    assertNull(ordinary.getManagementEntry());
    assertFalse(ordinary.isManagementTakenOver());
    assertTrue(metadata.isManagementTakenOver());

    var merged = metadata.merge(ordinary);
    assertEquals("/system-setting/auth-clients", merged.getManagementEntry());
    assertTrue(merged.isManagementTakenOver());
    var copied = Json.toBean(ConfigurationMetadata.class, Json.toJsonString(merged));
    assertEquals("/system-setting/auth-clients", copied.getManagementEntry());
    assertTrue(copied.isManagementTakenOver());
    assertEquals("clients", copied.getKey());

    ordinary.setManagementEntry("/custom-settings/clients");
    assertEquals("/custom-settings/clients", metadata.merge(ordinary).getManagementEntry());
  }

  @Test
  public void dedicatedManagementRequiresALocalAbsolutePagePath() {
    var metadata = new ConfigurationMetadata();
    for (String entry : List.of("", " ", "settings/clients", "https://example.com/settings", "//example.com/settings")) {
      assertThrows(com.ouroboros.exception.PlatformException.class, () -> metadata.setManagementEntry(entry));
    }
    metadata.setManagementEntry("/settings/clients");
    assertTrue(metadata.isManagementTakenOver());
    metadata.setManagementEntry(null);
    assertFalse(metadata.isManagementTakenOver());
  }

  @Test
  public void gettersSettersAndDefaultsCoverTheFullMetadataSurface() {
    ConfigurationMetadata metadata = new ConfigurationMetadata();
    SchemaNode editorSchema = new SchemaNode();

    metadata.setKey("security.session.idleTimeout");
    metadata.setLabel("空闲超时");
    metadata.setDescription("会话空闲超时设置");
    metadata.setModifiable(true);
    metadata.setNullable(false);
    metadata.setValueType(ConfigValueType.INT);
    metadata.setDefaultValue("300");
    metadata.setGroupPath("security.session");
    metadata.setOrder(20);
    metadata.setScope(ConfigScope.FRONTEND);
    metadata.setObsolete(true);
    metadata.setEditorSchema(editorSchema);
    metadata.setObsoleteCause("replaced by session timeout policy");
    metadata.setExtraProp("source", "system");
    metadata.setExtraProp("priority", Integer.valueOf(5));

    assertEquals("security.session.idleTimeout", metadata.getKey());
    assertEquals("空闲超时", metadata.getLabel());
    assertEquals("会话空闲超时设置", metadata.getDescription());
    assertTrue(metadata.isModifiable());
    assertFalse(metadata.isNullable());
    assertSame(ConfigValueType.INT, metadata.getValueType());
    assertEquals("300", metadata.getDefaultValue());
    assertEquals("security.session", metadata.getGroupPath());
    assertEquals(Integer.valueOf(20), metadata.getOrder());
    assertSame(ConfigScope.FRONTEND, metadata.getScope());
    assertTrue(metadata.isObsolete());
    assertSame(editorSchema, metadata.getEditorSchema());
    assertEquals("replaced by session timeout policy", metadata.getObsoleteCause());
    assertEquals("system", metadata.getExtraProp("source").get());
    assertEquals(Integer.valueOf(5), metadata.getExtraProp("priority").get());
    assertFalse(metadata.getExtraProp("missing").isPresent());

    Map<String, Object> extraProps = metadata.getExtraProps();
    assertEquals(2, extraProps.size());
    assertTrue(extraProps.containsKey("source"));
    assertTrue(extraProps.containsKey("priority"));
  }

  @Test
  public void merge_usesOverrideValuesAndFallsBackForMissingFields() {
    ConfigurationMetadata base = new ConfigurationMetadata();
    SchemaNode baseSchema = new SchemaNode();
    base.setKey("security.session.idleTimeout");
    base.setLabel("空闲超时");
    base.setDescription("会话空闲超时设置");
    base.setModifiable(true);
    base.setNullable(true);
    base.setValueType(ConfigValueType.INT);
    base.setDefaultValue("300");
    base.setGroupPath("security.session");
    base.setOrder(10);
    base.setScope(ConfigScope.BACKEND);
    base.setObsolete(false);
    base.setEditorSchema(baseSchema);
    base.setObsoleteCause("base-cause");
    base.setExtraProp("source", "base");
    base.setExtraProp("retention", "30d");

    ConfigurationMetadata override = new ConfigurationMetadata();
    SchemaNode overrideSchema = new SchemaNode();
    override.setKey("security.session.idleTimeout");
    override.setLabel("会话空闲超时");
    override.setDescription(null);
    override.setModifiable(false);
    override.setNullable(false);
    override.setValueType(ConfigValueType.LONG);
    override.setDefaultValue(null);
    override.setGroupPath("security.auth");
    override.setOrder(30);
    override.setScope(ConfigScope.FRONTEND);
    override.setObsolete(true);
    override.setEditorSchema(overrideSchema);
    override.setObsoleteCause(null);
    override.setExtraProp("source", "override");
    override.setExtraProp("scope", "frontend");

    ConfigurationMetadata merged = base.merge(override);

    assertSame(merged, merged.merge(null));
    assertEquals("security.session.idleTimeout", merged.getKey());
    assertEquals("会话空闲超时", merged.getLabel());
    assertEquals("会话空闲超时设置", merged.getDescription());
    assertFalse(merged.isModifiable());
    assertFalse(merged.isNullable());
    assertSame(ConfigValueType.LONG, merged.getValueType());
    assertEquals("300", merged.getDefaultValue());
    assertEquals("security.auth", merged.getGroupPath());
    assertEquals(Integer.valueOf(30), merged.getOrder());
    assertSame(ConfigScope.FRONTEND, merged.getScope());
    assertFalse(merged.isObsolete());
    assertEquals("base-cause", merged.getObsoleteCause());
    assertSame(overrideSchema, merged.getEditorSchema());
    assertEquals("override", merged.getExtraProp("source").get());
    assertEquals("30d", merged.getExtraProp("retention").get());
    assertEquals("frontend", merged.getExtraProp("scope").get());
    assertEquals(3, merged.getExtraProps().size());
    assertTrue(merged.getExtraProps().containsKey("source"));
    assertTrue(merged.getExtraProps().containsKey("retention"));
    assertTrue(merged.getExtraProps().containsKey("scope"));

    ConfigurationMetadata sameReference = base.merge(null);
    assertSame(base, sameReference);
    assertNotNull(sameReference.getEditorSchema());
  }

  @Test
  public void defaultValueDeserialization_preservesStringAndSerializesCompositeJsonValues() {
    ConfigurationMetadata stringDefault = Json.toBean(ConfigurationMetadata.class,
        "{\"key\":\"plain\",\"valueType\":\"string\",\"defaultValue\":\"enabled\"}");
    assertEquals("enabled", stringDefault.getDefaultValue());

    ConfigurationMetadata listDefault = Json.toBean(ConfigurationMetadata.class,
        "{\"key\":\"energy.system.standardCoalCoefficients\",\"valueType\":\"list\","
            + "\"defaultValue\":[{\"energyType\":\"electricity\"}]}");
    assertEquals("[{\"energyType\":\"electricity\"}]", listDefault.getDefaultValue());
    List<Object> listValue = ConfigValueType.LIST.convert(listDefault.getDefaultValue());
    List<Object> arrayValue = ConfigValueType.ARRAY.convert(listDefault.getDefaultValue());
    assertEquals("electricity", ((Map<?, ?>) listValue.get(0)).get("energyType"));
    assertEquals("electricity", ((Map<?, ?>) arrayValue.get(0)).get("energyType"));

    ConfigurationMetadata objectDefault = Json.toBean(ConfigurationMetadata.class,
        "{\"key\":\"energy.system.coefficient\",\"valueType\":\"object\","
            + "\"defaultValue\":{\"energyType\":\"gas\"}}");
    assertEquals("{\"energyType\":\"gas\"}", objectDefault.getDefaultValue());
    Map<String, Object> objectValue = ConfigValueType.OBJECT.convert(objectDefault.getDefaultValue());
    Map<String, Object> mapValue = ConfigValueType.MAP.convert(objectDefault.getDefaultValue());
    assertEquals("gas", objectValue.get("energyType"));
    assertEquals("gas", mapValue.get("energyType"));
  }
}
