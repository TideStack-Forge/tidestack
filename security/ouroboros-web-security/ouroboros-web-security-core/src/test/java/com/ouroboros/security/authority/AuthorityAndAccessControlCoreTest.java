package com.ouroboros.security.authority;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlFilterDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.DefaultAccessControlParams;
import com.ouroboros.security.authority.accesscontrol.SimpleAccessControlFilterDefine;
import com.ouroboros.security.authority.accesscontrol.SuperAccessControlParams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorityAndAccessControlCoreTest {

  @Test
  void accessControlDefineAndFieldEqualityBehaveAsExpected() {
    var f1 = new AccessControlDefine.Field("dept", "Dept", "string", "d1");
    var f2 = new AccessControlDefine.Field("dept", "Dept2", "string", "d2");
    assertTrue(f1.equals(f1));
    assertTrue(f1.equals(f2));
    assertFalse(f1.equals(null));

    var filter = newFilter("deptFilter", "Dept");
    var a = define(true,
        Arrays.asList(new AccessControlDefine.Field("commonA", "A", "string", null)),
        Arrays.asList(f1),
        Arrays.asList(filter));
    var b = define(false,
        Arrays.asList(new AccessControlDefine.Field("commonB", "B", "string", null)),
        Arrays.asList(new AccessControlDefine.Field("region", "R", "string", null)),
        Arrays.asList(filter));

    var merged = AccessControlDefine.merge(a, b);
    assertTrue(merged.getCanControlFields());
    assertEquals(2, merged.getCommonFields().size());
    assertEquals(2, merged.getControlledFields().size());
    assertEquals(1, merged.getFilterDefine().size());

    var empty = AccessControlDefine.empty();
    assertFalse(empty.getCanControlFields());
    assertSame(empty, AccessControlDefine.merge(empty, null));
    assertSame(a, AccessControlDefine.merge(a, AccessControlDefine.empty()));
  }

  @Test
  void accessControlParamsCombineOrAndMergeBranches() {
    var p1 = params(Arrays.asList("a", "b"), Arrays.asList("x", "y"), mapOf("k1", "v1"));
    var p2 = params(Arrays.asList("c"), Arrays.asList("y", "z"), mapOf("k2", "v2"));

    var or = AccessControlParams.or(p1, p2);
    assertEquals(3, or.getAllowedFields().size());
    assertEquals(java.util.Set.of("a", "b", "c"), or.getFieldConstraint().fields());
    assertTrue(or.getFilters().containsKey("OR"));

    var and = AccessControlParams.and(p1, p2);
    assertTrue(and.getFilters().containsKey("AND"));

    var merged = AccessControlParams.merge(p1, p2);
    assertEquals(3, merged.getAllowedFields().size());
    assertTrue(merged.getFilters().containsKey("k1"));
    assertTrue(merged.getFilters().containsKey("k2"));

    var empty = AccessControlParams.empty();
    assertEquals(com.ouroboros.security.authority.accesscontrol.FieldConstraint.Mode.NONE, empty.getFieldConstraint().mode());
    org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.security.exception.AuthorizationDeniedException.class, empty::getAllowedFields);

    var superParams = new SuperAccessControlParams(define(true,
        Arrays.asList(new AccessControlDefine.Field("common", "c", "string", null)),
        Arrays.asList(new AccessControlDefine.Field("ctrl", "x", "string", null)),
        Collections.<AccessControlFilterDefine>emptyList()));
    assertEquals(com.ouroboros.security.authority.accesscontrol.FieldConstraint.Mode.ALL,
        AccessControlParams.or(superParams, p2).getFieldConstraint().mode());
    assertEquals(java.util.Set.of("c"),
        AccessControlParams.and(superParams, p2).getFieldConstraint().fields());
    assertEquals(List.of("*"), superParams.getAllowedFields());
    assertTrue(superParams.getDisallowedFields().isEmpty());
    assertTrue(superParams.getFilters().isEmpty());
  }

  @Test
  void defaultAccessControlParamsAndAuthoritiesBehaveCorrectly() {
    var deptFilter = newFilter("dept", "部门");
    var define = define(true,
        Arrays.asList(new AccessControlDefine.Field("publicName", "Public", "string", null)),
        Arrays.asList(new AccessControlDefine.Field("salary", "Salary", "number", null),
            new AccessControlDefine.Field("dept", "Dept", "string", null)),
        Arrays.asList(deptFilter));

    var setting = new HashMap<String, Object>();
    setting.put("$fieldControl", Arrays.asList("salary"));
    setting.put("dept", "rd");
    setting.put("unknown", "x");
    setting.put("$ignored", true);

    var params = new DefaultAccessControlParams(define, setting);
    assertEquals(Arrays.asList("salary", "publicName"), params.getAllowedFields());
    assertEquals(Collections.singletonList("dept"), params.getDisallowedFields());
    org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.security.exception.AuthorizationDeniedException.class, params::getFilters);
    setting.remove("unknown");
    setting.remove("$ignored");
    assertEquals(Collections.singletonMap("dept", "rd"), params.getFilters());

    var base = new BaseAuthority();
    base.setFullName("app.order.read");
    base.setTitle("Read");
    base.setAccessControlDefine(define);
    base.setApis(Arrays.asList("GET /api/orders"));
    base.setUiViews(Arrays.asList("order:list"));
    base.setMenuItems(Arrays.asList("menu:order"));

    assertEquals("app.order", base.getParent());
    assertEquals("read", base.getName());
    assertEquals(Integer.valueOf(2), base.getLevel());
    assertSame(define, base.getAccessControlDefine());

    var copied = new DefaultAuthority(base);
    assertEquals("app.order.read", copied.getFullName());
    assertEquals("Read", copied.getTitle());
    assertEquals(1, copied.getApis().size());

    var b = simpleAuthority("", "Fallback", AccessControlDefine.empty(),
        Arrays.asList("POST /api/orders"), Arrays.asList("order:create"), Arrays.asList("menu:create"));
    var mergedAuthority = new MergedAuthority(base, b);
    assertEquals("app.order.read", mergedAuthority.getFullName());
    assertEquals("Read", mergedAuthority.getTitle());
    assertEquals(2, mergedAuthority.getApis().size());

    var ga1 = granted("perm.order", base, params(Arrays.asList("a"), Arrays.asList("x"), mapOf("f1", 1)));
    var ga2 = granted("perm.order", b, params(Arrays.asList("b"), Arrays.asList("y"), mapOf("f2", 2)));
    var mergedGranted = OuroborosGrantedAuthority.merge(ga1, ga2);
    assertEquals("perm.order", mergedGranted.getAuthority());
    assertTrue(mergedGranted.getAccessControlParams().getFilters().containsKey("OR"));

    var wrapped = new DefaultOuroborosGrantedAuthority(ga1);
    assertEquals("perm.order", wrapped.getAuthority());
    assertSame(base, wrapped.getAuthorityObject());
    assertNotNull(wrapped.getAccessControlParams());

    assertFalse(AuthorityCenter.getAuthority("not-exist").isPresent());
    assertNotNull(AuthorityCenter.getAllAuthority());
  }

  private static AccessControlParams params(final List<String> allow, final List<String> deny, final Map<String, Object> filters) {
    return new AccessControlParams() {
      @Override
      public List<String> getAllowedFields() {
        return allow;
      }

      @Override
      public List<String> getDisallowedFields() {
        return deny;
      }

      @Override
      public Map<String, Object> getFilters() {
        return filters;
      }
    };
  }

  private static Map<String, Object> mapOf(String k, Object v) {
    var map = new HashMap<String, Object>();
    map.put(k, v);
    return map;
  }

  private static AccessControlFilterDefine newFilter(String name, String label) {
    var define = new SimpleAccessControlFilterDefine();
    define.setExtraProp("name", name);
    define.setExtraProp("label", label);
    return new AccessControlFilterDefine() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getLabel() {
        return label;
      }

      @Override
      public String getDescription() {
        return null;
      }

      @Override
      public String getDataType() {
        return "string";
      }

      @Override
      public String getOriginalFieldType() {
        return "string";
      }

      @Override
      public String getOriginalFieldName() {
        return name;
      }

      @Override
      public List<com.ouroboros.uischema.schema.SchemaNode> getSettingSchema() {
        return Collections.emptyList();
      }

      @Override
      public Map<String, Object> getExtraProps() {
        return define.getExtraProps();
      }

      @Override
      public Object getExtraProp(String key) {
        return define.getExtraProp(key);
      }
    };
  }

  private static AccessControlDefine define(final boolean control,
                                            final List<AccessControlDefine.Field> commons,
                                            final List<AccessControlDefine.Field> controlled,
                                            final List<AccessControlFilterDefine> filters) {
    return new AccessControlDefine() {
      @Override
      public Boolean getCanControlFields() {
        return Boolean.valueOf(control);
      }

      @Override
      public List<Field> getCommonFields() {
        return commons;
      }

      @Override
      public List<Field> getControlledFields() {
        return controlled;
      }

      @Override
      public List<AccessControlFilterDefine> getFilterDefine() {
        return filters;
      }
    };
  }

  private static Authority simpleAuthority(final String fullName,
                                           final String title,
                                           final AccessControlDefine define,
                                           final List<String> apis,
                                           final List<String> views,
                                           final List<String> menus) {
    return new Authority() {
      @Override
      public String getFullName() {
        return fullName;
      }

      @Override
      public String getTitle() {
        return title;
      }

      @Override
      public AccessControlDefine getAccessControlDefine() {
        return define;
      }

      @Override
      public List<String> getApis() {
        return apis;
      }

      @Override
      public List<String> getUiViews() {
        return views;
      }

      @Override
      public List<String> getMenuItems() {
        return menus;
      }
    };
  }

  private static OuroborosGrantedAuthority granted(final String authority,
                                                   final Authority authorityObject,
                                                   final AccessControlParams params) {
    return new OuroborosGrantedAuthority() {
      @Override
      public String getAuthority() {
        return authority;
      }

      @Override
      public Authority getAuthorityObject() {
        return authorityObject;
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return params;
      }
    };
  }
}
