package com.ouroboros.businessmodel.projection.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.PluginDescriptor;

public abstract class AbstractDataMPS extends AbstractDefaultBusinessModelProcessStep {
  public void modifyMainDataModel(ModelProjectionPipelineContext<DefaultBusinessModel> context, Consumer<DataModelMeta> modifier) {
    var model = context.getSourceModel();
    context.getModel(DataModelMeta.class, model.getFullName())
        .ifPresent(modifier);
  }

  public void addFieldsToMainDataModel(ModelProjectionPipelineContext<DefaultBusinessModel> context, List<DataModelFieldMeta> newFields) {
    if (newFields.isEmpty()) {
      return;
    }
    modifyMainDataModel(context, dataModel -> {
      var fields = new ArrayList<>(dataModel.getFields());
      fields.addAll(newFields);
      dataModel.setFields(fields);
    });
  }

  public void addFieldsToMainDataModel(ModelProjectionPipelineContext<DefaultBusinessModel> context, DataModelFieldMeta... newFields) {
    addFieldsToMainDataModel(context, Arrays.asList(newFields));
  }

  public void addPluginDescriptorToMainDataModel(ModelProjectionPipelineContext<DefaultBusinessModel> context,
                                                 PluginDescriptor... newPluginDescriptors) {
    addPluginDescriptorToMainDataModel(context, Arrays.asList(newPluginDescriptors));
  }

  public void addPluginDescriptorToMainDataModel(ModelProjectionPipelineContext<DefaultBusinessModel> context,
                                                 List<PluginDescriptor> newPluginDescriptors) {
    if (newPluginDescriptors.isEmpty()) {
      return;
    }
    modifyMainDataModel(context, dataModel -> {
      var pluginDescriptors = new ArrayList<>(dataModel.getPluginDescriptors());
      pluginDescriptors.addAll(newPluginDescriptors);
      dataModel.setPluginDescriptors(pluginDescriptors);
    });
  }

  protected String buildReferenceKey(BusinessModel businessModel) {
    var key = Stream.of(businessModel.getFullName().split("\\."))
        .filter(StringUtils::isNotBlank)
        // UpperCase First
        .map(s -> s.length() == 1 ? s.toUpperCase() : (s.substring(0, 1).toUpperCase() + s.substring(1)))
        .collect(Collectors.joining(""))
        + "Id";
    if (key.length() == 3) {
      return key;
    }
    return key.substring(0, 1).toLowerCase() + key.substring(1);
  }
}
