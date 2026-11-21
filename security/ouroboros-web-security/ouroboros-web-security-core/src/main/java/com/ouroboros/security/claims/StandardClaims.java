package com.ouroboros.security.claims;

/**
 * 标准 Claim 类型常量
 */
public interface StandardClaims {

  // OIDC 标准
  String SUB = "sub";
  String PUBLIC_SUBJECT_ID = "public_subject_id";
  String GENDER = "gender";
  String BIRTHDATE = "birthdate";
  String NAME = "name";
  String NICKNAME = "nickname";
  String GIVEN_NAME = "given_name";
  String FAMILY_NAME = "family_name";
  String EMAIL = "email";
  String EMAIL_VERIFIED = "email_verified";
  String PHONE_NUMBER = "phone_number";
  String PHONE_NUMBER_VERIFIED = "phone_number_verified";
  String PICTURE = "picture";
  String PREFERRED_USERNAME = "preferred_username";
  String LOCALE = "locale";
  String ZONEINFO = "zoneinfo";
  String ROLES = "roles";

  // Ouroboros 扩展
  String DEPARTMENT = "department";
  String DEPARTMENT_ID = "department_id";
  String POSITION = "position";
  String POSITION_ID = "position_id";
  String EMPLOYEE_NUMBER = "employee_number";
  String MANAGER = "manager";
  String MANAGER_ID = "manager_id";
  String ORGANIZATION = "organization";
  String ORGANIZATION_ID = "organization_id";
}
