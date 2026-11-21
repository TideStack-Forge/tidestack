package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.modelmanage.ModelSummary;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

@Component
@Order(-1)
public class BusinessModelMetadataAdapter extends AbstractDevelopServiceAdapter<BusinessModelMeta> {

  public BusinessModelMetadataAdapter(DevelopService<BusinessModelMeta> developService) {
    super(developService);
  }

  @Override
  protected Metadata convert(Object model) {
    if (!(model instanceof ModelFileMetaWrapper<?> businessModelMeta)) {
      return null;
    }

    ModelSummary vo = (ModelSummary) businessModelMeta.getModel();
    var metadata = new LazyMetadata();
    metadata.setName(vo.getName());
    metadata.setLabel(vo.getTitle());
    metadata.setRawDetails(vo);

    return metadata;
  }
}
