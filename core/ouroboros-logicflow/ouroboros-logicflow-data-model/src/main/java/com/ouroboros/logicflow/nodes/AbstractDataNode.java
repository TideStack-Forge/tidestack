package com.ouroboros.logicflow.nodes;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Either;
import io.vavr.control.Try;

import com.querydsl.core.types.Ops;
import com.ouroboros.data.normalize.OperatorFactory;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.data.model.PluginDescriptor;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.util.LogicFlowJson;

public abstract class AbstractDataNode<T, P> extends AbstractNode<T> {
  final Logger logger = LoggerFactory.getLogger(getClass());
  final protected DataModel dataModel;
  final private Class<P> paramClass;

  public AbstractDataNode(FlowModel flowModel, FlowNodeMeta meta, NodeReferenceCatalog references) {
    super(flowModel, meta);
    dataModel = getDataModel(
        meta,
        meta.getExtraProp(List.class, "plugins").orElseGet(Collections::emptyList),
        references
    );
    paramClass = getParamClass();
  }

  private DataModel getDataModel(
      FlowNodeMeta meta,
      List<Map<String, Object>> pluginDescriptorMaps,
      NodeReferenceCatalog references
  ) {
    var dataModelName = meta.getExtraProp(String.class, "dataModel");
    if (!dataModelName.isPresent() || StringUtils.isBlank(dataModelName.get())) {
      addError(new NodeDesignException("数据模型未配置", meta));
      return null;
    }

    var pluginDescriptors = pluginDescriptorMaps.stream()
        .map(map -> Try.of(() -> LogicFlowJson.OBJECT_MAPPER.convertValue(map, PluginDescriptor.class)))
        .filter(Try::isSuccess)
        .map(Try::get)
        .collect(Collectors.toList());

    var resolvedDataModelName = dataModelName.get();
    return references.resolve(DataModelReferences.KIND, resolvedDataModelName).fold(
        dataModel -> pluginDescriptors.isEmpty()
            ? dataModel
            : dataModel.withPlugins(pluginDescriptors),
        () -> {
          addError(new NodeDesignException(String.format("编译 %s 节点时发生错误，未找到数据模型：%s", meta.getName(), resolvedDataModelName), meta));
          return null;
        },
        reason -> {
          addError(new NodeDesignException(String.format(
              "编译 %s 节点时数据模型引用目录不可用（%s），无法确认数据模型：%s",
              meta.getName(),
              reason,
              resolvedDataModelName
          ), meta));
          return null;
        }
    );
  }

  private Class<P> getParamClass() {
    var actualArgumentType = getSuperclassActualArgumentType(1);
    try {
      final String typeName = actualArgumentType.getTypeName();
      return (Class<P>) Class.forName(typeName.contains("<") ? typeName.substring(0, typeName.indexOf("<")) : typeName);
    } catch (ClassNotFoundException e) {
      addError(new NodeDesignException("查找参数类型时发生错误，未找到类", meta));
    } catch (Exception e) {
      addError(new NodeDesignException("查找参数类型时发生未知错误", meta));
    }
    addError(new NodeDesignException("未找到参数类型", meta));
    return null;
  }


  /**
   * 获取超类实际参数类型
   *
   * @param index 泛型参数索引
   * @return Type
   */
  protected Type getSuperclassActualArgumentType(int index) {
    var genericSuperclass = getClass().getGenericSuperclass();
    while (!(genericSuperclass instanceof ParameterizedType) && genericSuperclass != null) {
      genericSuperclass = genericSuperclass instanceof Class<?>
          ? ((Class<?>) genericSuperclass).getGenericSuperclass()
          : null;
    }
    if (!(genericSuperclass instanceof ParameterizedType)) {
      throw new FlowRuntimeException("获取超类实际参数类型失败", null, this, null);
    }
    return ((ParameterizedType) genericSuperclass).getActualTypeArguments()[index];
  }

  @Override
  protected final List<T> doExecute(List<Object> inputs) {
    if (inputs.size() != 1) {
      throw new FlowRuntimeException("数据节点只能接受一个输入", this, inputs);
    }
    final var input = inputs.get(0);
    if (!paramClass.isInstance(input)) {
      throw new FlowRuntimeException("数据节点输入数据类型不匹配", this, inputs);
    }
    var result = doExecute(paramClass.cast(input), dataModel, inputs).getOrElseThrow(e -> e);

    return Collections.singletonList(result);
  }

  @Override
  protected Integer maxOutputPortCount() {
    return 1;
  }

  protected abstract Either<FlowRuntimeException, T> doExecute(P input, DataModel dataModel, List<Object> rawInputs);

  /**
   * 判断where条件是否是单条记录的条件
   *
   * @param where where条件
   * @return 是否是单条记录的条件
   */
  protected boolean isSingleRecordCondition(Map<String, Object> where) {
    var pks = dataModel.getPrimaryKeys().stream().map(DataModelField::getName).collect(Collectors.toList());
    AtomicReference<Function<Map<String, Object>, Stream<Tuple2<String, Object>>>> pkConditionFlatter = new AtomicReference<>();
    AtomicReference<Function<Tuple2<String, Object>, Stream<Tuple2<String, Object>>>> pkCondtionFlatMapper = new AtomicReference<>();
    pkCondtionFlatMapper.set(tuple -> {
      // 如果是主键，直接返回
      if (pks.stream().anyMatch(pk -> pk.equalsIgnoreCase(tuple._1))) {
        return Stream.of(tuple);
      }

      // 如果是操作符，则继续深入
      var op = OperatorFactory.getOperator(tuple._1);
      if (!op.isPresent()) {
        return Stream.empty();
      }
      var right = tuple._2;
      if (right instanceof Map<?, ?> map) {
        return pkConditionFlatter.get().apply((Map<String, Object>) map);
      }
      if (right instanceof Collection<?> collection) {
        return collection.stream()
            .flatMap(v -> v instanceof Map<?, ?> map1
                ? pkConditionFlatter.get().apply((Map<String, Object>) map1)
                : Stream.empty()); // 这里可能要告警
      }
      // 这里可能要告警
      return Stream.empty();
    });
    pkConditionFlatter.set(conditions -> conditions.entrySet().stream()
        .map(entry -> Tuple.of(entry.getKey(), entry.getValue()))
        .flatMap(tuple -> pkCondtionFlatMapper.get().apply(tuple)));

    // 找出所有以主键为条件的条件
    var allPkConditions = pkConditionFlatter.get().apply(where).collect(Collectors.toList());

    // 如果所有主键都在where条件中，且所有主键的值都是单值或者单值操作符，则认为是单条记录的条件
    return pks.stream().allMatch(pkName -> allPkConditions
        .stream()
        .filter(tuple -> tuple._1.equalsIgnoreCase(pkName))
        .anyMatch(tuple -> {
          var right = tuple._2;
          // 右值是集合，则认为不是单值
          if (right instanceof Collection<?>) {
            return false;
          }

          // 右值是Map，且操作符是等于，则认为是单值
          if (right instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).keySet()
                .stream()
                .anyMatch(opKey -> {
                  var op = OperatorFactory.getOperator(opKey);
                  return op.map(o -> o == Ops.EQ).orElse(false);
                });
          }

          // 右值是单值或者数字，则认为是单值
          return right instanceof CharSequence || right instanceof Number;
        }));
  }

}
