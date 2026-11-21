package com.ouroboros.organizationstructure.service.impl;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.organizationstructure.AppointmentType;
import com.ouroboros.organizationstructure.dto.request.CreateAppointmentRequest;
import com.ouroboros.organizationstructure.entity.AppointmentEntity;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;
import com.ouroboros.organizationstructure.entity.PositionEntity;
import com.ouroboros.organizationstructure.service.AdminLevelService;
import com.ouroboros.organizationstructure.service.AppointmentManageService;
import com.ouroboros.organizationstructure.service.EmployeeManageService;
import com.ouroboros.organizationstructure.service.OrganizationFacadeService;
import com.ouroboros.organizationstructure.service.PositionManageService;
import com.ouroboros.organizationstructure.service.StructureManageService;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.PositionDefinitionCenter;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.UserSubject;
import com.ouroboros.security.rbac.UserSubjectCenter;
import com.ouroboros.security.rbac.entity.UserSubjectEntity;
import com.ouroboros.security.rbac.service.UserSubjectService;

/**
 * 组织架构门面服务实现
 *
 * @author songmingxu
 */
@Service
public class OrganizationFacadeServiceImpl implements OrganizationFacadeService {

  private final StructureManageService structureManageService;
  private final PositionManageService positionManageService;
  private final AppointmentManageService appointmentManageService;
  private final EmployeeManageService employeeManageService;
  private final UserSubjectService userSubjectService;
  private final UserSubjectCenter userSubjectCenter;
  private final EmployeeAggregator employeeAggregator;
  private final PositionDefinitionCenter positionDefinitionCenter;
  private final UserCenter userCenter;
  private final AdminLevelService adminLevelService;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private java.util.List<com.ouroboros.organizationstructure.service.OrganizationMutationPolicy> mutationPolicies = java.util.List.of();

