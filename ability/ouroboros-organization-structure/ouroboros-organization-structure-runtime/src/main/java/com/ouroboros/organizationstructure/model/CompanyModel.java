package com.ouroboros.organizationstructure.model;

import java.util.function.Function;

import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;

/**
 * 公司业务模型 - 实现Company接口，封装业务行为
 *
 * @author songmingxu
 */
public class CompanyModel implements Company {

  @Override
  public String source() {
    return "LOCAL";
  }

  private final OrganizationNodeEntity entity;
  private final Function<String, Employee> leaderResolver;
  private final Function<String, OrganizationNode> parentResolver;

  public CompanyModel(OrganizationNodeEntity entity,
                      Function<String, Employee> leaderResolver,
                      Function<String, OrganizationNode> parentResolver) {
    this.entity = entity;
    this.leaderResolver = leaderResolver;
    this.parentResolver = parentResolver;
  }

  @Override
  public String getId() {
    return entity.getId();
  }

  @Override
  public String getName() {
    return entity.getName();
  }

  @Override
  public String getType() {
    return entity.getType();
  }

  @Override
  public String getLeaderEmployee() {
    return entity.getLeaderEmployee();
  }

  @Override
  public Employee getLeaderEmployeeDetail() {
    String leaderId = entity.getLeaderEmployee();
    return leaderId != null && leaderResolver != null ? leaderResolver.apply(leaderId) : null;
  }

  @Override
  public String getParent() {
    return entity.getParent();
  }

  @Override
  public OrganizationNode getParentDetail() {
    String parentId = entity.getParent();
    return parentId != null && parentResolver != null ? parentResolver.apply(parentId) : null;
  }

  @Override
  public String getAdminLevel() {
    return entity.getAdminLevel();
  }

  @Override
  public String getDescription() {
    return entity.getDescription();
  }
}
