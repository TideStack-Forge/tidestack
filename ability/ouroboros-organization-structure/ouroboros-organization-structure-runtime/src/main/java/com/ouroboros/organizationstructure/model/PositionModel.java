package com.ouroboros.organizationstructure.model;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.Position;
import com.ouroboros.organizationstructure.PositionDefinition;
import com.ouroboros.organizationstructure.entity.PositionEntity;

/**
 * 岗位业务模型 - 实现Position接口，封装业务行为
 *
 * @author songmingxu
 */
public class PositionModel implements Position {

  @Override
  public String source() {
    return "LOCAL";
  }

  private final PositionEntity entity;
  private final Supplier<OrganizationNode> parentSupplier;
  private final Supplier<Employee> leaderSupplier;
  private final Supplier<Integer> actualCountSupplier;
  private final Supplier<PositionDefinition> definitionSupplier;
  
  private OrganizationNode parent;
  private Employee leader;
  private Integer actualCount;

  public PositionModel(PositionEntity entity,
                       Supplier<OrganizationNode> parentSupplier,
                       Supplier<Employee> leaderSupplier,
                       Supplier<Integer> actualCountSupplier,
                       Supplier<PositionDefinition> definitionSupplier) {
    this.entity = entity;
    this.parentSupplier = parentSupplier;
    this.leaderSupplier = leaderSupplier;
    this.actualCountSupplier = actualCountSupplier;
    this.definitionSupplier = definitionSupplier;
  }

  @Override
  public String getId() {
    return entity.getId();
  }

  @Override
  public String getPositionName() {
    return resolveDefinitionName();
  }

  @Override
  public String getPositionKey() {
    return entity.getPositionKey();
  }

  @Override
  public String getLevel() {
    return resolveDefinition().map(PositionDefinition::getLevel).orElse(entity.getLevel());
  }

  @Override
  public String getDescription() {
    return resolveDefinition().map(PositionDefinition::getDescription).orElse(entity.getDescription());
  }

  @Override
  public Integer getHeadcount() {
    return entity.getHeadcount();
  }

  @Override
  public Integer getActualCount() {
    if (actualCount == null && actualCountSupplier != null) {
      actualCount = actualCountSupplier.get();
    }
    return actualCount;
  }

  @Override
  public String getParent() {
    return entity.getParent();
  }

  @Override
  public OrganizationNode getParentDetail() {
    if (parent == null && parentSupplier != null) {
      parent = parentSupplier.get();
    }
    return parent;
  }

  @Override
  public String getLeaderEmployee() {
    return entity.getLeaderEmployee();
  }

  @Override
  public Employee getLeaderEmployeeDetail() {
    if (leader == null && leaderSupplier != null) {
      leader = leaderSupplier.get();
    }
    return leader;
  }

  private Optional<PositionDefinition> resolveDefinition() {
    if (definitionSupplier == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(definitionSupplier.get());
  }

  private String resolveDefinitionName() {
    PositionDefinition definition = resolveDefinition().orElse(null);
    if (definition != null && StringUtils.isNotBlank(definition.getName())) {
      return definition.getName();
    }
    return entity.getPositionName();
  }
}
