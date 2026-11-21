package com.ouroboros.businessmodel.schema;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.SPI;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIModelProvider;

/** Resolves actions against the same candidate models used by validation and projection. */
public final class CompositeViewWebApis {
  private CompositeViewWebApis() {
  }

  public static Map<String, WebAPIModel> available(BusinessModelProvider provider) {
    Stream<WebAPIModel> businessApis = provider.getBusinessModelMap().values().stream()
        .filter(BusinessModel::isEnabled).filter(model -> model.compositeView().isEmpty())
        .flatMap(model -> model.buildModelBundle().getModelMap(WebAPIModel.class).values().stream());
    Stream<WebAPIModel> providedApis = SPI.getCachedServiceStream(WebAPIModelProvider.class)
        .filter(source -> !source.isBusinessModelProjection())
        .flatMap(source -> source.getWebAPIModelMap().values().stream());
    Map<String, WebAPIModel> available = new LinkedHashMap<>();
    providedApis.forEach(api -> available.put(api.getApi(), api));
    businessApis.forEach(api -> available.put(api.getApi(), api));
    return Map.copyOf(available);
  }

  public static WebAPIModel resolve(BusinessModelProvider provider, String reference) {
    List<WebAPIModel> matches = available(provider).values().stream()
        .filter(api -> reference.equals(api.getOperationId()) || reference.equals(api.getApi()))
        .toList();
    if (matches.size() != 1) {
      throw new BusinessException("composite-view.action-api-invalid: " + reference
          + (matches.isEmpty() ? " 不存在或不可用" : " 引用不唯一"));
    }
    return matches.getFirst();
  }
}
