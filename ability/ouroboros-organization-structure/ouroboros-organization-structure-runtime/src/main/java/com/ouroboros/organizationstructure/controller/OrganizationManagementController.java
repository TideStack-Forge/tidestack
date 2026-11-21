package com.ouroboros.organizationstructure.controller;

import com.ouroboros.organizationstructure.*;
import com.ouroboros.organizationstructure.dto.request.*;
import com.ouroboros.organizationstructure.dto.response.AppointmentDTO;
import com.ouroboros.organizationstructure.dto.response.CompanyDTO;
import com.ouroboros.organizationstructure.dto.response.DepartmentDTO;
import com.ouroboros.organizationstructure.dto.response.PositionDTO;
import com.ouroboros.organizationstructure.service.OrganizationFacadeService;
import com.ouroboros.organizationstructure.service.OrganizationQueryFacade;
import com.ouroboros.organizationstructure.PositionDefinitionCenter;
import com.ouroboros.organizationstructure.util.ValidationUtils;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.ItemsWebApiResult;
import org.springframework.beans.factory.annotation.Autowired;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.web.bind.annotation.*;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

@RestController
@RequestMapping("/api/organization")
public class OrganizationManagementController {
    
    private final OrganizationFacadeService manageFacade;
    private final OrganizationQueryFacade queryFacade;
    private final PositionDefinitionCenter positionDefinitionCenter;

    public OrganizationManagementController(
        OrganizationFacadeService manageFacade,
        OrganizationQueryFacade queryFacade) {
        this(manageFacade, queryFacade, PositionDefinitionCenter.instance());
    }

    @Autowired
    public OrganizationManagementController(
        OrganizationFacadeService manageFacade,
        OrganizationQueryFacade queryFacade,
        PositionDefinitionCenter positionDefinitionCenter) {
        this.manageFacade = manageFacade;
        this.queryFacade = queryFacade;
        this.positionDefinitionCenter = positionDefinitionCenter;
    }

