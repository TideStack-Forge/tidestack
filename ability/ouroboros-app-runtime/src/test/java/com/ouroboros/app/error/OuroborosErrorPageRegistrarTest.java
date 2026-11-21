package com.ouroboros.app.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistry;

class OuroborosErrorPageRegistrarTest {

  @Test
  void registersGlobalErrorPageToOuroborosErrorEndpoint() {
    OuroborosErrorPageRegistrar registrar = new OuroborosErrorPageRegistrar();
    List<ErrorPage> errorPages = new ArrayList<>();

    registrar.registerErrorPages(new ErrorPageRegistry() {
      @Override
      public void addErrorPages(ErrorPage... errorPagesToAdd) {
        Collections.addAll(errorPages, errorPagesToAdd);
      }
    });

    assertEquals(1, errorPages.size());
    assertEquals("/error", errorPages.get(0).getPath());
    assertTrue(errorPages.get(0).isGlobal());
  }
}
