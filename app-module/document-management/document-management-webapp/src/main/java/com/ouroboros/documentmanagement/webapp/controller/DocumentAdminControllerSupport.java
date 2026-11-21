package com.ouroboros.documentmanagement.webapp.controller;

import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.service.DocumentLibraryAdminFacade;
import com.ouroboros.documentmanagement.webapp.dto.DocumentLibraryAdminDTO;
import com.ouroboros.web.WebApiResult;

abstract class DocumentAdminControllerSupport {

  protected final DocumentLibraryAdminFacade facade;

  protected DocumentAdminControllerSupport(DocumentLibraryAdminFacade facade) {
    this.facade = facade;
  }

  protected DocumentLibraryAdminDTO toAdminDTO(DocumentLibrary library) {
    return DocumentLibraryAdminDTO.fromDocumentLibrary(library);
  }

  protected <T> WebApiResult<T> notFound(String message) {
    return WebApiResult.error(404, message);
  }
}
