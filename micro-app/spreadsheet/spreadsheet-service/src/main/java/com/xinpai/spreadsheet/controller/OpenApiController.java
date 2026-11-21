package com.xinpai.spreadsheet.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletResponse;

import com.alibaba.fastjson.JSONObject;
import com.xinpai.spreadsheet.db.entity.SpreadsheetBlockModel;
import com.xinpai.spreadsheet.db.entity.SpreadsheetModel;
import com.xinpai.spreadsheet.dto.CreateSpreadsheetRequest;
import com.xinpai.spreadsheet.dto.AccessTokenPayload;
import com.xinpai.spreadsheet.dto.AccessTokenResult;
import com.xinpai.spreadsheet.dto.ResponseVO;
import com.xinpai.spreadsheet.service.OpenApiService;
import com.xinpai.spreadsheet.service.ReadSpreadsheetService;
import com.xinpai.spreadsheet.service.TokenService;
import com.xinpai.spreadsheet.service.WriteSpreadsheetService;
import com.xinpai.spreadsheet.utils.StringUtil;
import com.xinpai.spreadsheet.xlsutils.poiutil.XlsUtil;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/api/open")
@RestController
public class OpenApiController {

  @Autowired
  private OpenApiService openApiService;

  @Autowired
  private TokenService tokenService;

  @Autowired
  private WriteSpreadsheetService writeSpreadsheetService;

  @Autowired
  private ReadSpreadsheetService readSpreadsheetService;

  // 获取编辑token
  @PostMapping("/token")
  public ResponseVO getEditToken(@RequestBody(required = true) AccessTokenPayload payload,
      HttpServletResponse response) {
    String spreadsheetId = payload.getSpreadsheetId();
    String appId = payload.getAppId();

    if (StringUtil.isNullOrEmpty(spreadsheetId)) {
      response.setStatus(400);
      return ResponseVO.errorInstance("spreadsheetId不能为空");
    }

    // 校验spreadsheetId
    if (!openApiService.isSpreadsheetExists(spreadsheetId, appId)) {
      response.setStatus(404);
      return ResponseVO.errorInstance("Not Found");
    }

    SpreadsheetModel spreadsheetModel = openApiService.getSpreadsheetInfo(spreadsheetId);

    AccessTokenResult data = new AccessTokenResult();
    data.setSpreadsheetId(payload.getSpreadsheetId());
    data.setSpreadsheetName(spreadsheetModel.getName());
    data.setToken(tokenService.generateAccessToken(payload));
    return ResponseVO.successInstance(data);
  }

  // 新建文件
  @PostMapping("/create")
  public ResponseVO createSpreadsheet(@RequestBody(required = true) CreateSpreadsheetRequest request,
      HttpServletResponse response) {
    String appId = request.getAppId();
    String spreadsheetName = request.getSpreadsheetName();
    String editorName = request.getEditorName();

    if (StringUtil.isNullOrEmpty(spreadsheetName)) {
      response.setStatus(400);
      return ResponseVO.errorInstance("spreadsheetName不能为空");
    }

    if (StringUtil.isNullOrEmpty(editorName)) {
      response.setStatus(400);
      return ResponseVO.errorInstance("editorName不能为空");
    }

    SpreadsheetModel spreadsheet = new SpreadsheetModel();
    spreadsheet.setName(spreadsheetName);
    spreadsheet.setApp_id(appId);
    spreadsheet.setCreated_by(editorName);
    spreadsheet.setUpdated_by(editorName);

    // 创建文件
    spreadsheet = openApiService.createSpreadsheet(spreadsheet);
    if (spreadsheet == null) {
      return ResponseVO.errorInstance("创建失败");
    }

    AccessTokenPayload payload = new AccessTokenPayload();
    payload.setSpreadsheetId(spreadsheet.getId());
    payload.setAppId(appId);
    payload.setEditorName(editorName);
    payload.setReadonly(false);

    // 返回edit token
    AccessTokenResult result = new AccessTokenResult();
    result.setSpreadsheetId(spreadsheet.getId());
    result.setSpreadsheetName(spreadsheet.getName());
    result.setToken(tokenService.generateAccessToken(payload));
    return ResponseVO.successInstance(result);
  }

