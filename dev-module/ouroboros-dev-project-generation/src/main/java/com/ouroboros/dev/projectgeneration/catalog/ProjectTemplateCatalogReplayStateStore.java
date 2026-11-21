package com.ouroboros.dev.projectgeneration.catalog;

import java.util.Optional;

public interface ProjectTemplateCatalogReplayStateStore {

  ProjectTemplateCatalogReplayState accept(String catalogStateId, long sequence, String digest);

  Optional<ProjectTemplateCatalogReplayState> current(String catalogStateId);
}
