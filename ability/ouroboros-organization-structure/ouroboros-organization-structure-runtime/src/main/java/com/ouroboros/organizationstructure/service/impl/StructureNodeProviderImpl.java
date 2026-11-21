package com.ouroboros.organizationstructure.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.OrganizationAggregator;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.OrganizationNodeProvider;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;
import com.ouroboros.organizationstructure.enums.OrganizationNodeType;
import com.ouroboros.organizationstructure.model.CompanyModel;
import com.ouroboros.organizationstructure.model.DepartmentModel;
import com.ouroboros.organizationstructure.service.StructureManageService;

/**
 * 结构性节点 Provider 实现
 *
 * @author songmingxu
 */
@Service
public class StructureNodeProviderImpl implements OrganizationNodeProvider {

  @Autowired
  private StructureManageService structureManageService;

  @Autowired
  @Lazy
  private OrganizationAggregator organizationAggregator;

  @Autowired
  @Lazy
  private EmployeeAggregator employeeAggregator;

  @Override
  public Optional<? extends OrganizationNode> getById(String id) {
    return structureManageService.findById(id)
        .map(this::toModel);
  }

  @Override
  public List<? extends OrganizationNode> getAll() {
    return structureManageService.findAll().stream()
        .map(this::toModel)
        .collect(Collectors.toList());
  }

  private OrganizationNode toModel(OrganizationNodeEntity entity) {
    String type = entity.getType();

    if (OrganizationNodeType.COMPANY.getType().equals(type)) {
      return new CompanyModel(
          entity,
          id -> employeeAggregator.getById(id).orElse(null),
          id -> organizationAggregator.getById(id).orElse(null)
      );
    } else {
      return new DepartmentModel(
          entity,
          id -> employeeAggregator.getById(id).orElse(null),
          id -> organizationAggregator.getById(id).orElse(null),
          id -> organizationAggregator.getNearestCompany(id).orElse(null)
      );
    }
  }
}
