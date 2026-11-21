package com.ouroboros.devhelper.dev.metadata.provider.adapter;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.modelmanage.DevelopService;
import com.ouroboros.notification.template.DefaultNotificationTemplate;

@Component
@Order(-1)
public class NotificationTemplateMetadataAdapter extends AbstractDevelopServiceAdapter<DefaultNotificationTemplate> {

  public NotificationTemplateMetadataAdapter(DevelopService<DefaultNotificationTemplate> developService) {
    super(developService);
  }

  @Override
  protected Metadata convert(Object model) {
    if (!(model instanceof DefaultNotificationTemplate template)) {
      return null;
    }

    var metadata = new LazyMetadata();
    metadata.setName(template.getName());
    metadata.setLabel(template.getTitle());
    metadata.setRawDetails(template);

    return metadata;
  }
}
