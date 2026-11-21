package com.ouroboros.businessmodel.projection.data.advance;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Priority;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;

/**
 * @author liansz
 */
@Priority(3)
public class AuthorityFieldsMPS extends AbstractDataMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var accessControlMap = context.getSourceModel()
        .getExtraPropDeep(Map.class, "accessControl")
        .orElse(null);
    if (accessControlMap == null) {
      return;
    }

    List<String> filterNames = getAuthorityFilterNames((Map<String, Object>) accessControlMap);
    if (filterNames.isEmpty()) {
      return;
    }

    // 将访问控制配置保存到数据模型的 extraProps 中，供装饰器使用
    modifyMainDataModel(context, dataModel -> {
      dataModel.getExtraProps().put("accessControl", accessControlMap);
    });

    // 注意：字段和插件的添加现在完全由 AuthorityFieldsDecorator 处理
  }

  /**
   * 获取数据模型中的权限字段
   *
   * @param accessControlMap
   * @return
   */
  private List<String> getAuthorityFilterNames(Map<String, Object> accessControlMap) {
    return Stream.of("add", "update", "delete", "search")
        .filter(accessControlMap::containsKey)
        .map(key -> (Map<String, Object>) accessControlMap.get(key))
        .filter(accessControl -> (Boolean) accessControl.getOrDefault("isEnabled", false))
        .map(accessControl -> (Map<String, Object>) accessControl.getOrDefault("filters", Collections.emptyMap()))
        .flatMap(filtersMap -> filtersMap.keySet().stream().map(key -> Tuple.of(key, filtersMap.get(key))))
        .filter(tuple -> (Boolean) tuple._2())
        .map(Tuple2::_1)
        .distinct()
        .collect(Collectors.toList());
  }
}
