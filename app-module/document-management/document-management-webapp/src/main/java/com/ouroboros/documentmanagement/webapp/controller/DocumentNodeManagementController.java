package com.ouroboros.documentmanagement.webapp.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.documentmanagement.model.DirectoryNode;
import com.ouroboros.documentmanagement.model.DocumentNode;
import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade;
import com.ouroboros.documentmanagement.webapp.util.UserContextHolder;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/document-libraries/{libraryId}")
public class DocumentNodeManagementController extends DocumentControllerSupport {

  public DocumentNodeManagementController(DocumentLibraryContentFacade facade) {
    super(facade);
  }

  @Operation(
      summary = "创建目录",
      description = "在档案库指定父目录下创建一个新的子目录。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "目录创建成功"),
      @ApiResponse(responseCode = "400", description = "目录创建参数不合法"),
      @ApiResponse(responseCode = "404", description = "父目录不存在或当前用户无权访问")
  })
  @PostMapping("/directories")
  public WebApiResult<DirectoryNode> createDirectory(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "目录创建请求", required = true)
      @Valid @RequestBody DocumentLibraryContentRequests.CreateDirectoryRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    DirectoryNode directory =
        facade.createDirectory(libraryId, request.parentDirectoryId, request.directoryName, userId);
    return WebApiResult.success(directory);
  }

  @Operation(
      summary = "移动文件",
      description = "将指定文件移动到另一个目录下。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "文件移动成功"),
      @ApiResponse(responseCode = "400", description = "移动请求参数不合法"),
      @ApiResponse(responseCode = "404", description = "文件或目标目录不存在，或当前用户无权访问")
  })
  @PutMapping("/documents/{documentId}/move")
  public WebApiResult<DocumentNode> moveDocument(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "文件 ID", required = true)
      @PathVariable("documentId") String documentId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "文件移动请求", required = true)
      @Valid @RequestBody DocumentLibraryContentRequests.MoveNodeRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    DocumentNode document =
        facade.moveDocument(libraryId, documentId, request.targetDirectoryId, userId);
    return WebApiResult.success(document);
  }

  @Operation(
      summary = "移动目录",
      description = "将指定目录移动到另一个目录下。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "目录移动成功"),
      @ApiResponse(responseCode = "400", description = "移动请求参数不合法"),
      @ApiResponse(responseCode = "404", description = "目录或目标目录不存在，或当前用户无权访问")
  })
  @PutMapping("/directories/{directoryId}/move")
  public WebApiResult<DirectoryNode> moveDirectory(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "目录移动请求", required = true)
      @Valid @RequestBody DocumentLibraryContentRequests.MoveDirectoryRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    DirectoryNode directory =
        facade.moveDirectory(libraryId, directoryId, request.targetDirectoryId, userId);
    return WebApiResult.success(directory);
  }

  @Operation(
      summary = "重命名文件",
      description = "更新指定文件的名称。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "文件重命名成功"),
      @ApiResponse(responseCode = "400", description = "重命名参数不合法"),
      @ApiResponse(responseCode = "404", description = "文件不存在或当前用户无权访问")
  })
  @PutMapping("/documents/{documentId}/rename")
  public WebApiResult<DocumentNode> renameDocument(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "文件 ID", required = true)
      @PathVariable("documentId") String documentId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "文件重命名请求", required = true)
      @Valid @RequestBody DocumentLibraryContentRequests.RenameRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    DocumentNode document = facade.renameDocument(libraryId, documentId, request.newName, userId);
    return WebApiResult.success(document);
  }

  @Operation(
      summary = "重命名目录",
      description = "更新指定目录的名称。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "目录重命名成功"),
      @ApiResponse(responseCode = "400", description = "重命名参数不合法"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @PutMapping("/directories/{directoryId}/rename")
  public WebApiResult<DirectoryNode> renameDirectory(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "目录重命名请求", required = true)
      @Valid @RequestBody DocumentLibraryContentRequests.RenameDirectoryRequest request) {
    String userId = UserContextHolder.requireCurrentUserId();
    DirectoryNode directory = facade.renameDirectory(libraryId, directoryId, request.newName, userId);
    return WebApiResult.success(directory);
  }

  @Operation(
      summary = "删除文件",
      description = "删除指定文件。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "文件删除成功"),
      @ApiResponse(responseCode = "404", description = "文件不存在或当前用户无权访问")
  })
  @DeleteMapping("/documents/{documentId}")
  public WebApiResult<Void> deleteDocument(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "文件 ID", required = true)
      @PathVariable("documentId") String documentId) {
    String userId = UserContextHolder.requireCurrentUserId();
    facade.deleteDocument(libraryId, documentId, userId);
    return WebApiResult.success();
  }

  @Operation(
      summary = "删除目录",
      description = "递归删除指定目录及其全部子节点。",
      tags = {"document-library"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "目录删除成功"),
      @ApiResponse(responseCode = "404", description = "目录不存在或当前用户无权访问")
  })
  @DeleteMapping("/directories/{directoryId}")
  public WebApiResult<Void> deleteDirectory(
      @Parameter(description = "档案库 ID", required = true)
      @PathVariable("libraryId") String libraryId,
      @Parameter(description = "目录 ID", required = true)
      @PathVariable("directoryId") String directoryId) {
    String userId = UserContextHolder.requireCurrentUserId();
    facade.deleteDirectory(libraryId, directoryId, userId);
    return WebApiResult.success();
  }
}
