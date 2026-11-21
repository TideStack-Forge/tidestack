package com.ouroboros.fieldtype.dev;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan
@Configuration
public class FieldTypeDevAutoConfiguration {
  @org.springframework.context.annotation.Bean
  public com.ouroboros.dev.codeintelligence.CodeContextProvider fieldTypeCodeContextProvider() { return new FieldTypeCodeContextProvider(); }


}
