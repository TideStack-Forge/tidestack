package com.ouroboros.organizationstructure;

import java.util.List;
import java.util.Optional;

public interface OrganizationNodeProvider {
  Optional<? extends OrganizationNode> getById(String id);

  List<? extends OrganizationNode> getAll();
}
