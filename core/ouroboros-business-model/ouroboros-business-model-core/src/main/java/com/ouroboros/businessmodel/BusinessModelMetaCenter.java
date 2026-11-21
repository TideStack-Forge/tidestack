package com.ouroboros.businessmodel;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.util.SPI;

@SuppressWarnings("unused")
public class BusinessModelMetaCenter {
  public static Optional<BusinessModelMeta> getBusinessModelMeta(String name) {
    return SPI.getCachedReversedServiceStream(BusinessModelMetaProvider.class)
        .map(provider -> provider.getBusinessModelMeta(name))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  public static Map<String, BusinessModelMeta> getBusinessModelMetaMap() {
    Map<String, BusinessModelMeta> map = SPI.getCachedSortedServiceStream(BusinessModelMetaProvider.class)
        .map(BusinessModelMetaProvider::getBusinessModelMetaMap)
        .collect(() -> new TreeMap<>(String::compareToIgnoreCase), Map::putAll, Map::putAll);
    return Collections.unmodifiableMap(map);
  }
}
