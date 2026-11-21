package com.ouroboros.organizationstructure.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import com.ouroboros.organizationstructure.entity.AdminLevelEntity;
import com.ouroboros.organizationstructure.service.AdminLevelService;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;

public class AdminLevelSeedRunner implements ApplicationRunner {

  private static final List<AdminLevelEntity> SEEDS = Arrays.asList(
      seed("DEPARTMENT", "部门", "DEPT", 1),
      seed("DIVISION", "处室", "DEPT", 2),
      seed("SECTION", "科室", "DEPT", 3),
      seed("UNIT", "股", "DEPT", 4),
      seed("GROUP", "集团", "ORG", 1),
      seed("SUB_GROUP", "子集团", "ORG", 2),
      seed("BRANCH", "分公司", "ORG", 3));

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final AdminLevelService adminLevelService;
  private final boolean verifyExistingAdminLevels;

  public AdminLevelSeedRunner(AdminLevelService adminLevelService, boolean verifyExistingAdminLevels) {
    this.adminLevelService = adminLevelService;
    this.verifyExistingAdminLevels = verifyExistingAdminLevels;
  }

  @Override
  public void run(ApplicationArguments args) {
    seedAdminLevels();
    if (verifyExistingAdminLevels) {
      try {
        List<String> warnings = adminLevelService.verifyExistingOrganizationNodeAdminLevels();
        for (String warning : warnings) {
          logger.warn(warning);
        }
      } catch (Exception exception) {
        throw new IllegalStateException("行政级别引用校验失败", exception);
      }
    }
  }

  private void seedAdminLevels() {
    for (AdminLevelEntity seed : SEEDS) {
      try {
        Optional<AdminLevelEntity> existingOptional = adminLevelService.findByCode(seed.getCode());
        if (!existingOptional.isPresent()) {
          createSeed(seed);
        }
      } catch (Exception exception) {
        throw new IllegalStateException("行政级别种子初始化失败: " + seed.getCode(), exception);
      }
    }
  }

  private void createSeed(AdminLevelEntity seed) {
    CurrentSecurityContextHolder.withIdentity(SystemSecurityIdentities.system(),
        () -> adminLevelService.create(copy(seed)));
  }

  private AdminLevelEntity copy(AdminLevelEntity source) {
    AdminLevelEntity copy = new AdminLevelEntity();
    copy.setId(source.getId());
    copy.setCode(source.getCode());
    copy.setName(source.getName());
    copy.setScope(source.getScope());
    copy.setRank(source.getRank());
    copy.setEnabled(source.getEnabled());
    copy.setDescription(source.getDescription());
    copy.setCreatedBy(source.getCreatedBy());
    copy.setCreatedAt(source.getCreatedAt());
    copy.setUpdatedBy(source.getUpdatedBy());
    copy.setUpdatedAt(source.getUpdatedAt());
    return copy;
  }

  private static AdminLevelEntity seed(String code, String name, String scope, Integer rank) {
    AdminLevelEntity entity = new AdminLevelEntity();
    entity.setCode(code);
    entity.setName(name);
    entity.setScope(scope);
    entity.setRank(rank);
    entity.setEnabled(Boolean.TRUE);
    return entity;
  }
}
