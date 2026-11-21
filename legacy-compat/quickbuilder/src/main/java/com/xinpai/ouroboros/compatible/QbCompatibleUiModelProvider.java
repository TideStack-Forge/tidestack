package com.xinpai.ouroboros.compatible;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.xinpai.common.dao.Where;
import com.xinpai.common.jackson.JsonObject;
import com.xinpai.common.rbac.service.IResourceService;
import com.xinpai.common.service.model.JsonModel;
import com.ouroboros.annotation.LowCodeProvider;
import com.xinpai.ouroboros.uimodel.BaseUiModel;
import com.xinpai.ouroboros.uimodel.UiModel;
import com.xinpai.ouroboros.uimodel.UiModelProvider;
import com.xinpai.ouroboros.uiroute.enums.RouteTypeEnum;
import com.xinpai.qb.core.context.QbApplicationContext;

/**
 * @author liansz
 * @createTime 2022/12/20 23:33
 */
@Component
public class QbCompatibleUiModelProvider {

  @Autowired
  private QbApplicationContext applicationContext;

  @Autowired
  private IResourceService resourceService;

  public QbCompatibleUiModelProvider() {
    Proxy.provider = this;
  }

  public Optional<UiModel> getUiModel(String path) {
    Where where = Where.build()
        .field("type").equal(1)
        .and()
        .field("app_id").equal(applicationContext.getAppId())
        .and()
        .field("data -> '$.url'").equal(path);

    return Optional.ofNullable(resourceService.searchOne(where)).map(this::resourceModelToUiModel);
  }

  public Map<String, UiModel> getUiModelMap() {
    Where where = Where.build()
        .field("type").equal(1)
        .and()
        .field("app_id").equal(applicationContext.getAppId());

    // 查询所有菜单资源，按ord排序并转换成UI模型
    return resourceService.searchList(where).stream()
        .sorted(Comparator.comparingInt(o -> ObjectUtils.defaultIfNull(o.getIntValue("ord"), 0)))
        .map(this::resourceModelToUiModel)
        .filter(model -> StringUtils.isNotEmpty(model.getType()) && StringUtils.isNotEmpty(model.getPath()))
        .collect(Collectors.toMap(UiModel::getPath, r -> r, (r1, r2) -> r1));
  }

  /**
   * 资源模型转UI模型
   *
   * @param model
   * @return
   */
  private UiModel resourceModelToUiModel(JsonModel model) {
    BaseUiModel uiModel = new BaseUiModel();
    uiModel.setTitle(model.getString("name"));
    uiModel.setPath(model.getString("path"));
    uiModel.setType(model.getString("routeType"));

    JsonObject extraPropJsonObject = model.getJsonObject("extraProp");
    if (extraPropJsonObject != null) {
      extraPropJsonObject.forEach(uiModel::setExtraProp);
    }

    if (StringUtils.isEmpty(uiModel.getType())) {
      if (StringUtils.isNotEmpty(uiModel.getPath()) && uiModel.getPath().startsWith("/q/")) {
        uiModel.setType(RouteTypeEnum.QUICK.value());
      }

      if (StringUtils.isNotEmpty(uiModel.getPath()) && !uiModel.getPath().startsWith("/q/")) {
        uiModel.setType(RouteTypeEnum.MICRO.value());
        uiModel.setExtraProp("entry", "/micro-app-other");
      }
    }

    return uiModel;
  }

  @LowCodeProvider
  public static class Proxy implements UiModelProvider {
    private static QbCompatibleUiModelProvider provider;

    @Override
    public Optional<UiModel> getUiModel(String path) {
      return Optional.ofNullable(provider).flatMap(map -> map.getUiModel(path));
    }

    @Override
    public Map<String, UiModel> getUiModelMap() {
      return Optional.ofNullable(provider).map(QbCompatibleUiModelProvider::getUiModelMap).orElse(Collections.emptyMap());
    }
  }
}
