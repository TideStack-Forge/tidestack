package com.ouroboros.security.rbac;

import java.security.SecureRandom;
import java.util.Base64;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.entity.UserRoleMapEntity;
import com.ouroboros.security.rbac.provider.AdministratorRoleProvider;
import com.ouroboros.security.rbac.service.UserRoleService;
import com.ouroboros.security.rbac.service.UserService;

/**
 * @author liansz
 */
@DataModelScan
@ComponentScan
public class SecurityRbacManageAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(SecurityRbacManageAutoConfiguration.class);

  @Value("${ouroboros.security.rbac.autoCreateUser.enabled:true}")
  private boolean autoCreateUser;

  @Value("${ouroboros.security.rbac.autoCreateUser.userName:admin}")
  private String autoCreateUserName;

  @Value("${ouroboros.security.rbac.autoCreateUser.password:}")
  private String autoCreateUserPassword;


  @Autowired
  private UserService userService;

  @Autowired
  private UserRoleService userRoleService;

  @Autowired
  private AdministratorRoleProvider administratorRoleProvider;

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Security RBAC Manage loaded");
  }


  @EventListener
  public void initUser(OuroborosContextRefreshedEvent event) {
    if (!autoCreateUser) {
      return;
    }

    String administratorRoleId = administratorRoleProvider.getAdministratorRoleId();

    try {
      CurrentSecurityContextHolder.withIdentity(SystemSecurityIdentities.system(), () -> {
        ensureAutoCreatedUser();
        ensureAdministratorRole(administratorRoleId);
        return null;
      });
    } catch (Exception e) {
      logger.error("自动创建用户失败", e);
    }
  }

  private void ensureAutoCreatedUser() {
    if (userService.getByUserIdentity(autoCreateUserName).isPresent()) {
      if (StringUtils.hasText(autoCreateUserPassword)) {
        userService.modifyPassword(autoCreateUserName, autoCreateUserPassword);
      }
      return;
    }

    String password = autoCreateUserPassword;
    if (!StringUtils.hasText(password)) {
      password = generateSecureRandomPassword();
      logger.warn("No password configured for auto-created user '{}'. Generated random password: {}",
          autoCreateUserName, password);
      logger.warn("IMPORTANT: Please change this password immediately after first login!");
    }

    UserEntity user = new UserEntity();
    user.setUserIdentity(autoCreateUserName);
    user.setPassword(password);
    userService.add(user);
  }

  private void ensureAdministratorRole(String administratorRoleId) {
    if (userRoleService.getByRoleIdAndUserIdentity(administratorRoleId, autoCreateUserName).isPresent()) {
      return;
    }
    UserRoleMapEntity userRoleMap = new UserRoleMapEntity();
    userRoleMap.setUser(autoCreateUserName);
    userRoleMap.setRole(administratorRoleId);
    userRoleService.add(userRoleMap);
  }

  private String generateSecureRandomPassword() {
    SecureRandom random = new SecureRandom();
    byte[] randomBytes = new byte[18];
    random.nextBytes(randomBytes);
    return Base64.getEncoder().encodeToString(randomBytes);
  }
}
