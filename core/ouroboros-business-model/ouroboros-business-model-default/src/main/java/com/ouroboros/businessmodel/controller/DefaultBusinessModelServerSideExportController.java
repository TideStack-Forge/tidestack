package com.ouroboros.businessmodel.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.businessmodel.action.BusinessActionInput;
import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.util.FileResult;
import com.ouroboros.web.FileResponses;

/** Retains the original export URL while sharing the governed Action execution. */
@RestController
@RequestMapping("/api/default-business-model-data-export/{fullName}")
public class DefaultBusinessModelServerSideExportController {
  private final BusinessActionRuntime actions;

  public DefaultBusinessModelServerSideExportController(BusinessActionRuntime actions) { this.actions = actions; }

  @Operation(
      summary = "导出业务模型 Excel",
      description = "根据业务模型全名和查询条件导出业务模型数据为 Excel 文件。",
      tags = {"business-model"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Excel 导出成功"),
      @ApiResponse(responseCode = "400", description = "导出参数不合法或业务模型不存在")
  })
  @GetMapping("/export-excel")
  public void exportExcel(
      @Parameter(description = "业务模型全名", required = true)
      @PathVariable("fullName") String fullName,
      @Parameter(name = "params", description = "导出查询条件")
      @RequestParam Map<String, Object> params,
      HttpServletResponse response) throws java.io.IOException {
    var result = actions.invoke(fullName, "export", BusinessActionInput.paramsOnly(Map.of("query", params)));
    FileResponses.write(FileResult.class.cast(result), response);
  }
}
