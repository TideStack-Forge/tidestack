package com.ouroboros.organizationstructure.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.OrganizationNodeProvider;
import com.ouroboros.organizationstructure.service.PositionManageService;

/**
 * 岗位节点 Provider 实现
 *
 * @author songmingxu
 */
@Service
public class PositionNodeProviderImpl implements OrganizationNodeProvider {

  @Autowired
  private PositionManageServiceImpl positionManageService;

  @Override
  public Optional<? extends OrganizationNode> getById(String id) {
    return positionManageService.findById(id)
        .map(positionManageService::toModel);
  }

  @Override
  public List<? extends OrganizationNode> getAll() {
    return positionManageService.findAll().stream()
        .map(positionManageService::toModel)
        .collect(Collectors.toList());
  }
}
