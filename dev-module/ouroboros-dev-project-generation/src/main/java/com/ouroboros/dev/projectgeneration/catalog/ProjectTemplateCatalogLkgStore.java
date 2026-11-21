package com.ouroboros.dev.projectgeneration.catalog;

import java.util.Optional;

public interface ProjectTemplateCatalogLkgStore {

  Optional<LastKnownGoodCatalog> load();

  void save(LastKnownGoodCatalog catalog);
}
