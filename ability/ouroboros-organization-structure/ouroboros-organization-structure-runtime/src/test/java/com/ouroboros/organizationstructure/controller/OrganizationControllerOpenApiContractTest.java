package com.ouroboros.organizationstructure.controller;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import com.ouroboros.organizationstructure.dto.request.AssignPositionRequest;
import com.ouroboros.organizationstructure.dto.request.CreateAppointmentRequest;
import com.ouroboros.organizationstructure.dto.request.CreateCompanyRequest;
import com.ouroboros.organizationstructure.dto.request.CreateDepartmentRequest;
import com.ouroboros.organizationstructure.dto.request.CreateEmployeeRequest;
import com.ouroboros.organizationstructure.dto.request.CreatePositionRequest;
import com.ouroboros.organizationstructure.dto.request.UpdateCompanyRequest;
import com.ouroboros.organizationstructure.dto.request.UpdateDepartmentRequest;
import com.ouroboros.organizationstructure.dto.request.UpdateEmployeeRequest;
import com.ouroboros.organizationstructure.dto.request.UpdatePositionRequest;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.openapi.ParameterLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizationControllerOpenApiContractTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractEmployeeCrudAndAssociationContracts() throws Exception {
    Method list = EmployeeController.class.getMethod(
        "list",
        String.class,
        String.class,
        String.class,
        String.class,
        String.class,
        String.class,
        Integer.class,
        Integer.class);
    SimpleWebAPIModel listModel = extract("GET", "/api/organization/employees", mapping("/api/organization/employees", RequestMethod.GET), EmployeeController.class, list);
    assertEquals("分页查询员工", listModel.getSummary());
    assertEquals(Collections.singletonList("organization"), listModel.getTags());
    assertEquals(ParameterLocation.QUERY, parameter(listModel, "companyId").getIn());
    assertEquals("公司 ID", parameter(listModel, "companyId").getDescription());
    assertEquals(ParameterLocation.QUERY, parameter(listModel, "page").getIn());
    assertTrue(listModel.getResponses().containsKey("200"));
    assertEquals("成功返回员工分页结果", listModel.getResponses().get("200").getDescription());

    Method getById = EmployeeController.class.getMethod("getById", String.class);
    SimpleWebAPIModel getByIdModel = extract("GET", "/api/organization/employees/{id}", mapping("/api/organization/employees/{id}", RequestMethod.GET), EmployeeController.class, getById);
    assertEquals("获取员工详情", getByIdModel.getSummary());
    assertEquals(ParameterLocation.PATH, parameter(getByIdModel, "id").getIn());
    assertEquals("员工 ID", parameter(getByIdModel, "id").getDescription());
    assertEquals(Boolean.TRUE, parameter(getByIdModel, "id").getRequired());
    assertEquals("成功返回员工详情", getByIdModel.getResponses().get("200").getDescription());
    assertEquals("员工不存在", getByIdModel.getResponses().get("404").getDescription());

    Method create = EmployeeController.class.getMethod("create", CreateEmployeeRequest.class);
    SimpleWebAPIModel createModel = extract("POST", "/api/organization/employees", mapping("/api/organization/employees", RequestMethod.POST), EmployeeController.class, create);
    assertEquals("创建员工", createModel.getSummary());
    assertEquals(Collections.singletonList("organization"), createModel.getTags());
    assertNotNull(createModel.getRequestBody());
    assertEquals("员工创建请求", createModel.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, createModel.getRequestBody().getRequired());
    assertEquals("员工创建成功", createModel.getResponses().get("200").getDescription());
    assertEquals("员工参数不合法", createModel.getResponses().get("400").getDescription());
    assertEquals("关联用户不存在", createModel.getResponses().get("404").getDescription());
    assertEquals("用户或员工已存在关联关系", createModel.getResponses().get("409").getDescription());
    assertEquals("创建用户失败", createModel.getResponses().get("500").getDescription());

    Method update = EmployeeController.class.getMethod("update", String.class, UpdateEmployeeRequest.class);
    SimpleWebAPIModel updateModel = extract("PUT", "/api/organization/employees/{id}", mapping("/api/organization/employees/{id}", RequestMethod.PUT), EmployeeController.class, update);
    assertEquals("更新员工", updateModel.getSummary());
    assertEquals(ParameterLocation.PATH, parameter(updateModel, "id").getIn());
    assertEquals("员工更新请求", updateModel.getRequestBody().getDescription());
    assertEquals("员工更新成功", updateModel.getResponses().get("200").getDescription());
    assertEquals("员工参数不合法", updateModel.getResponses().get("400").getDescription());
    assertEquals("员工不存在", updateModel.getResponses().get("404").getDescription());

    Method unlinkUser = EmployeeController.class.getMethod("unlinkUser", String.class);
    SimpleWebAPIModel unlinkUserModel = extract("DELETE", "/api/organization/employees/{id}/user", mapping("/api/organization/employees/{id}/user", RequestMethod.DELETE), EmployeeController.class, unlinkUser);
    assertEquals("解绑员工用户", unlinkUserModel.getSummary());
    assertEquals("员工用户解绑成功", unlinkUserModel.getResponses().get("200").getDescription());
    assertEquals("员工不存在", unlinkUserModel.getResponses().get("404").getDescription());

    Method delete = EmployeeController.class.getMethod("delete", String.class);
    SimpleWebAPIModel deleteModel = extract("DELETE", "/api/organization/employees/{id}", mapping("/api/organization/employees/{id}", RequestMethod.DELETE), EmployeeController.class, delete);
    assertEquals("删除员工", deleteModel.getSummary());
    assertEquals("员工删除成功", deleteModel.getResponses().get("200").getDescription());
    assertEquals("员工不存在", deleteModel.getResponses().get("404").getDescription());

    Method getOrgChain = EmployeeController.class.getMethod("getOrganizationChain", String.class);
    SimpleWebAPIModel getOrgChainModel = extract("GET", "/api/organization/employees/{id}/org-chain", mapping("/api/organization/employees/{id}/org-chain", RequestMethod.GET), EmployeeController.class, getOrgChain);
    assertEquals("获取员工组织链", getOrgChainModel.getSummary());
    assertEquals("成功返回员工组织链", getOrgChainModel.getResponses().get("200").getDescription());
    assertEquals("员工不存在", getOrgChainModel.getResponses().get("404").getDescription());

    Method getAppointments = EmployeeController.class.getMethod("getAppointments", String.class);
    SimpleWebAPIModel getAppointmentsModel = extract("GET", "/api/organization/employees/{id}/appointments", mapping("/api/organization/employees/{id}/appointments", RequestMethod.GET), EmployeeController.class, getAppointments);
    assertEquals("获取员工任职信息", getAppointmentsModel.getSummary());
    assertEquals("成功返回员工任职记录", getAppointmentsModel.getResponses().get("200").getDescription());
    assertEquals("员工不存在", getAppointmentsModel.getResponses().get("404").getDescription());

    Method linkUser = EmployeeController.class.getMethod("linkUser", String.class, com.ouroboros.organizationstructure.dto.request.LinkUserRequest.class);
    SimpleWebAPIModel linkUserModel = extract("PUT", "/api/organization/employees/{id}/user", mapping("/api/organization/employees/{id}/user", RequestMethod.PUT), EmployeeController.class, linkUser);
    assertEquals("关联员工用户", linkUserModel.getSummary());
    assertEquals(ParameterLocation.PATH, parameter(linkUserModel, "id").getIn());
    assertEquals("员工用户关联请求", linkUserModel.getRequestBody().getDescription());
    assertEquals("员工用户关联成功", linkUserModel.getResponses().get("200").getDescription());
    assertEquals("员工用户关联参数不合法", linkUserModel.getResponses().get("400").getDescription());
    assertEquals("员工或用户不存在", linkUserModel.getResponses().get("404").getDescription());
    assertEquals("员工或用户已存在关联关系", linkUserModel.getResponses().get("409").getDescription());

    Method updatePosition = EmployeeController.class.getMethod("updateEmployeePosition", String.class, com.ouroboros.organizationstructure.dto.request.UpdateEmployeePositionRequest.class);
    SimpleWebAPIModel updatePositionModel = extract("PUT", "/api/organization/employees/{id}/position", mapping("/api/organization/employees/{id}/position", RequestMethod.PUT), EmployeeController.class, updatePosition);
    assertEquals("调整员工岗位", updatePositionModel.getSummary());
    assertEquals("员工岗位调整请求", updatePositionModel.getRequestBody().getDescription());
    assertEquals("员工岗位调整成功", updatePositionModel.getResponses().get("200").getDescription());
    assertEquals("岗位参数不合法", updatePositionModel.getResponses().get("400").getDescription());
    assertEquals("员工或岗位不存在", updatePositionModel.getResponses().get("404").getDescription());
  }

  @Test
  void shouldExtractOrganizationTreeAndNodeContracts() throws Exception {
    Method getTree = OrganizationTreeController.class.getMethod("getTree", String.class, boolean.class, List.class, String.class);
    SimpleWebAPIModel treeModel = extract("GET", "/api/organization/tree", mapping("/api/organization/tree", RequestMethod.GET), OrganizationTreeController.class, getTree);
    assertEquals("获取组织架构树", treeModel.getSummary());
    assertEquals(Collections.singletonList("organization"), treeModel.getTags());
    assertEquals(ParameterLocation.QUERY, parameter(treeModel, "rootId").getIn());
    assertEquals("根节点 ID", parameter(treeModel, "rootId").getDescription());
    assertEquals(ParameterLocation.QUERY, parameter(treeModel, "includePositions").getIn());
    assertEquals("是否包含岗位节点", parameter(treeModel, "includePositions").getDescription());
    assertEquals(ParameterLocation.QUERY, parameter(treeModel, "types").getIn());
    assertEquals(ParameterLocation.QUERY, parameter(treeModel, "type").getIn());
    assertEquals("成功返回组织架构树", treeModel.getResponses().get("200").getDescription());
    assertEquals("组织节点不存在", treeModel.getResponses().get("404").getDescription());

    Method getNode = OrganizationNodeController.class.getMethod("getById", String.class);
    SimpleWebAPIModel getNodeModel = extract("GET", "/api/organization/nodes/{id}", mapping("/api/organization/nodes/{id}", RequestMethod.GET), OrganizationNodeController.class, getNode);
    assertEquals("获取组织节点详情", getNodeModel.getSummary());
    assertEquals(ParameterLocation.PATH, parameter(getNodeModel, "id").getIn());
    assertEquals("组织节点 ID", parameter(getNodeModel, "id").getDescription());
    assertEquals("成功返回组织节点详情", getNodeModel.getResponses().get("200").getDescription());
    assertEquals("组织节点不存在", getNodeModel.getResponses().get("404").getDescription());

    Method getChildren = OrganizationNodeController.class.getMethod("getChildren", String.class);
    SimpleWebAPIModel childrenModel = extract("GET", "/api/organization/nodes/{id}/children", mapping("/api/organization/nodes/{id}/children", RequestMethod.GET), OrganizationNodeController.class, getChildren);
    assertEquals("获取组织节点子节点", childrenModel.getSummary());
    assertEquals("成功返回组织节点子节点", childrenModel.getResponses().get("200").getDescription());
    assertEquals("组织节点不存在", childrenModel.getResponses().get("404").getDescription());

    Method getPath = OrganizationNodeController.class.getMethod("getPath", String.class);
    SimpleWebAPIModel pathModel = extract("GET", "/api/organization/nodes/{id}/path", mapping("/api/organization/nodes/{id}/path", RequestMethod.GET), OrganizationNodeController.class, getPath);
    assertEquals("获取组织节点路径", pathModel.getSummary());
    assertEquals("成功返回组织节点路径", pathModel.getResponses().get("200").getDescription());
    assertEquals("组织节点不存在", pathModel.getResponses().get("404").getDescription());
  }

  @Test
  void shouldExtractOrganizationManagementCrudContracts() throws Exception {
    Method getCompanies = OrganizationManagementController.class.getMethod("getCompanies");
    SimpleWebAPIModel companiesModel = extract("GET", "/api/organization/companies", mapping("/api/organization/companies", RequestMethod.GET), OrganizationManagementController.class, getCompanies);
    assertEquals("列出公司", companiesModel.getSummary());
    assertEquals("成功返回公司列表", companiesModel.getResponses().get("200").getDescription());

    Method createCompany = OrganizationManagementController.class.getMethod("createCompany", CreateCompanyRequest.class);
    SimpleWebAPIModel createCompanyModel = extract("POST", "/api/organization/companies", mapping("/api/organization/companies", RequestMethod.POST), OrganizationManagementController.class, createCompany);
    assertEquals("创建公司", createCompanyModel.getSummary());
    assertNotNull(createCompanyModel.getRequestBody());
    assertEquals("公司创建请求", createCompanyModel.getRequestBody().getDescription());
    assertEquals("公司创建成功", createCompanyModel.getResponses().get("200").getDescription());
    assertEquals("公司参数不合法", createCompanyModel.getResponses().get("400").getDescription());
    assertEquals("创建公司后查询失败", createCompanyModel.getResponses().get("500").getDescription());

    Method getCompany = OrganizationManagementController.class.getMethod("getCompanyById", String.class);
    SimpleWebAPIModel getCompanyModel = extract("GET", "/api/organization/companies/{id}", mapping("/api/organization/companies/{id}", RequestMethod.GET), OrganizationManagementController.class, getCompany);
    assertEquals("获取公司详情", getCompanyModel.getSummary());
    assertEquals(ParameterLocation.PATH, parameter(getCompanyModel, "id").getIn());
    assertEquals("成功返回公司详情", getCompanyModel.getResponses().get("200").getDescription());
    assertEquals("公司不存在", getCompanyModel.getResponses().get("404").getDescription());

    Method updateCompany = OrganizationManagementController.class.getMethod("updateCompany", String.class, UpdateCompanyRequest.class);
    SimpleWebAPIModel updateCompanyModel = extract("PUT", "/api/organization/companies/{id}", mapping("/api/organization/companies/{id}", RequestMethod.PUT), OrganizationManagementController.class, updateCompany);
    assertEquals("更新公司", updateCompanyModel.getSummary());
    assertEquals(ParameterLocation.PATH, parameter(updateCompanyModel, "id").getIn());
    assertEquals("公司更新请求", updateCompanyModel.getRequestBody().getDescription());
    assertEquals("公司更新成功", updateCompanyModel.getResponses().get("200").getDescription());
    assertEquals("公司参数不合法", updateCompanyModel.getResponses().get("400").getDescription());
    assertEquals("公司不存在", updateCompanyModel.getResponses().get("404").getDescription());

    Method deleteCompany = OrganizationManagementController.class.getMethod("deleteCompany", String.class);
    SimpleWebAPIModel deleteCompanyModel = extract("DELETE", "/api/organization/companies/{id}", mapping("/api/organization/companies/{id}", RequestMethod.DELETE), OrganizationManagementController.class, deleteCompany);
    assertEquals("删除公司", deleteCompanyModel.getSummary());
    assertEquals("公司删除成功", deleteCompanyModel.getResponses().get("200").getDescription());
    assertEquals("公司不存在", deleteCompanyModel.getResponses().get("404").getDescription());

    Method getDepartments = OrganizationManagementController.class.getMethod("getDepartments", String.class);
    SimpleWebAPIModel departmentsModel = extract("GET", "/api/organization/departments", mapping("/api/organization/departments", RequestMethod.GET), OrganizationManagementController.class, getDepartments);
    assertEquals("列出部门", departmentsModel.getSummary());
    assertEquals(ParameterLocation.QUERY, parameter(departmentsModel, "companyId").getIn());
    assertEquals("成功返回部门列表", departmentsModel.getResponses().get("200").getDescription());

    Method createDepartment = OrganizationManagementController.class.getMethod("createDepartment", CreateDepartmentRequest.class);
    SimpleWebAPIModel createDepartmentModel = extract("POST", "/api/organization/departments", mapping("/api/organization/departments", RequestMethod.POST), OrganizationManagementController.class, createDepartment);
    assertEquals("创建部门", createDepartmentModel.getSummary());
    assertEquals("部门创建请求", createDepartmentModel.getRequestBody().getDescription());
    assertEquals("部门创建成功", createDepartmentModel.getResponses().get("200").getDescription());
    assertEquals("部门参数不合法", createDepartmentModel.getResponses().get("400").getDescription());
    assertEquals("创建部门后查询失败", createDepartmentModel.getResponses().get("500").getDescription());

    Method getDepartment = OrganizationManagementController.class.getMethod("getDepartmentById", String.class);
    SimpleWebAPIModel getDepartmentModel = extract("GET", "/api/organization/departments/{id}", mapping("/api/organization/departments/{id}", RequestMethod.GET), OrganizationManagementController.class, getDepartment);
    assertEquals("获取部门详情", getDepartmentModel.getSummary());
    assertEquals("成功返回部门详情", getDepartmentModel.getResponses().get("200").getDescription());
    assertEquals("部门不存在", getDepartmentModel.getResponses().get("404").getDescription());

    Method updateDepartment = OrganizationManagementController.class.getMethod("updateDepartment", String.class, UpdateDepartmentRequest.class);
    SimpleWebAPIModel updateDepartmentModel = extract("PUT", "/api/organization/departments/{id}", mapping("/api/organization/departments/{id}", RequestMethod.PUT), OrganizationManagementController.class, updateDepartment);
    assertEquals("更新部门", updateDepartmentModel.getSummary());
    assertEquals(ParameterLocation.PATH, parameter(updateDepartmentModel, "id").getIn());
    assertEquals("部门更新请求", updateDepartmentModel.getRequestBody().getDescription());
    assertEquals("部门更新成功", updateDepartmentModel.getResponses().get("200").getDescription());
    assertEquals("部门参数不合法", updateDepartmentModel.getResponses().get("400").getDescription());
    assertEquals("部门不存在", updateDepartmentModel.getResponses().get("404").getDescription());

    Method deleteDepartment = OrganizationManagementController.class.getMethod("deleteDepartment", String.class);
    SimpleWebAPIModel deleteDepartmentModel = extract("DELETE", "/api/organization/departments/{id}", mapping("/api/organization/departments/{id}", RequestMethod.DELETE), OrganizationManagementController.class, deleteDepartment);
    assertEquals("删除部门", deleteDepartmentModel.getSummary());
    assertEquals("部门删除成功", deleteDepartmentModel.getResponses().get("200").getDescription());
    assertEquals("部门不存在", deleteDepartmentModel.getResponses().get("404").getDescription());

    Method getPositionDefinitions = OrganizationManagementController.class.getMethod("getPositionDefinitions");
    SimpleWebAPIModel definitionsModel = extract("GET", "/api/organization/position-definitions", mapping("/api/organization/position-definitions", RequestMethod.GET), OrganizationManagementController.class, getPositionDefinitions);
    assertEquals("列出全局岗位定义", definitionsModel.getSummary());
    assertEquals("成功返回全局岗位定义列表", definitionsModel.getResponses().get("200").getDescription());

    Method createPosition = OrganizationManagementController.class.getMethod("createPosition", CreatePositionRequest.class);
    SimpleWebAPIModel createPositionModel = extract("POST", "/api/organization/positions", mapping("/api/organization/positions", RequestMethod.POST), OrganizationManagementController.class, createPosition);
    assertEquals("创建岗位", createPositionModel.getSummary());
    assertEquals("岗位创建请求", createPositionModel.getRequestBody().getDescription());
    assertEquals("岗位创建成功", createPositionModel.getResponses().get("200").getDescription());
    assertEquals("岗位参数不合法", createPositionModel.getResponses().get("400").getDescription());
    assertEquals("创建岗位后查询失败", createPositionModel.getResponses().get("500").getDescription());

    Method getPosition = OrganizationManagementController.class.getMethod("getPositionById", String.class);
    SimpleWebAPIModel getPositionModel = extract("GET", "/api/organization/positions/{id}", mapping("/api/organization/positions/{id}", RequestMethod.GET), OrganizationManagementController.class, getPosition);
    assertEquals("获取岗位详情", getPositionModel.getSummary());
    assertEquals("成功返回岗位详情", getPositionModel.getResponses().get("200").getDescription());
    assertEquals("岗位不存在", getPositionModel.getResponses().get("404").getDescription());

    Method updatePosition = OrganizationManagementController.class.getMethod("updatePosition", String.class, UpdatePositionRequest.class);
    SimpleWebAPIModel updatePositionModel = extract("PUT", "/api/organization/positions/{id}", mapping("/api/organization/positions/{id}", RequestMethod.PUT), OrganizationManagementController.class, updatePosition);
    assertEquals("更新岗位", updatePositionModel.getSummary());
    assertEquals(ParameterLocation.PATH, parameter(updatePositionModel, "id").getIn());
    assertEquals("岗位更新请求", updatePositionModel.getRequestBody().getDescription());
    assertEquals("岗位更新成功", updatePositionModel.getResponses().get("200").getDescription());
    assertEquals("岗位参数不合法", updatePositionModel.getResponses().get("400").getDescription());
    assertEquals("岗位不存在", updatePositionModel.getResponses().get("404").getDescription());

    Method deletePosition = OrganizationManagementController.class.getMethod("deletePosition", String.class);
    SimpleWebAPIModel deletePositionModel = extract("DELETE", "/api/organization/positions/{id}", mapping("/api/organization/positions/{id}", RequestMethod.DELETE), OrganizationManagementController.class, deletePosition);
    assertEquals("删除岗位", deletePositionModel.getSummary());
    assertEquals("岗位删除成功", deletePositionModel.getResponses().get("200").getDescription());
    assertEquals("岗位不存在", deletePositionModel.getResponses().get("404").getDescription());

    Method getAppointments = OrganizationManagementController.class.getMethod("getAppointments", String.class, String.class, String.class);
    SimpleWebAPIModel appointmentsModel = extract("GET", "/api/organization/appointments", mapping("/api/organization/appointments", RequestMethod.GET), OrganizationManagementController.class, getAppointments);
    assertEquals("列出任职", appointmentsModel.getSummary());
    assertEquals(ParameterLocation.QUERY, parameter(appointmentsModel, "employeeId").getIn());
    assertEquals(ParameterLocation.QUERY, parameter(appointmentsModel, "positionId").getIn());
    assertEquals(ParameterLocation.QUERY, parameter(appointmentsModel, "departmentId").getIn());
    assertEquals("成功返回任职列表", appointmentsModel.getResponses().get("200").getDescription());

    Method createAppointment = OrganizationManagementController.class.getMethod("createAppointment", CreateAppointmentRequest.class);
    SimpleWebAPIModel createAppointmentModel = extract("POST", "/api/organization/appointments", mapping("/api/organization/appointments", RequestMethod.POST), OrganizationManagementController.class, createAppointment);
    assertEquals("创建任职", createAppointmentModel.getSummary());
    assertEquals("任职创建请求", createAppointmentModel.getRequestBody().getDescription());
    assertEquals("任职创建成功", createAppointmentModel.getResponses().get("200").getDescription());
    assertEquals("创建任职后查询失败", createAppointmentModel.getResponses().get("500").getDescription());

    Method deleteAppointment = OrganizationManagementController.class.getMethod("deleteAppointment", String.class);
    SimpleWebAPIModel deleteAppointmentModel = extract("DELETE", "/api/organization/appointments/{id}", mapping("/api/organization/appointments/{id}", RequestMethod.DELETE), OrganizationManagementController.class, deleteAppointment);
    assertEquals("删除任职", deleteAppointmentModel.getSummary());
    assertEquals("任职删除成功", deleteAppointmentModel.getResponses().get("200").getDescription());
    assertEquals("任职不存在", deleteAppointmentModel.getResponses().get("404").getDescription());

    Method assignPosition = OrganizationManagementController.class.getMethod("assignPosition", String.class, AssignPositionRequest.class);
    SimpleWebAPIModel assignPositionModel = extract("POST", "/api/organization/employees/{employeeId}/assign-position", mapping("/api/organization/employees/{employeeId}/assign-position", RequestMethod.POST), OrganizationManagementController.class, assignPosition);
    assertEquals("为员工分配岗位", assignPositionModel.getSummary());
    assertEquals(ParameterLocation.PATH, parameter(assignPositionModel, "employeeId").getIn());
    assertEquals("岗位分配请求", assignPositionModel.getRequestBody().getDescription());
    assertEquals("岗位分配成功", assignPositionModel.getResponses().get("200").getDescription());
    assertEquals("岗位分配参数不合法", assignPositionModel.getResponses().get("400").getDescription());
    assertEquals("员工、岗位或既有任职不存在", assignPositionModel.getResponses().get("404").getDescription());
    assertEquals("目标岗位已有冲突任职", assignPositionModel.getResponses().get("409").getDescription());
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Class<?> controllerClass, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder().api(httpMethod, path).scope(WebAPIScope.INTERNAL).handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        controllerClass,
        method
    ).build();
  }

  private RequestMappingInfo mapping(String path, RequestMethod method) {
    return RequestMappingInfo.paths(path).methods(method).consumes("application/json").produces("application/json").build();
  }

  private com.ouroboros.webapi.openapi.ParameterModel parameter(SimpleWebAPIModel model, String name) {
    return model.getParameters().stream().filter(parameter -> name.equals(parameter.getName())).findFirst().orElseThrow(() -> new AssertionError("missing parameter " + name));
  }
}
