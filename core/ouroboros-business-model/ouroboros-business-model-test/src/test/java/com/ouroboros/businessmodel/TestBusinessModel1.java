package com.ouroboros.businessmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.ouroboros.AppContext;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.fieldtype.runtime.FieldTypeRuntimeAutoConfiguration;
import com.ouroboros.util.Json;
import com.ouroboros.util.ResourceUtil;

public class TestBusinessModel1 {
  private static final SpringContextUtil contextBridge = new SpringContextUtil();
  @Test
  public void testLoadBusinessModelMeta() throws Exception {
    var previousApplicationContext = getApplicationContext();
    var fieldTypeRuntimeContext = newFieldTypeRuntimeContext();
    var resources = ResourceUtil.getResourceContentStream("META-INF/ouroboros/business-model-test-cases/BusinessModel1.json")
        .collect(Collectors.toList());
    try {
      bindApplicationContext(fieldTypeRuntimeContext);

      assertEquals(1, resources.size());
      var json = resources.get(0);
      var businessModelMeta = Json.toBean(DefaultBusinessModelMeta.class, json);
      assertEquals("BusinessModel1", businessModelMeta.getName());

      var businessModel = new DefaultBusinessModel(businessModelMeta, BusinessModelCenter.buildInProvider);
      var result = businessModel.getValidationResult();
      assertTrue(result.getErrors().toString(), result.isValid());

      var modelBundle = businessModel.buildModelBundle();
      assertNotNull(modelBundle);
      assertFalse(modelBundle.getAll().isEmpty());
    } finally {
      bindApplicationContext(previousApplicationContext);
      fieldTypeRuntimeContext.close();
    }
  }

  private static AnnotationConfigApplicationContext newFieldTypeRuntimeContext() throws Exception {
    Path homePath = Paths.get("target", "test-field-type-home").toAbsolutePath();
    Files.createDirectories(homePath);

    var context = new AnnotationConfigApplicationContext();
    context.getBeanFactory().registerSingleton("appContext", new AppContext() {
      @Override
      public String getHomePath() {
        return homePath.toString();
      }
    });
    context.register(FieldTypeRuntimeAutoConfiguration.class);
    context.refresh();
    return context;
  }

  private static ApplicationContext getApplicationContext() throws Exception {
    return SpringContextUtil.getApplicationContext();
  }

  private static void bindApplicationContext(ApplicationContext applicationContext) throws Exception {
    contextBridge.destroy();
    contextBridge.setApplicationContext((org.springframework.context.ApplicationContext) applicationContext);
  }
}