  // 删除文件
  @PostMapping("/delete")
  public ResponseVO deleteSpreadsheet(@RequestBody(required = true) Map<String, String> request,
      HttpServletResponse response) {
    String appId = request.get("appId");
    String spreadsheetId = request.get("spreadsheetId");
    String editorName = request.get("editorName");

    // 验证editorName是否为空
    if (StringUtil.isNullOrEmpty(editorName)) {
      response.setStatus(400);
      return ResponseVO.errorInstance("editorName不能为空");
    }

    // 验证spreadsheetId是否存在
    if (!openApiService.isSpreadsheetExists(spreadsheetId, appId)) {
      response.setStatus(404);
      return ResponseVO.errorInstance("Not found");
    }

    // 删除文件
    if (!openApiService.deleteSpreadsheet(spreadsheetId, editorName)) {
      response.setStatus(500);
      return ResponseVO.errorInstance("DB error");
    }

    return ResponseVO.successInstance();
  }

  // 重命名文件
  @PostMapping("/rename")
  public ResponseVO renameSpreadsheet(@RequestBody(required = true) Map<String, String> request,
      HttpServletResponse response) {
    String appId = request.get("appId");
    String spreadsheetId = request.get("spreadsheetId");
    String spreadsheetName = request.get("spreadsheetName");
    String editorName = request.get("editorName");

    // 验证spreadsheetId是否为空
    if (StringUtil.isNullOrEmpty(spreadsheetId)) {
      response.setStatus(400);
      return ResponseVO.errorInstance("spreadsheetId不能为空");
    }

    // 验证spreadsheetName是否为空
    if (StringUtil.isNullOrEmpty(spreadsheetName)) {
      response.setStatus(400);
      return ResponseVO.errorInstance("spreadsheetName不能为空");
    }

    // 验证editorName是否为空
    if (StringUtil.isNullOrEmpty(editorName)) {
      response.setStatus(400);
      return ResponseVO.errorInstance("editorName不能为空");
    }

    // 验证spreadsheetId是否存在
    if (!openApiService.isSpreadsheetExists(spreadsheetId, appId)) {
      response.setStatus(404);
      return ResponseVO.errorInstance("Not found");
    }

    // 重命名文件
    if (!openApiService.renameSpreadsheet(spreadsheetId, spreadsheetName, editorName)) {
      response.setStatus(500);
      return ResponseVO.errorInstance("DB error");
    }

    return ResponseVO.successInstance();
  }

  // 获取文件信息
  @PostMapping("/info")
  public ResponseVO getSpreadsheetInfo(@RequestBody(required = true) Map<String, String> request,
      HttpServletResponse response) {
    String appId = request.get("appId");
    String spreadsheetId = request.get("spreadsheetId");

    // 验证spreadsheetId是否为空
    if (StringUtil.isNullOrEmpty(spreadsheetId)) {
      response.setStatus(400);
      return ResponseVO.errorInstance("spreadsheetId不能为空");
    }

    // 验证spreadsheetId是否合法
    if (!openApiService.isSpreadsheetExists(spreadsheetId, appId)) {
      response.setStatus(404);
      return ResponseVO.errorInstance("Not found");
    }

    // 获取文件信息
    SpreadsheetModel spreadsheet = openApiService.getSpreadsheetInfo(spreadsheetId);
    if (spreadsheet == null) {
      response.setStatus(500);
      return ResponseVO.errorInstance("DB error");
    }

    return ResponseVO.successInstance(spreadsheet);
  }

