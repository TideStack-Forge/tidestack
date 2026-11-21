package com.ouroboros.organizationstructure.service;

import java.util.List;
import java.util.Optional;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.organizationstructure.entity.AdminLevelEntity;

public interface AdminLevelService {

  AdminLevelEntity create(AdminLevelEntity entity) throws OuroborosException;

  Boolean update(String id, AdminLevelEntity entity) throws OuroborosException;

  Boolean delete(String id) throws OuroborosException;

  Boolean disable(String id) throws OuroborosException;

  Optional<AdminLevelEntity> findById(String id) throws OuroborosException;

  Optional<AdminLevelEntity> findByCode(String code) throws OuroborosException;

  List<AdminLevelEntity> findAll() throws OuroborosException;

  List<AdminLevelEntity> findByScope(String scope) throws OuroborosException;

  List<AdminLevelEntity> findEnabledByScope(String scope) throws OuroborosException;

  boolean isReferencedByOrgNodes(String code) throws OuroborosException;

  void validateAdminLevelReference(String code, String nodeType) throws OuroborosException;

  List<String> verifyExistingOrganizationNodeAdminLevels() throws OuroborosException;
}
