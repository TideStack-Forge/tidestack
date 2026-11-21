package com.ouroboros.businessmodel.projection.authority;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.event.BusinessEventAuthorities;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.model.BusinessModelAuthority;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;

/** Publishes Event production and complete-payload disclosure as explicit platform Authorities. */
@Priority(76)
public class BehaviorEventMPS extends AbstractDefaultBusinessModelProcessStep {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    model.getEvents().forEach(event -> {
      BusinessEventIdentity identity = new BusinessEventIdentity(model.getFullName(), event.getName());
      BusinessModelAuthority emit = new BusinessModelAuthority(BusinessEventAuthorities.emit(identity),
          model.getTitle() + " - 产生 " + event.getTitle());
      BusinessModelAuthority read = new BusinessModelAuthority(BusinessEventAuthorities.read(identity),
          model.getTitle() + " - 读取 " + event.getTitle());
      List<AccessControlDefine.Field> fields = new ArrayList<>();
      addPayloadFields(fields, "", event.getPayloadSchema());
      read.setACCanControlFields(true);
      read.addACControlledFields(fields);
      context.putModel(Authority.class, emit.getFullName(), emit);
      context.putModel(Authority.class, read.getFullName(), read);
    });
  }

  private void addPayloadFields(List<AccessControlDefine.Field> fields, String prefix, Map<?, ?> schema) {
    if (schema.get("properties") instanceof Map<?, ?> properties) {
      properties.forEach((name, value) -> {
        String path = prefix.isEmpty() ? String.valueOf(name) : prefix + "." + name;
        if (value instanceof Map<?, ?> property) {
          fields.add(new AccessControlDefine.Field(path, String.valueOf(property.get("type")), path, null));
          addPayloadFields(fields, path, property);
        }
      });
    }
    if (schema.get("items") instanceof Map<?, ?> items) {
      addPayloadFields(fields, prefix, items);
    }
  }
}
