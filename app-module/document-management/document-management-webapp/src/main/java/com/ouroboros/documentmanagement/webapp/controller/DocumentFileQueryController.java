package com.ouroboros.documentmanagement.webapp.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.documentmanagement.model.DocumentNode;
import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade.DirectoryStatistics;
import com.ouroboros.documentmanagement.webapp.dto.NodeItemDTO;
import com.ouroboros.documentmanagement.webapp.util.UserContextHolder;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/document-libraries/{libraryId}")
public class DocumentFileQueryController extends DocumentControllerSupport {

  public DocumentFileQueryController(DocumentLibraryContentFacade facade) {
    super(facade);
  }

  @Operation(
      summary = "查询文件详情",
      description = "根据文件 ID 查询文件节点详情。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回文件详情"),
      @ApiResponse(responseCode = "404", description = "文件不存在或当前用户无权访问")
  })
  @GetMapping("/documents/{documentId}")
  public WebApiResult<DocumentNode> getDocument(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "文件 ID", required = true)
      @PathVariable("documentId") String documentId) {
    String userId = UserContextHolder.requireCurrentUserId();
    return facade
        .getDocument(libraryId, documentId, userId)
        .map(WebApiResult::success)
        .orElse(notFound("文件不存在或无权限访问"));
  }

  @Operation(
      summary = "查询目录详情",
      description = "根据目录 ID 查询目录节点详情及其权限信息。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回目录详情"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @GetMapping("/directories/{directoryId}")
  public WebApiResult<NodeItemDTO> getDirectory(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    return facade
        .getDirectory(libraryId, directoryId, userId)
        .map(dir -> convertToNodeItemDTO(dir, libraryId, userId))
        .map(WebApiResult::success)
        .orElse(notFound("目录不存在或无权限访问"));
  }

  @Operation(
      summary = "搜索档案库文件",
      description = "在整个档案库范围内按名称关键字搜索文件。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回文件搜索结果")
  })
  @GetMapping("/search")
  public ItemsWebApiResult<DocumentNode> searchDocuments(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "文件名称匹配模式", required = true)
      @RequestParam("namePattern") String namePattern) {
    String userId = UserContextHolder.requireCurrentUserId();
    List<DocumentNode> documents = facade.searchDocuments(libraryId, namePattern, userId);
    return ItemsWebApiResult.ok(documents);
  }

  @Operation(
      summary = "搜索目录文件",
      description = "在指定目录及其子目录范围内按名称关键字搜索文件。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回文件搜索结果"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @GetMapping("/directories/{directoryId}/search")
  public ItemsWebApiResult<DocumentNode> searchDocumentsInDirectory(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId,
      @Parameter(description = "文件名称匹配模式", required = true)
      @RequestParam("namePattern") String namePattern) {
    String userId = UserContextHolder.requireCurrentUserId();
    List<DocumentNode> documents =
        facade.searchDocumentsInDirectory(libraryId, directoryId, namePattern, userId);
    return ItemsWebApiResult.ok(documents);
  }

  @Operation(
      summary = "查询目录统计信息",
      description = "查询指定目录下的文件数、目录数与容量等统计信息。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回目录统计信息"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @GetMapping("/directories/{directoryId}/statistics")
  public WebApiResult<DirectoryStatistics> getDirectoryStatistics(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    DirectoryStatistics statistics = facade.getDirectoryStatistics(libraryId, directoryId, userId);
    return WebApiResult.success(statistics);
  }
}
