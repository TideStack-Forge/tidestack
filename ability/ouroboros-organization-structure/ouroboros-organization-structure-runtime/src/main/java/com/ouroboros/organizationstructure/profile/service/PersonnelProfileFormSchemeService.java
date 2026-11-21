package com.ouroboros.organizationstructure.profile.service;

import java.util.List;
import java.util.Optional;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.organizationstructure.profile.entity.PersonnelProfileFormSchemeEntity;

public interface PersonnelProfileFormSchemeService {

  PersonnelProfileFormSchemeEntity create(PersonnelProfileFormSchemeEntity entity, String operatedBy) throws OuroborosException;

  Boolean update(String id, PersonnelProfileFormSchemeEntity entity, String operatedBy) throws OuroborosException;

  Boolean delete(String id, String operatedBy) throws OuroborosException;

  Optional<PersonnelProfileFormSchemeEntity> getById(String id) throws OuroborosException;

  Optional<PersonnelProfileFormSchemeEntity> getById(String id, String appName) throws OuroborosException;

  List<PersonnelProfileFormSchemeEntity> searchAll(String appName) throws OuroborosException;

  Optional<PersonnelProfileFormSchemeEntity> findDefaultScheme(String appName) throws OuroborosException;
}
