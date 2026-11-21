package com.ouroboros.organizationstructure.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.ouroboros.organizationstructure.AppointmentAggregator;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.OrganizationAggregator;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.Position;
import com.ouroboros.organizationstructure.PositionDefinitionCenter;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;
import com.ouroboros.organizationstructure.entity.PositionEntity;
import com.ouroboros.organizationstructure.entity.AppointmentEntity;
import com.ouroboros.organizationstructure.enums.OrganizationNodeType;
import com.ouroboros.organizationstructure.model.CompanyModel;
import com.ouroboros.organizationstructure.model.DepartmentModel;
import com.ouroboros.organizationstructure.model.EmployeeModel;
import com.ouroboros.organizationstructure.model.PositionModel;
import com.ouroboros.organizationstructure.model.AppointmentModel;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.UserSubjectCenter;

/**
 * 组织架构模型转换器 - 负责 Entity 到 Model 的转换，打破循环依赖
 */
@Component
public class OrganizationModelResolver {

  private final UserCenter userCenter;
  private final UserSubjectCenter userSubjectCenter;
  private final AppointmentAggregator appointmentAggregator;
  private final OrganizationAggregator organizationAggregator;
  private final EmployeeAggregator employeeAggregator;
  private final PositionDefinitionCenter positionDefinitionCenter;

  public OrganizationModelResolver(
      @Lazy OrganizationAggregator organizationAggregator,
      @Lazy EmployeeAggregator employeeAggregator,
      @Lazy AppointmentAggregator appointmentAggregator) {
    this.userCenter = UserCenter.instance();
    this.userSubjectCenter = UserSubjectCenter.instance();
    this.organizationAggregator = organizationAggregator;
    this.employeeAggregator = employeeAggregator;
    this.appointmentAggregator = appointmentAggregator;
    this.positionDefinitionCenter = PositionDefinitionCenter.instance();
  }

  public EmployeeModel toEmployeeModel(EmployeeEntity entity) {
    String employeeId = entity.getId();

    return new EmployeeModel(
        entity,
        () -> userSubjectCenter.getBySubject("employee", employeeId)
            .flatMap(us -> userCenter.getUser(us.getUserIdentity()))
            .orElse(null),
        appointmentAggregator);
  }

  public OrganizationNode toOrganizationNode(OrganizationNodeEntity entity) {
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

  public OrganizationNode toPositionModel(PositionEntity entity) {
    String parentId = entity.getParent();
    String leaderId = entity.getLeaderEmployee();
    
    return new PositionModel(
        entity,
        parentId == null ? () -> null : () -> organizationAggregator.getById(parentId).orElse(null),
        leaderId == null ? () -> null : () -> employeeAggregator.getById(leaderId).orElse(null),
        () -> appointmentAggregator.getCurrentByPosition(entity.getId()).size(),
        () -> positionDefinitionCenter.getDefinition(entity.getPositionKey()).orElse(null)
    );
  }

  public AppointmentModel toAppointmentModel(AppointmentEntity entity) {
    String employeeId = entity.getEmployee();
    String positionId = entity.getPosition();
    
    return new AppointmentModel(
        entity,
        StringUtils.isEmpty(employeeId) ? () -> null : () -> employeeAggregator.getById(employeeId).orElse(null),
        StringUtils.isEmpty(positionId) ? () -> null : () -> {
          return organizationAggregator.getById(positionId)
              .filter(node -> node instanceof Position)
              .map(node -> (Position) node)
              .orElse(null);
        }
    );
  }
}
