package com.ouroboros.widget.dashboard.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.widget.dashboard.model.UserDashboardTemplateModel;
import com.ouroboros.widget.dashboard.service.UserDashboardTemplateService;

@Service
public class UserDashboardTemplateServiceImpl implements UserDashboardTemplateService {
  @Autowired
  private TypedDataModel<String, UserDashboardTemplateModel> dataModel;

  @Override
  public Try<UserDashboardTemplateModel> addTemplate(UserDashboardTemplateModel template) {
    template.setId(null);
    template.setCreatedAt(LocalDateTime.now());
    template.setCreatedBy(resolveCurrentIdentity());
    return getDataModel()
        .mapTry(model -> model
            .insert(template)
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException));
  }

  @Override
  public Try<UserDashboardTemplateModel> updateTemplate(String id, UserDashboardTemplateModel template) {
    template.setId(id);
    template.setUpdatedAt(LocalDateTime.now());
    template.setUpdatedBy(resolveCurrentIdentity());
    return getDataModel()
        .mapTry(model -> model
            .update(id, template)
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .mapTry(t -> getDataModel()
            .mapTry(m -> m.get(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
            .getOrElseThrow(ex -> ex));
  }

  @Override
  public Try<Void> deleteTemplate(String id) {
    return getDataModel()
        .mapTry(model -> model.delete(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .map(v -> null);
  }

  @Override
  public Optional<UserDashboardTemplateModel> getTemplate(String id) {
    return getDataModel()
        .mapTry(model -> model.get(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .toJavaOptional();
  }

  @Override
  public List<UserDashboardTemplateModel> getAllTemplates() {
    return getDataModel()
        .mapTry(model -> model.query(Collections.emptyMap()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .getOrElse(Collections::emptyList);
  }

  private Try<TypedDataModel<String, UserDashboardTemplateModel>> getDataModel() {
    return Try.success(dataModel);
  }

  private String resolveCurrentIdentity() {
    return CurrentSecurityContextHolder.findIdentity()
        .map(SecurityIdentity::getUserIdentity)
        .orElse(null);
  }

  @Override
  public Optional<UserDashboardTemplateModel> getDefaultTemplate() {
    var where = Collections.singletonMap("isDefault", true);
    var query = Collections.<String, Object>singletonMap("where", where);
    return getDataModel()
        .mapTry(model -> model.query(query).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException))
        .map(list -> list.isEmpty()
            ? Optional.<UserDashboardTemplateModel>empty()
            : Optional.of(list.get(list.size() - 1)))
        .toJavaOptional()
        .orElseGet(Optional::empty);
  }
}