    // ==================== 公司管理 ====================
    @Operation(summary = "列出公司", description = "查询全部公司列表。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回公司列表")
    })
    @GetMapping("/companies")
    public ItemsWebApiResult<CompanyDTO> getCompanies() {
        List<CompanyDTO> companies = queryFacade.getAllCompaniesDTO();
        return ItemsWebApiResult.success(companies);
    }

    @Operation(summary = "创建公司", description = "新增一个公司节点。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "公司创建成功"),
        @ApiResponse(responseCode = "400", description = "公司参数不合法"),
        @ApiResponse(responseCode = "500", description = "创建公司后查询失败")
    })
    @PostMapping("/companies")
    public WebApiResult<Company> createCompany(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "公司创建请求", required = true)
        @RequestBody CreateCompanyRequest request) {
        if (StringUtils.isBlank(request.getName())) {
            return WebApiResult.error(400, "公司名称不能为空");
        }
        String id = manageFacade.createCompany(request.toEntity());
        return queryFacade.getCompanyById(id)
            .map(WebApiResult::success)
            .orElse(WebApiResult.error(500, "创建公司后查询失败"));
    }

    @Operation(summary = "获取公司详情", description = "根据公司 ID 获取公司详情。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回公司详情"),
        @ApiResponse(responseCode = "404", description = "公司不存在")
    })
    @GetMapping("/companies/{id}")
    public WebApiResult<Company> getCompanyById(
        @Parameter(description = "公司 ID", required = true)
        @PathVariable("id") String id) {
        return queryFacade.getCompanyById(id)
            .map(WebApiResult::success)
            .orElse(WebApiResult.error(404, "公司不存在"));
    }

    @Operation(summary = "更新公司", description = "根据公司 ID 更新公司信息。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "公司更新成功"),
        @ApiResponse(responseCode = "400", description = "公司参数不合法"),
        @ApiResponse(responseCode = "404", description = "公司不存在")
    })
    @PutMapping("/companies/{id}")
    public WebApiResult<Company> updateCompany(
        @Parameter(description = "公司 ID", required = true)
        @PathVariable("id") String id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "公司更新请求", required = true)
        @RequestBody UpdateCompanyRequest request) {
        if (StringUtils.isBlank(request.getName())) {
            return WebApiResult.error(400, "公司名称不能为空");
        }
        manageFacade.updateNode(id, request.toEntity());
        return queryFacade.getCompanyById(id)
            .map(WebApiResult::success)
            .orElse(WebApiResult.error(404, "公司不存在"));
    }

    @Operation(summary = "删除公司", description = "根据公司 ID 删除公司节点。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "公司删除成功"),
        @ApiResponse(responseCode = "404", description = "公司不存在")
    })
    @DeleteMapping("/companies/{id}")
    public WebApiResult<Company> deleteCompany(
        @Parameter(description = "公司 ID", required = true)
        @PathVariable("id") String id) {
        Optional<Company> company = queryFacade.getCompanyById(id);
        if (!company.isPresent()) {
            return WebApiResult.error(404, "公司不存在");
        }
        manageFacade.deleteNode(id);
        return WebApiResult.success(company.get());
    }

    // ==================== 部门管理 ====================
    @Operation(summary = "列出部门", description = "按公司过滤查询部门列表。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回部门列表")
    })
    @GetMapping("/departments")
    public ItemsWebApiResult<DepartmentDTO> getDepartments(
            @Parameter(description = "公司 ID")
            @RequestParam(name = "companyId", required = false) String companyId) {
        List<DepartmentDTO> departments = companyId != null 
            ? queryFacade.getDepartmentsByCompanyDTO(companyId)
            : queryFacade.getAllDepartmentsDTO();
        return ItemsWebApiResult.success(departments);
    }

    @Operation(summary = "创建部门", description = "新增一个部门节点。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "部门创建成功"),
        @ApiResponse(responseCode = "400", description = "部门参数不合法"),
        @ApiResponse(responseCode = "500", description = "创建部门后查询失败")
    })
    @PostMapping("/departments")
    public WebApiResult<Department> createDepartment(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "部门创建请求", required = true)
        @RequestBody CreateDepartmentRequest request) {
        if (StringUtils.isBlank(request.getName())) {
            return WebApiResult.error(400, "部门名称不能为空");
        }
        String id = manageFacade.createDepartment(request.toEntity(), request.isWithDefaultPosition());
        return queryFacade.getDepartmentById(id)
            .map(WebApiResult::success)
            .orElse(WebApiResult.error(500, "创建部门后查询失败"));
    }

    @Operation(summary = "获取部门详情", description = "根据部门 ID 获取部门详情。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回部门详情"),
        @ApiResponse(responseCode = "404", description = "部门不存在")
    })
    @GetMapping("/departments/{id}")
    public WebApiResult<Department> getDepartmentById(
        @Parameter(description = "部门 ID", required = true)
        @PathVariable("id") String id) {
        return queryFacade.getDepartmentById(id)
            .map(WebApiResult::success)
            .orElse(WebApiResult.error(404, "部门不存在"));
    }

    @Operation(summary = "更新部门", description = "根据部门 ID 更新部门信息。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "部门更新成功"),
        @ApiResponse(responseCode = "400", description = "部门参数不合法"),
        @ApiResponse(responseCode = "404", description = "部门不存在")
    })
    @PutMapping("/departments/{id}")
    public WebApiResult<Department> updateDepartment(
        @Parameter(description = "部门 ID", required = true)
        @PathVariable("id") String id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "部门更新请求", required = true)
        @RequestBody UpdateDepartmentRequest request) {
        if (StringUtils.isBlank(request.getName())) {
            return WebApiResult.error(400, "部门名称不能为空");
        }
        manageFacade.updateNode(id, request.toEntity());
        return queryFacade.getDepartmentById(id)
            .map(WebApiResult::success)
            .orElse(WebApiResult.error(404, "更新后部门不存在"));
    }

    @Operation(summary = "删除部门", description = "根据部门 ID 删除部门节点。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "部门删除成功"),
        @ApiResponse(responseCode = "404", description = "部门不存在")
    })
    @DeleteMapping("/departments/{id}")
    public WebApiResult<Department> deleteDepartment(
        @Parameter(description = "部门 ID", required = true)
        @PathVariable("id") String id) {
        Optional<Department> department = queryFacade.getDepartmentById(id);
        if (!department.isPresent()) {
            return WebApiResult.error(404, "部门不存在");
        }
        manageFacade.deleteNode(id);
        return WebApiResult.success(department.get());
    }

    // ==================== 岗位管理 ====================
    @Operation(summary = "列出全局岗位定义", description = "查询文件中声明的全局岗位定义列表。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回全局岗位定义列表")
    })
    @GetMapping("/position-definitions")
    public ItemsWebApiResult<PositionDefinition> getPositionDefinitions() {
        return ItemsWebApiResult.success(positionDefinitionCenter.getDefinitions());
    }

    @Operation(summary = "创建岗位", description = "新增一个岗位节点。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "岗位创建成功"),
        @ApiResponse(responseCode = "400", description = "岗位参数不合法"),
        @ApiResponse(responseCode = "500", description = "创建岗位后查询失败")
    })
    @PostMapping("/positions")
    public WebApiResult<Position> createPosition(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "岗位创建请求", required = true)
        @RequestBody CreatePositionRequest request) {
        if (StringUtils.isBlank(request.getDepartmentId())) {
            return WebApiResult.error(400, "部门ID不能为空");
        }
        String requestedPositionKey = request.getPositionKey();
        String resolvedPositionKey = resolvePositionKey(request);
        if (StringUtils.isNotBlank(requestedPositionKey) && StringUtils.isBlank(resolvedPositionKey)) {
            return WebApiResult.error(400, "岗位定义不存在");
        }
        if (StringUtils.isBlank(request.getName())
            && StringUtils.isBlank(requestedPositionKey)
            && StringUtils.isBlank(resolvedPositionKey)) {
            return WebApiResult.error(400, "岗位名称不能为空");
        }
        if (StringUtils.isNotBlank(resolvedPositionKey)) {
            request.setPositionKey(resolvedPositionKey);
        }
        String id = manageFacade.createPosition(request.toEntity());
        return queryFacade.getPositionById(id)
            .map(WebApiResult::success)
            .orElse(WebApiResult.error(500, "创建岗位后查询失败"));
    }

    private String resolvePositionKey(CreatePositionRequest request) {
        if (request == null) {
            return null;
        }
        if (StringUtils.isNotBlank(request.getPositionKey())) {
            return positionDefinitionCenter.getDefinition(request.getPositionKey())
                .map(PositionDefinition::getKey)
                .orElse(null);
        }
        if (StringUtils.isNotBlank(request.getName())) {
            return positionDefinitionCenter.getDefinitions().stream()
                .filter(definition -> request.getName().equals(definition.getName()))
                .map(definition -> definition.getKey())
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    @Operation(summary = "获取岗位详情", description = "根据岗位 ID 获取岗位详情。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回岗位详情"),
        @ApiResponse(responseCode = "404", description = "岗位不存在")
    })
    @GetMapping("/positions/{id}")
    public WebApiResult<Position> getPositionById(
        @Parameter(description = "岗位 ID", required = true)
        @PathVariable("id") String id) {
        return queryFacade.getPositionById(id)
            .map(WebApiResult::success)
            .orElse(WebApiResult.error(404, "岗位不存在"));
    }

    @Operation(summary = "更新岗位", description = "根据岗位 ID 更新岗位信息。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "岗位更新成功"),
        @ApiResponse(responseCode = "400", description = "岗位参数不合法"),
        @ApiResponse(responseCode = "404", description = "岗位不存在")
    })
    @PutMapping("/positions/{id}")
    public WebApiResult<Position> updatePosition(
        @Parameter(description = "岗位 ID", required = true)
        @PathVariable("id") String id,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "岗位更新请求", required = true)
        @RequestBody UpdatePositionRequest request) {
        if (StringUtils.isBlank(request.getName()) && request.getDescription() == null && request.getHeadcount() == null) {
            return WebApiResult.error(400, "岗位名称不能为空");
        }
        manageFacade.updatePosition(id, request.toEntity());
        return queryFacade.getPositionById(id)
            .map(WebApiResult::success)
            .orElse(WebApiResult.error(404, "更新后岗位不存在"));
    }

    @Operation(summary = "删除岗位", description = "根据岗位 ID 删除岗位节点。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "岗位删除成功"),
        @ApiResponse(responseCode = "404", description = "岗位不存在")
    })
    @DeleteMapping("/positions/{id}")
    public WebApiResult<Position> deletePosition(
        @Parameter(description = "岗位 ID", required = true)
        @PathVariable("id") String id) {
        Optional<Position> position = queryFacade.getPositionById(id);
        if (!position.isPresent()) {
            return WebApiResult.error(404, "岗位不存在");
        }
        manageFacade.deletePosition(id);
        return WebApiResult.success(position.get());
    }

    @Operation(summary = "列出岗位", description = "按部门过滤查询岗位列表。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回岗位列表")
    })
    @GetMapping("/positions")
    public ItemsWebApiResult<PositionDTO> getPositions(
        @Parameter(description = "部门 ID")
        @RequestParam(name = "departmentId", required = false) String departmentId) {
        List<PositionDTO> positions = departmentId != null
            ? queryFacade.getPositionsByDepartmentDTO(departmentId)
            : queryFacade.getAllPositionsDTO();
        return ItemsWebApiResult.success(positions);
    }

    // ==================== 任职管理 ====================
    @Operation(summary = "列出任职", description = "按员工、岗位或部门过滤查询任职列表。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回任职列表")
    })
    @GetMapping("/appointments")
    public ItemsWebApiResult<AppointmentDTO> getAppointments(
            @Parameter(description = "员工 ID")
            @RequestParam(name = "employeeId", required = false) String employeeId,
            @Parameter(description = "岗位 ID")
            @RequestParam(name = "positionId", required = false) String positionId,
            @Parameter(description = "部门 ID")
            @RequestParam(name = "departmentId", required = false) String departmentId) {
        List<AppointmentDTO> appointments;
        if (employeeId != null) {
            appointments = queryFacade.getAppointmentsByEmployeeDTO(employeeId);
        } else if (positionId != null) {
            appointments = queryFacade.getAppointmentsByPositionDTO(positionId);
        } else if (departmentId != null) {
            appointments = queryFacade.getAppointmentsByDepartmentDTO(departmentId);
        } else {
            appointments = queryFacade.getAllAppointmentsDTO();
        }
        return ItemsWebApiResult.success(appointments);
    }

    @Operation(summary = "创建任职", description = "新增一条员工任职记录。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "任职创建成功"),
        @ApiResponse(responseCode = "400", description = "任职参数不合法"),
        @ApiResponse(responseCode = "500", description = "创建任职后查询失败")
    })
    @PostMapping("/appointments")
    public WebApiResult<Appointment> createAppointment(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "任职创建请求", required = true)
        @RequestBody CreateAppointmentRequest request) {
        String id = manageFacade.createAppointment(request);
        return queryFacade.getAppointmentById(id)
            .map(WebApiResult::success)
            .orElse(WebApiResult.error(500, "创建任职后查询失败"));
    }

    @Operation(summary = "删除任职", description = "根据任职 ID 删除一条任职记录。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "任职删除成功"),
        @ApiResponse(responseCode = "404", description = "任职不存在")
    })
    @DeleteMapping("/appointments/{id}")
    public WebApiResult<AppointmentDTO> deleteAppointment(
        @Parameter(description = "任职 ID", required = true)
        @PathVariable("id") String id) {
        Optional<Appointment> appointment = queryFacade.getAppointmentById(id);
        if (!appointment.isPresent()) {
            return WebApiResult.error(404, "任职不存在");
        }
        manageFacade.removeAppointment(id);
        return WebApiResult.success(AppointmentDTO.from(appointment.get()));
    }

    // ==================== 岗位分配管理 ====================
    @Operation(summary = "为员工分配岗位", description = "为指定员工分配岗位并生成对应任职记录。", tags = {"organization"})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "岗位分配成功"),
        @ApiResponse(responseCode = "400", description = "岗位分配参数不合法"),
        @ApiResponse(responseCode = "404", description = "员工、岗位或既有任职不存在"),
        @ApiResponse(responseCode = "409", description = "目标岗位已有冲突任职")
    })
    @PostMapping("/employees/{employeeId}/assign-position")
    public WebApiResult<AppointmentDTO> assignPosition(
            @Parameter(description = "员工 ID", required = true)
            @PathVariable("employeeId") String employeeId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "岗位分配请求", required = true)
            @RequestBody AssignPositionRequest request) {
        if (StringUtils.isBlank(request.getPositionId())) {
            return WebApiResult.error(400, "岗位ID不能为空");
        }
        if (request.getAppointmentType() == null) {
            return WebApiResult.error(400, "任职类型不能为空");
        }
        
        // 获取该员工的所有当前任职
        List<Appointment> currentAppointments = queryFacade.getCurrentAppointmentsByEmployee(employeeId);
        
        // 第一步：检查目标岗位是否已有当前任职（一岗一任职原则）
        Appointment existingAppointment = null;
        for (Appointment appointment : currentAppointments) {
            if (appointment.isCurrent() && 
                appointment.getPosition().equals(request.getPositionId())) {
                existingAppointment = appointment;
                break;
            }
        }
        
        // 如果目标岗位已有任职，根据类型返回不同错误
        if (existingAppointment != null) {
            String existingType = existingAppointment.getType().name();
            String requestType = request.getAppointmentType().name();
            
            // 构建精确的错误信息
            if (existingType.equals("PRIMARY")) {
                if (requestType.equals("PRIMARY")) {
                    return WebApiResult.error(400, "该员工已在此岗位担任主岗");
                } else if (requestType.equals("SECONDARY")) {
                    return WebApiResult.error(400, "该员工已在此岗位担任主岗，无法再设置副岗");
                } else { // ACTING
                    return WebApiResult.error(400, "该员工已在此岗位担任主岗，无法再设置代理");
                }
            } else if (existingType.equals("SECONDARY")) {
                if (requestType.equals("PRIMARY")) {
                    return WebApiResult.error(400, "该员工已在此岗位担任副岗，请先删除副岗任职后再设置主岗");
                } else if (requestType.equals("SECONDARY")) {
                    return WebApiResult.error(400, "该员工已在此岗位担任副岗");
                } else { // ACTING
                    return WebApiResult.error(400, "该员工已在此岗位担任副岗，无法再设置代理");
                }
            } else { // ACTING
                if (requestType.equals("PRIMARY")) {
                    return WebApiResult.error(400, "该员工已在此岗位担任代理，请先删除代理任职后再设置主岗");
                } else if (requestType.equals("SECONDARY")) {
                    return WebApiResult.error(400, "该员工已在此岗位担任代理，无法再设置副岗");
                } else { // ACTING
                    return WebApiResult.error(400, "该员工已在此岗位担任代理");
                }
            }
        }
        
        // 第二步：如果是主岗，处理主岗唯一性（结束其他岗位的主岗）
        if (request.getAppointmentType() == AppointmentType.PRIMARY) {
            for (Appointment appointment : currentAppointments) {
                // 只结束其他岗位的主岗（目标岗位已在第一步检查过）
                if (appointment.isCurrent() && 
                    appointment.isPrimary() && 
                    !appointment.getPosition().equals(request.getPositionId())) {
                    manageFacade.removeAppointment(appointment.getId());
                }
            }
        }
        
        // 第三步：创建新的任职
        CreateAppointmentRequest createRequest = new CreateAppointmentRequest();
        createRequest.setEmployeeId(employeeId);
        createRequest.setPositionId(request.getPositionId());
        createRequest.setType(request.getAppointmentType());
        createRequest.setStartDate(LocalDate.now());
        
        WebApiResult<Appointment> result = this.createAppointment(createRequest);
        if (result.getData() != null) {
            return WebApiResult.success(AppointmentDTO.from(result.getData()));
        } else {
            return WebApiResult.error(result.getCode(), result.getMsg());
        }
    }
}
