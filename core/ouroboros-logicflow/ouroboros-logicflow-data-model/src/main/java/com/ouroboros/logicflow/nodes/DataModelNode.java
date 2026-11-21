package com.ouroboros.logicflow.nodes;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;

public class DataModelNode extends AbstractNode<Object> {
  List<Object> result;

  public DataModelNode(FlowModel flowModel, FlowNodeMeta meta, NodeReferenceCatalog references) {
    super(flowModel, meta);
    var dataModelName = meta.getExtraProp(String.class, "dataModel").orElse("");
    var resultWrapperKey = meta.getExtraProp(String.class, "resultWrapperKey").orElse("");
    var dataModel = getDataModel(dataModelName, references);
    result = Collections.singletonList(resultWrapperKey.isEmpty() ? dataModel : Collections.singletonMap(resultWrapperKey, dataModel));
  }

  private DataModel getDataModel(String dataModelName, NodeReferenceCatalog references) {
    if (StringUtils.isBlank(dataModelName)) {
      addError(new NodeDesignException("数据模型未配置", meta));
      return null;
    }
    return references.resolve(DataModelReferences.KIND, dataModelName).fold(
        dataModel -> dataModel,
        () -> {
          addError(new NodeDesignException(String.format("编译 %s 节点时发生错误，未找到数据模型：%s", meta.getName(), dataModelName), meta));
          return null;
        },
        reason -> {
          addError(new NodeDesignException(String.format(
              "编译 %s 节点时数据模型引用目录不可用（%s），无法确认数据模型：%s",
              meta.getName(),
              reason,
              dataModelName
          ), meta));
          return null;
        }
    );
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    return result;
  }

  @Override
  protected Integer minInputPortCount() {
    return 0;
  }

  @Override
  protected Integer minOutputPortCount() {
    return 0;
  }

  public static class Builder implements NodeBuilder<DataModelNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "dataModel".equalsIgnoreCase(meta.getType());
    }

    @Override
    public DataModelNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new DataModelNode(owner, metadata, references);
    }
  }
}
