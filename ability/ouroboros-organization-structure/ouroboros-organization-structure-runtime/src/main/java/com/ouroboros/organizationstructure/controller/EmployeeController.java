package com.ouroboros.organizationstructure.controller;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.dto.request.*;
import com.ouroboros.organizationstructure.dto.response.EmployeeAppointmentInfoDTO;
import com.ouroboros.organizationstructure.dto.response.EmployeeDTO;
import com.ouroboros.organizationstructure.dto.response.OrganizationNodeDTO;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;
import com.ouroboros.organizationstructure.profile.service.EmployeeProfileValueService;
import com.ouroboros.organizationstructure.service.EmployeeAppointmentInfo;
import com.ouroboros.organizationstructure.service.OrganizationFacadeService;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.organizationstructure.util.ValidationUtils;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.service.UserService;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/organization")
public class EmployeeController {

  private final OrganizationFacadeService manageFacade;
  private final OrganizationQueryFacade queryFacade;
  private final UserService userService;
  private final UserCenter userCenter;
  private final EmployeeProfileValueService employeeProfileValueService;

  @Autowired
  public EmployeeController(
      OrganizationFacadeService manageFacade,
      OrganizationQueryFacade queryFacade,
      UserService userService,
      UserCenter userCenter,
      EmployeeProfileValueService employeeProfileValueService) {
    this.manageFacade = manageFacade;
    this.queryFacade = queryFacade;
    this.userService = userService;
    this.userCenter = userCenter;
    this.employeeProfileValueService = employeeProfileValueService;
  }

  public EmployeeController(
      OrganizationFacadeService manageFacade,
      OrganizationQueryFacade queryFacade,
      UserService userService,
      UserCenter userCenter) {
    this(manageFacade, queryFacade, userService, userCenter, NoopEmployeeProfileValueProvider.INSTANCE);
  }