  @PostMapping("/import")
  public ResponseVO importExcel(@RequestParam("file") MultipartFile file, @RequestParam String appId,
      @RequestParam(required = false) String spreadsheetName,
      @RequestParam String editorName, HttpServletResponse response) {
    try {
      // 验证file是否为空
      if (file == null || file.isEmpty()) {
        response.setStatus(400);
        return ResponseVO.errorInstance("文件为空");
      }

      // 验证editorName是否为空
      if (StringUtil.isNullOrEmpty(editorName)) {
        response.setStatus(400);
        return ResponseVO.errorInstance("editorName不能为空");
      }

      InputStream inputStream = file.getInputStream();
      if (Objects.requireNonNull(file.getOriginalFilename()).endsWith(".xls")
          || file.getOriginalFilename().endsWith(".xlsx")) {
        Workbook workbook = WorkbookFactory.create(inputStream);
        String fileName = spreadsheetName != null ? spreadsheetName
            : file.getOriginalFilename().substring(0, file.getOriginalFilename().lastIndexOf("."));
        // 读取EXCEL
        List<SpreadsheetBlockModel> modelList = XlsUtil.readExcel(workbook);

        SpreadsheetModel spreadsheet = new SpreadsheetModel();
        spreadsheet.setApp_id(appId);
        spreadsheet.setName(fileName);
        spreadsheet.setCreated_by(editorName);
        spreadsheet.setUpdated_by(editorName);
        spreadsheet = openApiService.createSpreadsheet(spreadsheet);

        // 插入文档数据
        writeSpreadsheetService.cloneSpreadsheetBlocks(modelList, spreadsheet.getId());

        // 准备token payload
        AccessTokenPayload payload = new AccessTokenPayload();
        payload.setSpreadsheetId(spreadsheet.getId());
        payload.setAppId(appId);
        payload.setEditorName(editorName);
        payload.setReadonly(false);

        // 返回文件信息
        AccessTokenResult result = new AccessTokenResult();
        result.setSpreadsheetId(spreadsheet.getId());
        result.setSpreadsheetName(spreadsheet.getName());
        result.setToken(tokenService.generateAccessToken(payload));
        return ResponseVO.successInstance(result);
      } else {
        response.setStatus(400);
        return ResponseVO.errorInstance("File type not support");
      }
    } catch (Exception e) {
      response.setStatus(500);
      return ResponseVO.errorInstance("Spreadsheet server error");
    }
  }

  @PostMapping("/export")
  public ResponseVO exportXlsx(HttpServletResponse response, @RequestBody Map<String, String> request) {
    String appId = request.get("appId");
    String spreadsheetId = request.get("spreadsheetId");
    Boolean isZip = "1".equals(request.get("zip"));

    // 验证spreadsheetId是否为空
    if (StringUtil.isNullOrEmpty(spreadsheetId)) {
      response.setStatus(400);
      return ResponseVO.errorInstance("spreadsheetId不能为空");
    }

    SpreadsheetModel spreadsheet = openApiService.getSpreadsheetInfo(spreadsheetId);
    if (spreadsheet == null || spreadsheet.getIs_deleted() == 1 || !appId.equals(spreadsheet.getApp_id())) {
      response.setStatus(404);
      return ResponseVO.errorInstance("Not found");
    }

    String finalFileName = spreadsheet.getName();

    // 获取全部数据
    List<JSONObject> spreadsheetBlocks = readSpreadsheetService.getAllSheetBySpreadsheetId(spreadsheetId);
    ByteArrayOutputStream xlsxStream = new ByteArrayOutputStream();
    try {
      XlsUtil.exportXlsFile(xlsxStream, true, spreadsheetBlocks);
    } catch (Exception e) {
      response.setStatus(500);
      return ResponseVO.errorInstance("I/O error");
    }

    // 输出的文件名
    try {
      String _fileName = new String(finalFileName.getBytes("gb2312"), "ISO8859-1");
      response.setHeader("Content-Disposition", "attachment;fileName=" + _fileName + (isZip ? ".zip" : ".xlsx"));
    } catch (Exception e) {
      response.setStatus(500);
      return ResponseVO.errorInstance("Filename error");
    }

    // 若有zip参数则打包成zip文件
    if (isZip) {
      response.setContentType("application/zip");
      byte[] data = xlsxStream.toByteArray();
      try {
        ZipOutputStream out = new ZipOutputStream(response.getOutputStream());
        ZipEntry zipEntry = new ZipEntry(finalFileName + ".xlsx");
        zipEntry.setSize(data.length);
        zipEntry.setTime(System.currentTimeMillis());
        out.putNextEntry(zipEntry);
        out.write(data);
        out.closeEntry();
        out.finish();
        return null;
      } catch (Exception ex) {
        response.setStatus(500);
        return ResponseVO.errorInstance("I/O error");
      }
    }

    // 否则直接输出
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    try {
      xlsxStream.writeTo(response.getOutputStream());
    } catch (IOException e) {
      response.setStatus(500);
      return ResponseVO.errorInstance("I/O error");
    }
    return null;
  }
}
