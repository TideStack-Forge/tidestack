package com.ouroboros.uischema;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.uimodel.microweb.MicroWebModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmisLibraryWebAppInfoContributorTest {

  @Test
  void shouldOnlyReturnLibraryMicroWebModels() {
    AmisLibraryWebAppInfoContributor contributor = new AmisLibraryWebAppInfoContributor();

    Map<String, Object> webAppInfo = contributor.getWebAppInfo();
    List<?> componentLibraries = (List<?>) webAppInfo.get("componentLibraries");

    assertEquals(2, componentLibraries.size());
    assertTrue(componentLibraries.get(0) instanceof MicroWebModel);
    assertTrue(componentLibraries.get(1) instanceof MicroWebModel);
    assertEquals("library-one", ((MicroWebModel) componentLibraries.get(0)).getName());
    assertEquals(MicroWebModel.MicroWebType.LIBRARY, ((MicroWebModel) componentLibraries.get(0)).getType());
    assertEquals("library-two", ((MicroWebModel) componentLibraries.get(1)).getName());
    assertEquals(MicroWebModel.MicroWebType.LIBRARY, ((MicroWebModel) componentLibraries.get(1)).getType());
  }
}
