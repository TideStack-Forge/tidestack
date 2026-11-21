package com.ouroboros.security.rbac;

import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.ouroboros.appmodule.AppModuleIntegrator;
import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.SubjectCenter;
import com.ouroboros.security.rbac.UserSubject;
import com.ouroboros.security.rbac.UserSubjectCenter;

/**
 * @author liansz
 */
@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class);
  }

  @Bean
  public AnonymousApiProvider getAnonymousAccessApiProvider() {
    return () -> Collections.singletonList("* /**");
  }

  @Bean
  public AppModuleIntegrator appModuleIntegrator() {
    return Collections::emptyList;
  }

  @Bean
  public com.ouroboros.security.profile.UserProfilePolicySource profilePolicies() {
    return () -> new com.ouroboros.security.profile.UserProfilePolicySource.Snapshot("test:protection-v1",
        com.ouroboros.security.profile.UserProfilePolicy.defaults());
  }

  @Bean
  public com.ouroboros.cache.CacheManager profileCache() { return new com.ouroboros.cache.MemoryCacheManager(); }

  @Bean
  public UserCenter userCenter() {
    return new UserCenter() {
      @Override
      public Optional<User> getUser(String userIdentity) {
        return Optional.empty();
      }

      @Override
      public List<User> getUsers(java.util.Collection<String> userIdentities) {
        return Collections.emptyList();
      }

      @Override
      public List<User> getAllUsers() {
        return Collections.emptyList();
      }
    };
  }

  @Bean
  public SubjectCenter subjectCenter() {
    return new SubjectCenter() {
      @Override
      public Optional<Subject> getByTypeAndId(String type, String id) {
        return Optional.empty();
      }

      @Override
      public Optional<Subject> getByTypeAndId(String type, String id, String providerName) {
        return Optional.empty();
      }

      @Override
      public List<Subject> getByTypeAndIds(String type, Collection<String> ids) {
        return Collections.emptyList();
      }

      @Override
      public List<Subject> getByType(String type) {
        return Collections.emptyList();
      }

      @Override
      public List<String> getSupportedTypes() {
        return Collections.emptyList();
      }

      @Override
      public List<String> getProviderNames(String type) {
        return Collections.emptyList();
      }
    };
  }

  @Bean
  public UserSubjectCenter userSubjectCenter() {
    return new UserSubjectCenter() {
      @Override
      public Optional<UserSubject> getByUser(String userIdentity) {
        return Optional.empty();
      }

      @Override
      public Optional<UserSubject> getByUser(String userIdentity, String subjectType) {
        return Optional.empty();
      }

      @Override
      public List<UserSubject> getAllByUser(String userIdentity) {
        return Collections.emptyList();
      }

      @Override
      public Map<String, List<UserSubject>> getAllByUsers(Collection<String> userIdentities) {
        return Collections.emptyMap();
      }

      @Override
      public Map<String, UserSubject> getByUsers(Collection<String> userIdentities, String subjectType) {
        return Collections.emptyMap();
      }

      @Override
      public Optional<UserSubject> getBySubject(String subjectType, String subjectId) {
        return Optional.empty();
      }

      @Override
      public List<UserSubject> getAll() {
        return Collections.emptyList();
      }
    };
  }
}
