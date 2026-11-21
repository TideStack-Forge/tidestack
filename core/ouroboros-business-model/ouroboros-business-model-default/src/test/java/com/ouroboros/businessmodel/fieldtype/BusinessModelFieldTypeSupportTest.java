package com.ouroboros.businessmodel.fieldtype;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import com.ouroboros.common.app.util.SpringContextUtil;

public class BusinessModelFieldTypeSupportTest {
  private static final SpringContextUtil contextBridge = new SpringContextUtil();
  private ApplicationContext originalApplicationContext;

  @Before
  public void clearSpringContext() throws Exception {
    originalApplicationContext = (ApplicationContext) readApplicationContext();
    writeApplicationContext(null);
  }

  @After
  public void restoreSpringContext() throws Exception {
    writeApplicationContext(originalApplicationContext);
  }

  @Test
  public void shouldResolveBundledFieldTypesWithoutSpringContext() {
    assertTrue(BusinessModelFieldTypeSupport.getRuntimeMetadata("单行文本").isPresent());
    assertTrue(BusinessModelFieldTypeSupport.compileRuntime("单行文本", Collections.emptyMap()).isPresent());
  }

  @Test
  public void shouldCompileBundledDecimalFieldTypeWithoutSpringContext() {
    Map<String, Object> params = new HashMap<String, Object>();
    params.put("size", 10);
    params.put("precision", 2);

    var compiled = BusinessModelFieldTypeSupport.compileRuntime("定点数字", params);

    assertTrue(compiled.isPresent());
    assertEquals("Decimal", compiled.get().toDataFieldMeta().getType());
    assertEquals(Integer.valueOf(10), compiled.get().toDataFieldMeta().getSize());
    assertEquals(Integer.valueOf(2), compiled.get().toDataFieldMeta().getDecimalDigits());
  }

  private Object readApplicationContext() throws Exception {
    return SpringContextUtil.getApplicationContext();
  }

  private void writeApplicationContext(ApplicationContext applicationContext) throws Exception {
    contextBridge.destroy();
    contextBridge.setApplicationContext((org.springframework.context.ApplicationContext) applicationContext);
  }
}
