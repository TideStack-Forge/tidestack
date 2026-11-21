package com.ouroboros.security.logicflow.nodes;

import java.util.Collections;
import java.util.List;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractNode;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.security.authentication.DefaultAuthentication;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;

public class AuthDisallowedFieldsNode extends AbstractNode<Object> {
  private final String resultWrapperKey;
  private final String authorityName;

  protected AuthDisallowedFieldsNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    resultWrapperKey = meta.getExtraProp(String.class, "resultWrapperKey")
        .map(String::trim)
        .orElse("");
    authorityName = meta.getExtraProp(String.class, "authorityName")
        .map(String::trim)
        .orElse("");
  }

  @Override
  protected Integer maxInputPortCount() {
    return 1;
  }

  @Override
  protected Integer minInputPortCount() {
    return 0;
  }

  @Override
  protected List<Object> doExecute(List<Object> inputs) {
    List<String> result = resolveAccessControlParams().getDisallowedFields();
    return resultWrapperKey.isEmpty()
        ? Collections.singletonList(result)
        : Collections.singletonList(Collections.singletonMap(resultWrapperKey, result));
  }

  private AccessControlParams resolveAccessControlParams() {
    OuroborosAuthentication authentication = findCurrentAuthentication();
    if (authentication == null) {
      return AccessControlParams.empty();
    }
    return authorityName.isEmpty()
        ? authentication.getAccessControlParams()
        : authentication.getAccessControlParams(authorityName);
  }

  private OuroborosAuthentication findCurrentAuthentication() {
    OuroborosAuthentication authentication = CurrentSecurityContextHolder.findAuthentication().orElse(null);
    if (authentication != null) {
      return authentication;
    }

    return CurrentSecurityContextHolder.findIdentity().map(DefaultAuthentication::new).orElse(null);
  }

  public static class Builder implements NodeBuilder<AuthDisallowedFieldsNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "authDisallowedFields".equalsIgnoreCase(meta.getType());
    }

    @Override
    public AuthDisallowedFieldsNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new AuthDisallowedFieldsNode(owner, metadata);
    }
  }
}
