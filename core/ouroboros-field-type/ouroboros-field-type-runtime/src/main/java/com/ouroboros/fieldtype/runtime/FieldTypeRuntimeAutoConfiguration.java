package com.ouroboros.fieldtype.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.AppContext;
import com.ouroboros.fieldtype.DefaultFieldTypeCatalog;
import com.ouroboros.fieldtype.DefaultFieldTypeCompiler;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCompiler;
import com.ouroboros.fieldtype.FieldTypeDefinitionProvider;
import com.ouroboros.fieldtype.FieldTypeDefinitionReader;
import com.ouroboros.fieldtype.JarFieldTypeDefinitionProvider;
import com.ouroboros.util.SPI;
import com.ouroboros.util.Paths;

@ComponentScan
@Configuration
public class FieldTypeRuntimeAutoConfiguration {
  static final Logger logger = LoggerFactory.getLogger(FieldTypeRuntimeAutoConfiguration.class);

  @Bean
  public FieldTypeDefinitionReader fieldTypeDefinitionReader() {
    return new FieldTypeDefinitionReader();
  }

  @Bean
  public FieldTypeCompiler fieldTypeCompiler() {
    return new DefaultFieldTypeCompiler();
  }

  @Bean
  public FieldTypeDefinitionProvider jarFieldTypeDefinitionProvider(FieldTypeDefinitionReader fieldTypeDefinitionReader) {
    return new JarFieldTypeDefinitionProvider(fieldTypeDefinitionReader);
  }

  @Bean
  public FieldTypeDefinitionProvider runtimeFieldTypeDefinitionProvider(AppContext appContext,
                                                                       FieldTypeDefinitionReader fieldTypeDefinitionReader) {
    String basePath = appContext.getHomePath();
    List<String> paths = new ArrayList<String>();
    paths.add(Paths.File.of(basePath, "field-types").getPath());
    return new RuntimeFieldTypeDefinitionProvider(paths, fieldTypeDefinitionReader);
  }

  @Bean
  public FieldTypeCatalog fieldTypeCatalog(java.util.List<FieldTypeDefinitionProvider> providers,
                                           FieldTypeCompiler fieldTypeCompiler) {
    return new DefaultFieldTypeCatalog(mergeProviders(providers), fieldTypeCompiler);
  }

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Runtime Field Type Auto Configuration loaded");
  }

  private List<FieldTypeDefinitionProvider> mergeProviders(List<FieldTypeDefinitionProvider> providers) {
    LinkedHashMap<String, FieldTypeDefinitionProvider> merged = new LinkedHashMap<String, FieldTypeDefinitionProvider>();
    for (FieldTypeDefinitionProvider provider : providers == null ? Collections.<FieldTypeDefinitionProvider>emptyList() : providers) {
      if (provider != null) {
        merged.put(provider.getClass().getName(), provider);
      }
    }
    SPI.getSortedServiceStream(FieldTypeDefinitionProvider.class)
        .forEach(provider -> {
          if (provider != null) {
            merged.putIfAbsent(provider.getClass().getName(), provider);
          }
        });
    return new ArrayList<FieldTypeDefinitionProvider>(merged.values());
  }
}