  public OrganizationFacadeServiceImpl(StructureManageService structureManageService,
                                       PositionManageService positionManageService,
                                       AppointmentManageService appointmentManageService,
                                       EmployeeManageService employeeManageService,
                                       UserSubjectService userSubjectService,
                                       UserSubjectCenter userSubjectCenter,
                                       EmployeeAggregator employeeAggregator,
                                       UserCenter userCenter) {
    this(structureManageService,
        positionManageService,
        appointmentManageService,
        employeeManageService,
        userSubjectService,
        userSubjectCenter,
        employeeAggregator,
        PositionDefinitionCenter.instance(),
        userCenter,
        null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public OrganizationFacadeServiceImpl(StructureManageService structureManageService,
                                       PositionManageService positionManageService,
                                       AppointmentManageService appointmentManageService,
                                       EmployeeManageService employeeManageService,
                                       UserSubjectService userSubjectService,
                                       UserSubjectCenter userSubjectCenter,
                                       EmployeeAggregator employeeAggregator,
                                       PositionDefinitionCenter positionDefinitionCenter,
                                       UserCenter userCenter,
                                       AdminLevelService adminLevelService) {
    this.structureManageService = structureManageService;
    this.positionManageService = positionManageService;
    this.appointmentManageService = appointmentManageService;
    this.employeeManageService = employeeManageService;
    this.userSubjectService = userSubjectService;
    this.userSubjectCenter = userSubjectCenter;
    this.employeeAggregator = employeeAggregator;
    this.positionDefinitionCenter = positionDefinitionCenter;
    this.userCenter = userCenter;
    this.adminLevelService = adminLevelService;
  }

  // ==================== 部门操作 ====================

  @Override
  public void assignToDepartment(String employeeId, String departmentId) throws OuroborosException {
    PositionEntity defaultPosition = positionManageService.getOrCreateDefaultPosition(departmentId);
    appointmentManageService.create(employeeId, defaultPosition.getId(), AppointmentType.PRIMARY, LocalDate.now());
  }

  @Override
  public void transferToDepartment(String employeeId, String newDepartmentId) throws OuroborosException {
    checkPrimaryAppointmentMutation(employeeId);
    // 结束当前主岗任职
    appointmentManageService.findPrimaryAppointment(employeeId)
        .ifPresent(appointment -> appointmentManageService.endAppointment(appointment.getId()));
    
    // 分配到新部门
    assignToDepartment(employeeId, newDepartmentId);
  }

  @Override
  public String createDepartment(OrganizationNodeEntity department, boolean withDefaultPosition) throws OuroborosException {
    validateAdminLevel(department, "department");
    OrganizationNodeEntity created = structureManageService.addDepartment(department);
    
    if (withDefaultPosition) {
      positionManageService.getOrCreateDefaultPosition(created.getId());
    }
    
    return created.getId();
  }

  // ==================== 岗位任命操作 ====================

  @Override
  public void assignToPosition(String employeeId, String positionId) throws OuroborosException {
    appointmentManageService.create(employeeId, positionId, AppointmentType.PRIMARY, LocalDate.now());
  }

  @Override
  public void addSecondaryPosition(String employeeId, String positionId) throws OuroborosException {
    appointmentManageService.create(employeeId, positionId, AppointmentType.SECONDARY, LocalDate.now());
  }

  @Override
  public void setActingPosition(String employeeId, String positionId, LocalDate startDate, LocalDate endDate) throws OuroborosException {
    appointmentManageService.create(employeeId, positionId, AppointmentType.ACTING, startDate, endDate);
  }

  @Override
  public void transferToPosition(String employeeId, String newPositionId) throws OuroborosException {
    checkPrimaryAppointmentMutation(employeeId);
    if (!employeeManageService.findById(employeeId).isPresent()) {
      throw new BusinessException(404, "员工不存在");
    }
    if (!positionManageService.findById(newPositionId).isPresent()) {
      throw new BusinessException(404, "岗位不存在");
    }
    appointmentManageService.findPrimaryAppointment(employeeId)
        .ifPresent(appointment -> appointmentManageService.endAppointment(appointment.getId()));
    assignToPosition(employeeId, newPositionId);
  }

  @Override
  public void removeAppointment(String appointmentId) {
    appointmentManageService.remove(appointmentId);
  }

  // ==================== 组织节点管理 ====================

  @Override
  public String createCompany(OrganizationNodeEntity company) throws OuroborosException {
    validateAdminLevel(company, "company");
    return structureManageService.addCompany(company).getId();
  }

  @Override
  public void updateNode(String id, OrganizationNodeEntity node) throws OuroborosException {
    if (!structureManageService.findById(id).isPresent()) {
      throw new BusinessException(404, "组织节点不存在");
    }
    OrganizationNodeEntity current = structureManageService.findById(id).orElse(null);
    if (current != null && node != null && !Objects.equals(current.getAdminLevel(), node.getAdminLevel())) {
      validateAdminLevel(node, current.getType());
    }
    structureManageService.update(id, node);
  }

  @Override
  public void moveNode(String id, String newParentId) throws OuroborosException {
    OrganizationNodeEntity entity = new OrganizationNodeEntity();
    entity.setParent(newParentId);
    structureManageService.update(id, entity);
  }

  @Override
  public void deleteNode(String id) throws OuroborosException {
    // 简化级联删除：依赖数据库外键约束或手动处理
    // TODO: 完善级联删除逻辑
    structureManageService.delete(id);
  }

  // ==================== 岗位管理 ====================

  @Override
  public String createPosition(PositionEntity position) throws OuroborosException {
    String positionKey = position.getPositionKey();
    if (positionKey == null || positionKey.trim().isEmpty()) {
      java.util.List<String> matchingKeys = positionDefinitionCenter.getDefinitions().stream()
          .filter(definition -> definition.getName() != null && definition.getName().equals(position.getPositionName()))
          .map(definition -> definition.getKey())
          .collect(java.util.stream.Collectors.toList());
      positionKey = matchingKeys.size() == 1 ? matchingKeys.get(0) : null;
    } else if (!positionDefinitionCenter.getDefinition(positionKey).isPresent()) {
      throw new BusinessException(400, "岗位定义不存在");
    }
    if (positionKey == null || positionKey.trim().isEmpty()) {
      return positionManageService.add(position).getId();
    }
    return positionManageService.assignDefinitionToDepartment(
        position.getParent(),
        positionKey,
        position.getPositionName(),
        position.getHeadcount(),
        position.getLeaderEmployee()).getId();
  }

  private void validateAdminLevel(OrganizationNodeEntity node, String nodeType) throws OuroborosException {
    if (adminLevelService == null || node == null) {
      return;
    }
    String adminLevel = node.getAdminLevel();
    if (adminLevel == null || adminLevel.trim().isEmpty()) {
      return;
    }
    adminLevelService.validateAdminLevelReference(adminLevel, nodeType);
  }

  @Override
  public void updatePosition(String id, PositionEntity position) throws OuroborosException {
    Optional<PositionEntity> existing = positionManageService.findById(id);
    if (!existing.isPresent()) {
      throw new BusinessException(404, "岗位不存在");
    }
    if (existing.get().getPositionKey() != null) {
      boolean changesName = position.getPositionName() != null
          && !java.util.Objects.equals(existing.get().getPositionName(), position.getPositionName());
      boolean changesDescription = position.getDescription() != null
          && !java.util.Objects.equals(existing.get().getDescription(), position.getDescription());
      if (changesName || changesDescription) {
        throw new BusinessException(400, "全局岗位定义的名称和描述请在岗位目录中维护");
      }
    }
    positionManageService.update(id, position);
  }

  @Override
  public void deletePosition(String id) throws OuroborosException {
    positionManageService.delete(id);
  }

  // ==================== 员工管理 ====================

  @Override
  public String createEmployee(EmployeeEntity employee, String departmentId) throws OuroborosException {
    com.ouroboros.organizationstructure.entity.EmployeeEntity created = employeeManageService.insert(employee);

    if (departmentId != null) {
      PositionEntity defaultPosition = positionManageService.getOrCreateDefaultPosition(departmentId);
      appointmentManageService.create(created.getId(), defaultPosition.getId(), AppointmentType.PRIMARY, LocalDate.now());
    }

    return created.getId();
  }

  @Override
  public void updateEmployee(String id, EmployeeEntity employee) throws OuroborosException {
    if (!Boolean.TRUE.equals(employeeManageService.updateById(id, employee))) {
      throw new BusinessException(404, "员工不存在");
    }
  }

  @Override
  public void deleteEmployee(String id) throws OuroborosException {
    mutationPolicies.forEach(policy -> policy.employee(id, null, true));
    // 删除员工前先取消用户关联
    userSubjectCenter.getBySubject("employee", id)
        .ifPresent(userSubject -> userSubjectService.deleteByUserAndSubjectType(userSubject.getUserIdentity(), "employee"));

    if (!Boolean.TRUE.equals(employeeManageService.deleteById(id))) {
      throw new BusinessException(404, "员工不存在");
    }
  }

  private void checkPrimaryAppointmentMutation(String employeeId) {
    var appointment = new AppointmentEntity(); appointment.setEmployee(employeeId); appointment.setType("PRIMARY");
    mutationPolicies.forEach(policy -> policy.appointment(appointment, false));
  }

  @Override
  public void linkEmployeeUser(String employeeId, String userId) throws OuroborosException {
    // 1. 检查员工是否存在
    if (!employeeAggregator.getById(employeeId).isPresent()) {
      throw new BusinessException(404, "员工不存在");
    }
    
    // 2. 检查用户是否存在
    if (!userCenter.getUser(userId).isPresent()) {
      throw new BusinessException(404, "用户不存在");
    }
    
    // 3. 检查用户是否已关联其他员工
    Optional<UserSubject> existingByUser = userSubjectCenter.getByUser(userId, "employee");
    if (existingByUser.isPresent()) {
      String existingEmployeeId = existingByUser.get().getSubjectId();
      Optional<com.ouroboros.organizationstructure.Employee> existingEmployee = employeeAggregator.getById(existingEmployeeId);
      
      if (existingEmployee.isPresent()) {
        // 员工存在，报错并显示员工名
        throw new BusinessException(409, "用户已关联员工：" + existingEmployee.get().getName() + "，无法重复关联");
      } else {
        // 员工不存在，删除旧关联后继续
        userSubjectService.deleteByUserAndSubjectType(userId, "employee");
      }
    }
    
    // 4. 检查员工是否已关联其他用户
    Optional<UserSubject> existingBySubject = userSubjectCenter.getBySubject("employee", employeeId);
    if (existingBySubject.isPresent()) {
      String existingUserId = existingBySubject.get().getUserIdentity();
      if (userCenter.getUser(existingUserId).isPresent()) {
        throw new BusinessException(409, "员工已关联用户,无法重复关联");
      } else {
        userSubjectService.deleteByUserAndSubjectType(existingUserId, "employee");
      }
    }
    
    // 使用Service进行管理操作
    userSubjectService.create(userId, "employee", employeeId);
  }

  @Override
  public void unlinkEmployeeUser(String employeeId) throws OuroborosException {
    if (!employeeAggregator.getById(employeeId).isPresent()) {
      throw new BusinessException(404, "员工不存在");
    }
    // 使用Service进行管理操作
    Optional<UserSubjectEntity> userSubject = userSubjectService.findBySubject("employee", employeeId);
    if (userSubject.isPresent()) {
      userSubjectService.deleteByUserAndSubjectType(userSubject.get().getUserIdentity(), "employee");
    }
  }

  @Override
  public String createAppointment(CreateAppointmentRequest request) throws OuroborosException {
    AppointmentEntity entity = appointmentManageService.create(
        request.getEmployeeId(),
        request.getPositionId(),
        request.getType() != null ? request.getType() : AppointmentType.PRIMARY,
        request.getStartDate() != null ? request.getStartDate() : LocalDate.now(),
        request.getEndDate()
    );
    return entity.getId();
  }
}