  @PermitAll
  @Operation(summary = "分页查询员工", description = "按公司、部门、岗位、名称等条件分页查询员工。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回员工分页结果")
  })
  @GetMapping("/employees")
  public PagedWebApiResult<EmployeeDTO> list(
      @Parameter(description = "公司 ID")
      @RequestParam(name = "companyId", required = false) String companyId,
      @Parameter(description = "部门 ID")
      @RequestParam(name = "departmentId", required = false) String departmentId,
      @Parameter(description = "岗位 ID")
      @RequestParam(name = "positionId", required = false) String positionId,
      @Parameter(description = "员工姓名关键字")
      @RequestParam(name = "name", required = false) String name,
      @Parameter(description = "员工 ID 列表")
      @RequestParam(name = "id", required = false) String id,
      @Parameter(description = "通用值过滤")
      @RequestParam(name = "value", required = false) String value,
      @Parameter(description = "页码")
      @RequestParam(name = "page", required = false) Integer page,
      @Parameter(description = "每页条数")
      @RequestParam(name = "perPage", required = false) Integer perPage) {
    BiFunction<Set<String>, String, Predicate<Employee>> filterBuilder = (ids, keyword) -> {
      if (!ids.isEmpty()) {
        return employee -> ids.contains(employee.getId());
      }
      if (!keyword.isEmpty()) {
        return employee -> StringUtils.containsIgnoreCase(employee.getName(), keyword);
      }
      return employee -> true;
    };
    List<Employee> employees = searchEmployees(companyId, departmentId, positionId, name, id, value, filterBuilder);
    Function<Employee, EmployeeDTO> mapper = EmployeeDTO::from;

    if (perPage == null || perPage <= 0) {
      return PagedWebApiResult.success(employees.size(), employees.stream().map(mapper).collect(Collectors.toList()));
    }

    var pageIndex = page == null ? 1 : page;
    var items = employees.stream()
        .skip(Math.min((long) (pageIndex - 1) * perPage, employees.size()))
        .limit(Math.min(perPage, employees.size()))
        .map(mapper)
        .collect(Collectors.toList());
    return PagedWebApiResult.success(employees.size(), items);
  }

  @PermitAll
  @Operation(summary = "获取员工详情", description = "根据员工 ID 获取员工详情。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回员工详情"),
      @ApiResponse(responseCode = "404", description = "员工不存在")
  })
  @GetMapping("/employees/{id}")
  public WebApiResult<EmployeeDTO> getById(
      @Parameter(description = "员工 ID", required = true)
      @PathVariable("id") String id) {
    return queryFacade.getEmployeeById(id)
        .map(employee -> EmployeeDTO.from(employee, employeeProfileValueService.getProfileValuesByEmployeeId(id)))
        .map(WebApiResult::success)
        .orElse(WebApiResult.error(404, "员工不存在"));
  }

  @PermitAll
  @Operation(summary = "获取员工组织链", description = "根据员工 ID 获取员工所在组织链。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回员工组织链"),
      @ApiResponse(responseCode = "404", description = "员工不存在")
  })
  @GetMapping("/employees/{id}/org-chain")
  public ItemsWebApiResult<OrganizationNodeDTO> getOrganizationChain(
      @Parameter(description = "员工 ID", required = true)
      @PathVariable("id") String id) {
    if (!queryFacade.getEmployeeById(id).isPresent()) {
      return ItemsWebApiResult.fail(404, "员工不存在");
    }
    List<OrganizationNode> chain = queryFacade.getOrganizationChain(id);
    List<OrganizationNodeDTO> dtos = chain.stream()
        .map(OrganizationNodeDTO::from)
        .collect(Collectors.toList());

    return ItemsWebApiResult.success(dtos);
  }

  @PermitAll
  @Operation(summary = "获取员工任职信息", description = "根据员工 ID 获取员工任职记录。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回员工任职记录"),
      @ApiResponse(responseCode = "404", description = "员工不存在")
  })
  @GetMapping("/employees/{id}/appointments")
  public ItemsWebApiResult<EmployeeAppointmentInfoDTO> getAppointments(
      @Parameter(description = "员工 ID", required = true)
      @PathVariable("id") String id) {
    if (!queryFacade.getEmployeeById(id).isPresent()) {
      return ItemsWebApiResult.fail(404, "员工不存在");
    }
    List<EmployeeAppointmentInfo> infos = queryFacade.getAppointments(id);
    List<EmployeeAppointmentInfoDTO> dtos = infos.stream()
        .map(EmployeeAppointmentInfoDTO::from)
        .collect(Collectors.toList());

    return ItemsWebApiResult.success(dtos);
  }

  @Transactional
  @Operation(summary = "创建员工", description = "新增员工并可选关联用户和任职信息。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "员工创建成功"),
      @ApiResponse(responseCode = "400", description = "员工参数不合法"),
      @ApiResponse(responseCode = "404", description = "关联用户不存在"),
      @ApiResponse(responseCode = "409", description = "用户或员工已存在关联关系"),
      @ApiResponse(responseCode = "500", description = "创建用户失败")
  })
  @PostMapping("/employees")
  public WebApiResult<String> create(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "员工创建请求", required = true)
      @RequestBody CreateEmployeeRequest request) {
    if (StringUtils.isBlank(request.getName())) {
      return WebApiResult.error(400, "员工姓名不能为空");
    }
    try {
      ValidationUtils.validateUserLinkInfo(request.getUser());
    } catch (BusinessException exception) {
      return WebApiResult.error(400, exception.getMessage());
    }
    if (request.getUser() != null
        && "create".equals(request.getUser().getMode())
        && userCenter.getUser(request.getUser().getUsername()).isPresent()) {
      return WebApiResult.error(400, "用户名已存在: " + request.getUser().getUsername());
    }

    String userId;
    try {
      userId = handleUserLink(request.getUser());
    } catch (BusinessException exception) {
      return WebApiResult.error(500, exception.getMessage());
    }

    EmployeeEntity employee = request.toEntity();

    String employeeId = manageFacade.createEmployee(employee, request.getDepartmentId());
    employeeProfileValueService.saveProfileValuesByEmployeeId(employeeId, request.toProfileValues());

    // 如果有用户关联，使用新架构创建绑定关系
    if (userId != null) {
      manageFacade.linkEmployeeUser(employeeId, userId);
    }

    // 如果指定了岗位，创建任职
    if (request.getPositionId() != null && !request.getPositionId().trim().isEmpty()) {
      CreateAppointmentRequest appointmentRequest = new CreateAppointmentRequest();
      appointmentRequest.setEmployeeId(employeeId);
      appointmentRequest.setPositionId(request.getPositionId());
      appointmentRequest.setStartDate(request.getStartTime());
      manageFacade.createAppointment(appointmentRequest);
    }

    return WebApiResult.success(employeeId);
  }

  @Operation(summary = "更新员工", description = "根据员工 ID 更新员工基本信息。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "员工更新成功"),
      @ApiResponse(responseCode = "400", description = "员工参数不合法"),
      @ApiResponse(responseCode = "404", description = "员工不存在")
  })
  @PutMapping("/employees/{id}")
  @Transactional
  public WebApiResult<Void> update(
      @Parameter(description = "员工 ID", required = true)
      @PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "员工更新请求", required = true)
      @RequestBody UpdateEmployeeRequest request) {
    if (StringUtils.isBlank(request.getName())) {
      return WebApiResult.error(400, "员工姓名不能为空");
    }

    manageFacade.updateEmployee(id, request.toEntity());
    employeeProfileValueService.saveProfileValuesByEmployeeId(id, request.toProfileValues());
    return WebApiResult.success();
  }

  @Operation(summary = "关联员工用户", description = "为指定员工关联已有用户或创建并绑定新用户。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "员工用户关联成功"),
      @ApiResponse(responseCode = "400", description = "员工用户关联参数不合法"),
      @ApiResponse(responseCode = "404", description = "员工或用户不存在"),
      @ApiResponse(responseCode = "409", description = "员工或用户已存在关联关系")
  })
  @PutMapping("/employees/{id}/user")
  public WebApiResult<Void> linkUser(
      @Parameter(description = "员工 ID", required = true)
      @PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "员工用户关联请求", required = true)
      @RequestBody LinkUserRequest request) {
    String userId;

    if (StringUtils.isBlank(request.getMode())) {
      return WebApiResult.error(400, "用户关联模式不能为空");
    }
    if (!"create".equals(request.getMode()) && !"link".equals(request.getMode())) {
      return WebApiResult.error(400, "无效的用户关联模式: " + request.getMode());
    }

    if ("create".equals(request.getMode())) {
      // 创建新用户
      if (StringUtils.isBlank(request.getNewUsername())) {
        return WebApiResult.error(400, "用户名不能为空");
      }
      if (StringUtils.isBlank(request.getNewPassword())) {
        return WebApiResult.error(400, "密码不能为空");
      }

      // 检查用户名是否已存在
      if (userCenter.getUser(request.getNewUsername()).isPresent()) {
        return WebApiResult.error(400, "用户名已存在: " + request.getNewUsername());
      }

      // 创建用户
      UserEntity user = new UserEntity();
      user.setUserIdentity(request.getNewUsername());
      user.setPassword(request.getNewPassword());

      try {
        UserEntity created = userService.add(user);
        userId = created.getUserIdentity();
      } catch (Exception e) {
        return WebApiResult.error(500, "创建用户失败: " + e.getMessage());
      }
    } else {
      // 关联已有用户
      if (StringUtils.isBlank(request.getUserId())) {
        return WebApiResult.error(400, "用户ID不能为空");
      }
      userId = request.getUserId();
    }

    manageFacade.linkEmployeeUser(id, userId);
    return WebApiResult.success();
  }

  @Operation(summary = "解绑员工用户", description = "解除指定员工与用户之间的绑定关系。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "员工用户解绑成功"),
      @ApiResponse(responseCode = "404", description = "员工不存在")
  })
  @DeleteMapping("/employees/{id}/user")
  public WebApiResult<Void> unlinkUser(
      @Parameter(description = "员工 ID", required = true)
      @PathVariable("id") String id) {
    manageFacade.unlinkEmployeeUser(id);
    return WebApiResult.success();
  }

  @Operation(summary = "删除员工", description = "根据员工 ID 删除员工。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "员工删除成功"),
      @ApiResponse(responseCode = "404", description = "员工不存在")
  })
  @DeleteMapping("/employees/{id}")
  @Transactional
  public WebApiResult<Void> delete(
      @Parameter(description = "员工 ID", required = true)
      @PathVariable("id") String id) {
    manageFacade.deleteEmployee(id);
    employeeProfileValueService.deleteByEmployeeId(id);
    return WebApiResult.success();
  }

  private enum NoopEmployeeProfileValueProvider implements EmployeeProfileValueService {
    INSTANCE;

    @Override
    public Optional<com.ouroboros.organizationstructure.profile.entity.EmployeeProfileValueEntity> findByEmployeeId(String employeeId) {
      return Optional.empty();
    }

    @Override
    public Map<String, Object> getProfileValuesByEmployeeId(String employeeId) {
      return Collections.emptyMap();
    }

    @Override
    public void saveProfileValuesByEmployeeId(String employeeId, Map<String, Object> profileValues) {
    }

    @Override
    public void deleteByEmployeeId(String employeeId) {
    }
  }

  private String handleUserLink(UserLinkInfo info) {
    if (info == null || "none".equals(info.getMode())) {
      return null;
    }

    if ("link".equals(info.getMode())) {
      return info.getUserId();
    }

    if ("create".equals(info.getMode())) {
      if (userCenter.getUser(info.getUsername()).isPresent()) {
        throw new BusinessException("用户名已存在: " + info.getUsername());
      }

      UserEntity user = new UserEntity();
      user.setUserIdentity(info.getUsername());
      user.setPassword(info.getPassword());

      try {
        UserEntity created = userService.add(user);
        return created.getUserIdentity();
      } catch (Exception e) {
        throw new BusinessException("创建用户失败: " + e.getMessage());
      }
    }

    return null;
  }

  @Operation(summary = "调整员工岗位", description = "根据员工 ID 更新员工岗位信息。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "员工岗位调整成功"),
      @ApiResponse(responseCode = "400", description = "岗位参数不合法"),
      @ApiResponse(responseCode = "404", description = "员工或岗位不存在")
  })
  @PutMapping("/employees/{id}/position")
  public WebApiResult<Void> updateEmployeePosition(
      @Parameter(description = "员工 ID", required = true)
      @PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "员工岗位调整请求", required = true)
      @RequestBody UpdateEmployeePositionRequest request) {
    if (StringUtils.isBlank(request.getPositionId())) {
      return WebApiResult.error(400, "岗位ID不能为空");
    }

    manageFacade.transferToPosition(id, request.getPositionId());
    return WebApiResult.success();
  }

  private boolean isDescendantOf(OrganizationNode node, OrganizationNode ancestor) {
    if (node == null || ancestor == null) {
      return false;
    }

    String currentParent = node.getParent();
    while (currentParent != null) {
      if (currentParent.equals(ancestor.getId())) {
        return true;
      }
      currentParent = queryFacade.getNodeById(currentParent)
          .map(OrganizationNode::getParent)
          .orElse(null);
    }
    return false;
  }

  @PermitAll
  @Operation(summary = "分页查询组织用户", description = "按组织范围和关键字分页查询已绑定的用户列表。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回用户分页结果")
  })
  @GetMapping("/users")
  public PagedWebApiResult<User> listUsers(
      @Parameter(description = "公司 ID")
      @RequestParam(name = "companyId", required = false) String companyId,
      @Parameter(description = "部门 ID")
      @RequestParam(name = "departmentId", required = false) String departmentId,
      @Parameter(description = "岗位 ID")
      @RequestParam(name = "positionId", required = false) String positionId,
      @Parameter(description = "姓名关键字")
      @RequestParam(name = "name", required = false) String name,
      @Parameter(description = "用户 ID 列表")
      @RequestParam(name = "id", required = false) String id,
      @Parameter(description = "通用值过滤")
      @RequestParam(name = "value", required = false) String value,
      @Parameter(description = "页码")
      @RequestParam(name = "page", required = false) Integer page,
      @Parameter(description = "每页条数")
      @RequestParam(name = "perPage", required = false) Integer perPage) {
    BiFunction<Set<String>, String, Predicate<Employee>> filterBuilder = (ids, keyword) -> {
      Predicate<Employee> baseFilter = employee -> employee.getUserDetail() != null;
      if (!ids.isEmpty()) {
        return baseFilter.and(employee -> ids.contains(employee.getUserDetail().getUserIdentity()));
      }
      if (!keyword.isEmpty()) {
        return baseFilter.and(employee -> StringUtils.containsIgnoreCase(employee.getName(), keyword)
            || StringUtils.containsIgnoreCase(employee.getUserDetail().getUserIdentity(), keyword));
      }
      return baseFilter;
    };
    var employees = searchEmployees(companyId, departmentId, positionId, name, id, value, filterBuilder);
    var users = employees.stream()
        .map(Employee::getUserDetail)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    if (perPage == null || perPage <= 0) {
      return PagedWebApiResult.ok(users.size(), users);
    }
    var pageIndex = page == null ? 1 : page;
    return PagedWebApiResult.ok(users.size(), users.stream()
        .skip(Math.min((long) (pageIndex - 1) * perPage, users.size()))
        .limit(Math.min(perPage, users.size()))
        .collect(Collectors.toList()));
  }

  private List<Employee> searchEmployees(
      String companyId,
      String departmentId,
      String positionId,
      String name,
      String id,
      String value,
      BiFunction<Set<String>, String, Predicate<Employee>> filterBuilder) {
    List<Employee> employees;

    if (departmentId != null && !departmentId.isEmpty()) {
      employees = queryFacade.getEmployeesByDepartment(departmentId);
    } else if (companyId != null && !companyId.isEmpty()) {
      employees = queryFacade.getAllEmployees().stream()
          .filter(emp -> {
            // 通过部门找到公司
            if (emp.getDepartment() != null) {
              return queryFacade.getNodeById(emp.getDepartment())
                  .map(dept -> queryFacade.getNodeById(companyId)
                      .map(company -> isDescendantOf(dept, company))
                      .orElse(false))
                  .orElse(false);
            }
            return false;
          })
          .collect(Collectors.toList());
    } else if (positionId != null && !positionId.isEmpty()) {
      employees = queryFacade.getEmployeesByPosition(positionId);
    } else {
      employees = queryFacade.getAllEmployees();
    }

    Set<String> ids;
    String nameCondition;
    if (StringUtils.isNotBlank(id)) {
      ids = Arrays.stream(id.split(",")).collect(Collectors.toSet());
    } else if (StringUtils.isNotBlank(value)) {
      ids = Arrays.stream(value.split(",")).collect(Collectors.toSet());
    } else {
      ids = Collections.emptySet();
    }
    nameCondition = StringUtils.isNotBlank(name) ? name : "";

    Predicate<Employee> filter = filterBuilder.apply(ids, nameCondition);

    return filter == null ? employees : employees.stream()
        .filter(filter)
        .collect(Collectors.toList());
  }
}
