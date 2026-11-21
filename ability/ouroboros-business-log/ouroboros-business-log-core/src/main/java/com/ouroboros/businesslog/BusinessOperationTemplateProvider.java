package com.ouroboros.businesslog;

import java.util.List;

/**
 * @author liansz
 **/
public interface BusinessOperationTemplateProvider {

  BusinessOperationTemplateSource getSource();

  List<BusinessOperationTemplate> getAllTemplates();
}
