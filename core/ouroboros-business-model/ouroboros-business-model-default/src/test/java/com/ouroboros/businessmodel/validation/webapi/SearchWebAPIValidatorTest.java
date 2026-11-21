package com.ouroboros.businessmodel.validation.webapi;

import static org.junit.Assert.*;

import java.util.*;
import java.util.stream.Collectors;

import org.junit.Test;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.BusinessModelFieldMeta;
import com.ouroboros.businessmodel.meta.BusinessModelRelationMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.validation.ValidationContext;

public class SearchWebAPIValidatorTest {

  @Test
  public void skipsValidationWhenSearchApiDisabled() {
    var model = createCustomerModelWithApis(stringMap(
        "search", stringMap(
            "isEnabled", false,
            "populate", "missingRelation"
        )
    ));

    var context = new ValidationContext<DefaultBusinessModel>(model);
    new SearchWebAPIValidator().doValidate(context);

    assertTrue(context.getErrors().isEmpty());
  }

  @Test
  public void acceptsStringPopulateWhenRelationsExist() {
    var model = createCustomerModelWithApis(stringMap(
        "search", stringMap(
            "isEnabled", true,
            "populate", "owner,department"
        )
    ));

    var context = new ValidationContext<DefaultBusinessModel>(model);
    new SearchWebAPIValidator().doValidate(context);

    assertTrue(context.getErrors().isEmpty());
  }

  @Test
  public void reportsInvalidNestedSelectField() {
    var model = createCustomerModelWithApis(stringMap(
        "search", stringMap(
            "isEnabled", true,
            "populate", stringMap(
                "owner", stringMap(
                    "select", Arrays.asList("id", "missingField")
                )
            )
        )
    ));

    var context = new ValidationContext<DefaultBusinessModel>(model);
    new SearchWebAPIValidator().doValidate(context);

    assertHasErrorContaining(context, "搜索");
    assertHasErrorContaining(context, "owner");
    assertHasErrorContaining(context, "select");
    assertHasErrorContaining(context, "missingField");
  }

  @Test
  public void reportsInvalidPopulateListMember() {
    var model = createCustomerModelWithApis(stringMap(
        "search", stringMap(
            "isEnabled", true,
            "populate", Arrays.<Object>asList("owner", Integer.valueOf(1))
        )
    ));

    var context = new ValidationContext<DefaultBusinessModel>(model);
    new SearchWebAPIValidator().doValidate(context);

    assertHasErrorContaining(context, "搜索");
    assertHasErrorContaining(context, "[1]");
  }

  @Test
  public void reportsNestedWhereErrorsForMapPopulate() {
    var model = createCustomerModelWithApis(stringMap(
        "search", stringMap(
            "isEnabled", true,
            "populate", stringMap(
                "owner", stringMap(
                    "where", stringMap(
                        "missingField", stringMap("$eq", "x")
                    )
                )
            )
        )
    ));

    var context = new ValidationContext<DefaultBusinessModel>(model);
    new SearchWebAPIValidator().doValidate(context);

    assertHasErrorContaining(context, "搜索");
    assertHasErrorContaining(context, "owner");
    assertHasErrorContaining(context, "where");
    assertHasErrorContaining(context, "missingField");
  }

  @Test
  public void reportsNestedLimitAndOffsetErrors() {
    var model = createCustomerModelWithApis(stringMap(
        "search", stringMap(
            "isEnabled", true,
            "populate", stringMap(
                "owner", stringMap(
                    "populate", stringMap(
                        "department", stringMap(
                            "limit", Integer.valueOf(0),
                            "offset", Integer.valueOf(-1)
                        )
                    )
                )
            )
        )
    ));

    var context = new ValidationContext<DefaultBusinessModel>(model);
    new SearchWebAPIValidator().doValidate(context);

    assertHasErrorContaining(context, "owner");
    assertHasErrorContaining(context, "department");
    assertHasErrorContaining(context, "limit");
    assertHasErrorContaining(context, "offset");
  }

