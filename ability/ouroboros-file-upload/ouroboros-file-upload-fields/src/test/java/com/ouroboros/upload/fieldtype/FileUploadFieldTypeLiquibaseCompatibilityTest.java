package com.ouroboros.upload.fieldtype;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.*;

import org.junit.jupiter.api.Test;

import com.ouroboros.data.migration.TableBuilder;
import com.ouroboros.data.migration.datatype.DataTypeBuilder;
import com.ouroboros.data.model.*;
import com.ouroboros.data.validation.Rule;
import com.ouroboros.fieldtype.*;
import com.ouroboros.util.SPI;

import liquibase.database.Database;
import liquibase.structure.core.DataType;

class FileUploadFieldTypeLiquibaseCompatibilityTest {

  @Test
  void bundledUploadFieldTypesShouldResolveToLiquibaseTypes() throws Exception {
    var catalog = new DefaultFieldTypeCatalog(
        Collections.singletonList(new JarFieldTypeDefinitionProvider(new FieldTypeDefinitionReader())),
        new DefaultFieldTypeCompiler());

    assertFieldTypeBuildsLiquibaseType(catalog, "文件", params("dirCode", "default", "accept", ".txt"), "VARCHAR", 255);
    assertFieldTypeBuildsLiquibaseType(catalog, "多文件", params("dirCode", "default", "accept", ".txt"), "TEXT", null);
  }

  private static void assertFieldTypeBuildsLiquibaseType(DefaultFieldTypeCatalog catalog,
                                                         String fieldTypeName,
                                                         Map<String, Object> params,
                                                         String expectedType,
                                                         Integer expectedSize) throws Exception {
    var compileResult = catalog.compile(FieldTypeCatalogContext.runtime(), fieldTypeName, params);
    assertTrue(compileResult.isRight(), () -> fieldTypeName + " compile failed: " + compileResult.getLeft().getMessage());
    var fieldMeta = compileResult.get().toDataFieldMeta();
    assertNotNull(fieldMeta, () -> fieldTypeName + " dataType missing");
    fieldMeta.setName("field");
    fieldMeta.setLabel(fieldTypeName);
    fieldMeta.setRawName("field");

    var field = new StubField(fieldMeta);
    assertNotEquals("Unknown", field.getValueType().getName(), () -> fieldTypeName + " resolved unknown value type");
    assertTrue(SPI.getCachedSortedServiceStream(DataTypeBuilder.class)
        .anyMatch(builder -> builder.support(null, field)), () -> fieldTypeName + " has no SQL DataTypeBuilder");
    var dataType = buildDataType(field);

    assertEquals(expectedType, dataType.getTypeName(), fieldTypeName);
    if (expectedSize != null) {
      assertEquals(expectedSize, dataType.getColumnSize(), fieldTypeName);
    }
  }

  private static DataType buildDataType(DataModelField field) throws Exception {
    Method method = TableBuilder.class.getDeclaredMethod("buildDataType", Database.class, DataModelField.class);
    method.setAccessible(true);
    return (DataType) method.invoke(null, null, field);
  }

  private static Map<String, Object> params(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return map;
  }

  private static class StubField implements DataModelField {
    private final DataModelFieldMeta fieldMeta;

    StubField(DataModelFieldMeta fieldMeta) {
      this.fieldMeta = fieldMeta;
    }

    @Override public String getName() { return fieldMeta.getName(); }
    @Override public String getLabel() { return fieldMeta.getLabel(); }
    @Override public String getDescription() { return fieldMeta.getDescription(); }
    @Override public String getType() { return fieldMeta.getType(); }
    @Override public String getRawName() { return fieldMeta.getRawName(); }
    @Override public String getRawType() { return fieldMeta.getRawType(); }
    @Override public ValueType<?> getValueType() { return ValueTypeFactory.getValueType(this).orElse(null); }
    @Override public Object getDefaultValue(Map<String, Object> context) { return null; }
    @Override public List<Rule> getRules() { return Collections.emptyList(); }
    @Override public Integer getDecimalDigits() { return fieldMeta.getDecimalDigits(); }
    @Override public Integer getSize() { return fieldMeta.getSize(); }
    @Override public Boolean getIsNullable() { return true; }
    @Override public Boolean getIsUnsigned() { return false; }
    @Override public Boolean getIsAutoIncrement() { return false; }
    @Override public Boolean getIsUnique() { return false; }
    @Override public Map<String, Object> getExtraProps() { return fieldMeta.getExtraProps(); }
    @Override public Optional<Object> getExtraProp(String name) { return fieldMeta.getExtraProp(name); }
    @Override public DataModel getDataModel() { return null; }
  }
}
