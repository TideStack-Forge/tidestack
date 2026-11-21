package com.ouroboros.dev.projectgeneration.catalog;

public class ProjectTemplateCatalogUnavailableException extends RuntimeException {

  public ProjectTemplateCatalogUnavailableException(String message) {
    super(message);
  }

  public ProjectTemplateCatalogUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