  private static DefaultBusinessModel createCustomerModelWithApis(Map<String, Object> apis) {
    var provider = new InMemoryBusinessModelProvider();

    var departmentModel = createDepartmentModel(provider);
    var userModel = createUserModel(provider);
    var customerModel = createCustomerModel(provider, apis);

    provider.add(departmentModel);
    provider.add(userModel);
    provider.add(customerModel);

    return customerModel;
  }

  private static DefaultBusinessModel createCustomerModel(BusinessModelProvider provider, Map<String, Object> apis) {
    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setNamespace("crm");
    modelMeta.setName("customer");
    modelMeta.setTitle("客户");
    modelMeta.setFields(Arrays.asList(
        field("id", "客户ID", "单行文本", true, true),
        field("name", "客户名称", "单行文本", true, false)
    ));
    modelMeta.setRelations(Arrays.asList(
        relation("owner", "负责人", "master", "sys.user"),
        relation("department", "部门", "master", "crm.department")
    ));
    modelMeta.setExtraProp("apis", apis);
    return new DefaultBusinessModel(modelMeta, provider);
  }

  private static DefaultBusinessModel createUserModel(BusinessModelProvider provider) {
    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setNamespace("sys");
    modelMeta.setName("user");
    modelMeta.setTitle("用户");
    modelMeta.setFields(Arrays.asList(
        field("id", "用户ID", "单行文本", true, true),
        field("name", "用户名", "单行文本", true, false),
        field("status", "状态", "单行文本", false, false)
    ));
    modelMeta.setRelations(Collections.singletonList(
        relation("department", "部门", "master", "crm.department")
    ));
    return new DefaultBusinessModel(modelMeta, provider);
  }

  private static DefaultBusinessModel createDepartmentModel(BusinessModelProvider provider) {
    var modelMeta = new DefaultBusinessModelMeta();
    modelMeta.setNamespace("crm");
    modelMeta.setName("department");
    modelMeta.setTitle("部门");
    modelMeta.setFields(Arrays.asList(
        field("id", "部门ID", "单行文本", true, true),
        field("name", "部门名称", "单行文本", true, false)
    ));
    modelMeta.setRelations(Collections.<BusinessModelRelationMeta>emptyList());
    return new DefaultBusinessModel(modelMeta, provider);
  }

  private static BusinessModelFieldMeta field(String name, String title, String type, boolean required, boolean primaryKey) {
    var fieldMeta = new BusinessModelFieldMeta();
    fieldMeta.setName(name);
    fieldMeta.setTitle(title);
    fieldMeta.setType(type);
    fieldMeta.setIsRequired(required);
    fieldMeta.setIsPrimaryKey(primaryKey);
    return fieldMeta;
  }

  private static BusinessModelRelationMeta relation(String name, String title, String type, String relatedModel) {
    var relationMeta = new BusinessModelRelationMeta();
    relationMeta.setName(name);
    relationMeta.setTitle(title);
    relationMeta.setType(type);
    relationMeta.setModel(relatedModel);
    return relationMeta;
  }

  private static void assertHasErrorContaining(ValidationContext<DefaultBusinessModel> context, String fragment) {
    assertTrue("Expected error containing '" + fragment + "', but got: " + errorMessages(context),
        context.getErrors().stream().map(Throwable::getMessage).anyMatch(message -> message != null && message.contains(fragment)));
  }

  private static String errorMessages(ValidationContext<DefaultBusinessModel> context) {
    return context.getErrors().stream().map(Throwable::getMessage).collect(Collectors.joining(" | "));
  }

  private static Map<String, Object> stringMap(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
    }
    return map;
  }

  private static class InMemoryBusinessModelProvider implements BusinessModelProvider {
    private final Map<String, BusinessModel> models = new LinkedHashMap<String, BusinessModel>();

    private void add(DefaultBusinessModel model) {
      models.put(model.getFullName(), model);
    }

    @Override
    public Optional<BusinessModel> getBusinessModel(String fullName) {
      return Optional.ofNullable(models.get(fullName));
    }

    @Override
    public Map<String, BusinessModel> getBusinessModelMap() {
      return Collections.unmodifiableMap(models);
    }
  }
}
