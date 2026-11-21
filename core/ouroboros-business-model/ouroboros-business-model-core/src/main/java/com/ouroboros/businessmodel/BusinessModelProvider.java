package com.ouroboros.businessmodel;

import java.util.Map;
import java.util.Optional;

public interface BusinessModelProvider {
  Optional<BusinessModel> getBusinessModel(String fullName);

  Map<String, BusinessModel> getBusinessModelMap();
}
