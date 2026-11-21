package com.ouroboros.webapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ouroboros.util.SPI;

@Component
public class WebAPIModelCatalog {
  private final Supplier<List<WebAPIModel>> supplier;

  public WebAPIModelCatalog() {
    this(() -> SPI.getSortedServiceStream(WebAPIModelProvider.class)
        .flatMap(provider -> provider.getWebAPIModelMap().values().stream())
        .collect(Collectors.toList()));
  }

  public WebAPIModelCatalog(Supplier<List<WebAPIModel>> supplier) {
    this.supplier = supplier;
  }

  public List<WebAPIModel> listAll() {
    List<WebAPIModel> models = supplier.get();
    return models != null ? new ArrayList<>(models) : Collections.emptyList();
  }
}
