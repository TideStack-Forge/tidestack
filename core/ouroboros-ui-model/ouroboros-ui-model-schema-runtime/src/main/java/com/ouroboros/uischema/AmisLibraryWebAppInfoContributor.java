package com.ouroboros.uischema;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ouroboros.WebAppInfoContributor;
import com.ouroboros.uimodel.microweb.MicroWebModel;
import com.ouroboros.uimodel.microweb.MicroWebModelProvider;
import com.ouroboros.util.SPI;

/**
 * @author liansz
 */
public class AmisLibraryWebAppInfoContributor implements WebAppInfoContributor {

  @Override
  public Map<String, Object> getWebAppInfo() {
    return Collections.singletonMap(
        "componentLibraries",
        SPI.getSortedServiceStream(MicroWebModelProvider.class)
            .map(MicroWebModelProvider::getAllMicroWebModel)
            .flatMap(List::stream)
            .filter(model -> model.getType() == MicroWebModel.MicroWebType.LIBRARY)
            .collect(Collectors.toList())
    );
  }
}
