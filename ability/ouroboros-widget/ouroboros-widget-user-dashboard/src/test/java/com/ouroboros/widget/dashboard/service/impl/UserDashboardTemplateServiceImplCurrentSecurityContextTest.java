package com.ouroboros.widget.dashboard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.PluginDescriptor;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.User;
import com.ouroboros.widget.dashboard.model.UserDashboardTemplateModel;

import io.vavr.control.Try;

class UserDashboardTemplateServiceImplCurrentSecurityContextTest {

  private final UserDashboardTemplateServiceImpl service = new UserDashboardTemplateServiceImpl();
  private final FakeTypedDataModel dataModel = new FakeTypedDataModel();

  @BeforeEach
  void setUp() {
    dataModel.reset();
    injectField("dataModel", dataModel);

    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void addTemplateShouldUseSeamIdentity() {
    User seamUser = () -> "alice";
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(seamUser)));

    UserDashboardTemplateModel created = service.addTemplate(new UserDashboardTemplateModel().setName("demo"))
        .getOrElseThrow(ex -> new IllegalStateException(ex));

    assertThat(created.getCreatedBy()).isEqualTo("alice");
    assertThat(dataModel.getLastInsert().getCreatedBy()).isEqualTo("alice");
  }

  @Test
  void updateTemplateShouldUseSeamIdentity() {
    User seamUser = () -> "alice";
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(seamUser)));

    UserDashboardTemplateModel updated = service.updateTemplate("existing", new UserDashboardTemplateModel().setName("updated"))
        .getOrElseThrow(ex -> new IllegalStateException(ex));

    assertThat(updated.getUpdatedBy()).isEqualTo("alice");
    assertThat(dataModel.getLastUpdate().getUpdatedBy()).isEqualTo("alice");
  }

  @Test
  void addTemplateShouldIgnoreLegacyPrincipal() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("legacy-principal", "N/A", Collections.emptyList()));

    UserDashboardTemplateModel created = service.addTemplate(new UserDashboardTemplateModel().setName("demo"))
        .getOrElseThrow(ex -> new IllegalStateException(ex));

    assertThat(created.getCreatedBy()).isNull();
    assertThat(dataModel.getLastInsert().getCreatedBy()).isNull();
  }

  @Test
  void updateTemplateShouldIgnoreLegacyPrincipal() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("legacy-principal", "N/A", Collections.emptyList()));

    UserDashboardTemplateModel template = new UserDashboardTemplateModel().setName("updated");
    UserDashboardTemplateModel updated = service.updateTemplate("existing", template)
        .getOrElseThrow(ex -> new IllegalStateException(ex));

    assertThat(updated.getUpdatedBy()).isNull();
    assertThat(dataModel.getLastUpdate().getUpdatedBy()).isNull();
  }

  private static final class FakeTypedDataModel implements TypedDataModel<String, UserDashboardTemplateModel> {
    private final Map<String, UserDashboardTemplateModel> storage = new HashMap<>();
    private UserDashboardTemplateModel lastInsert;
    private UserDashboardTemplateModel lastUpdate;
    private int counter;

    void reset() {
      storage.clear();
      lastInsert = null;
      lastUpdate = null;
      counter = 0;
    }

    UserDashboardTemplateModel getLastInsert() {
      return lastInsert;
    }

    UserDashboardTemplateModel getLastUpdate() {
      return lastUpdate;
    }

    @Override
    public DataModel getDataModel() {
      return null;
    }

    @Override
    public Try<UserDashboardTemplateModel> insert(UserDashboardTemplateModel template) {
      String id = Optional.ofNullable(template.getId()).orElseGet(() -> "generated-" + ++counter);
      template.setId(id);
      lastInsert = template;
      storage.put(id, template);
      return Try.success(template);
    }

    @Override
    public Try<List<UserDashboardTemplateModel>> batchInsert(Collection<UserDashboardTemplateModel> dataList) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Long> delete(String id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Long> delete(Collection<String> ids) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Long> delete(Map<String, Object> where) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Long> update(String id, UserDashboardTemplateModel template) {
      lastUpdate = template;
      storage.put(id, template);
      return Try.success(1L);
    }

    @Override
    public Try<Long> update(String id, Map<String, Object> data) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Long> update(Collection<String> ids, Map<String, Object> data) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Long> update(Map<String, Object> where, Map<String, Object> data) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<Long> count(Map<String, Object> where) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<UserDashboardTemplateModel> get(String id) {
      return Try.success(storage.get(id));
    }

    @Override
    public Try<UserDashboardTemplateModel> get(String id, Map<String, Object> statement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<List<UserDashboardTemplateModel>> query(Collection<String> ids) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<List<UserDashboardTemplateModel>> query(Map<String, Object> statement) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<List<UserDashboardTemplateModel>> query(Collection<String> select, Map<String, Object> where) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<List<UserDashboardTemplateModel>> query(Collection<String> select, Map<String, Object> where, String orderBy) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Try<List<UserDashboardTemplateModel>> query(Collection<String> select, Map<String, Object> where,
        String orderBy, Integer offset, Integer limit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TypedDataModel<String, UserDashboardTemplateModel> withPlugins(List<PluginDescriptor> pluginDescriptors) {
      return this;
    }

    @Override
    public TypedDataModel<String, UserDashboardTemplateModel> withoutPlugins(List<String> pluginNames) {
      return this;
    }

    @Override
    public TypedDataModel<String, UserDashboardTemplateModel> withoutPlugins() {
      return this;
    }

    @Override
    public boolean hasPlugin(String name) {
      return false;
    }
  }

  private void injectField(String name, Object value) {
    try {
      Field field = UserDashboardTemplateServiceImpl.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(service, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
