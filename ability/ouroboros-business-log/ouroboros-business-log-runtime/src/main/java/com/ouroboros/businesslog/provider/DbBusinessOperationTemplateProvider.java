package com.ouroboros.businesslog.provider;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.springframework.stereotype.Component;

import com.ouroboros.businesslog.BusinessOperationTemplate;
import com.ouroboros.businesslog.BusinessOperationTemplateProvider;
import com.ouroboros.businesslog.BusinessOperationTemplateSource;
import com.ouroboros.businesslog.service.BusinessOperationTemplateService;

/**
 * @author liansz
 **/
@Component
public class DbBusinessOperationTemplateProvider {

  private final BusinessOperationTemplateService service;

  public DbBusinessOperationTemplateProvider(BusinessOperationTemplateService service) {
    this.service = service;
    Proxy.provider = this;
  }

  public List<BusinessOperationTemplate> getAllTemplates() {
    return Collections.unmodifiableList(
        service.searchAll().stream().map(
            BusinessOperationTemplate.class::cast
        ).collect(Collectors.toList())
    );
  }

  @Priority(-20)
  public static class Proxy implements BusinessOperationTemplateProvider {
    private static DbBusinessOperationTemplateProvider provider;

    @Override
    public BusinessOperationTemplateSource getSource() {
      return BusinessOperationTemplateSource.DATABASE;
    }

    @Override
    public List<BusinessOperationTemplate> getAllTemplates() {
      return Optional.ofNullable(provider).map(
          DbBusinessOperationTemplateProvider::getAllTemplates
      ).orElse(Collections.emptyList());
    }
  }
}
