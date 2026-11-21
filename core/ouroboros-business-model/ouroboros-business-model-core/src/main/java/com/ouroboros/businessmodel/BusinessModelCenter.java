package com.ouroboros.businessmodel;

import java.util.*;
import java.util.stream.Collectors;

import com.ouroboros.util.SPI;

public class BusinessModelCenter {
  private static final List<BusinessModelBuilder> businessModelBuilders = SPI.getReversedServiceStream(BusinessModelBuilder.class).collect(Collectors.toList());
  private static Map<String, BusinessModel> businessModelMap;

  private BusinessModelCenter() {
  }

  public static final BusinessModelProvider buildInProvider = new BusinessModelProvider() {
    @Override
    public Optional<BusinessModel> getBusinessModel(String fullName) {
      return BusinessModelCenter.getBusinessModel(fullName);
    }

    @Override
    public Map<String, BusinessModel> getBusinessModelMap() {
      return BusinessModelCenter.getBusinessModelMap();
    }
  };

  public static Optional<BusinessModel> getBusinessModel(String businessModelName) {
    return Optional.ofNullable(businessModelMap).map(map -> map.get(businessModelName));
  }

  public static Map<String, BusinessModel> getBusinessModelMap() {
    return Optional.ofNullable(businessModelMap).map(Collections::unmodifiableMap).orElse(Collections.emptyMap());
  }

  public static void refresh() {
    businessModelMap = BusinessModelMetaCenter.getBusinessModelMetaMap()
        .values()
        .stream()
        .map(meta -> businessModelBuilders.stream()
            .filter(builder -> builder.support(meta))
            .findFirst()
            .map(builder -> builder.build(meta, buildInProvider))
            .orElse(null))
        .filter(Objects::nonNull)
        .collect(() -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER), (map, meta) -> map.put(meta.getFullName(), meta), Map::putAll);
  }
}
