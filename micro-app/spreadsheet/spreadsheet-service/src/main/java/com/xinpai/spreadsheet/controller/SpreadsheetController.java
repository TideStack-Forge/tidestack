package com.xinpai.spreadsheet.controller;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import com.alibaba.fastjson.JSONObject;
import com.xinpai.spreadsheet.db.entity.SpreadsheetModel;
import com.xinpai.spreadsheet.dto.ResponseVO;
import com.xinpai.spreadsheet.dto.SpreadsheetInfo;
import com.xinpai.spreadsheet.service.ReadSpreadsheetService;
import com.xinpai.spreadsheet.service.TokenService;
import com.xinpai.spreadsheet.utils.GzipUtil;
import com.xinpai.spreadsheet.utils.JsonUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@Api("spreadsheet API")
@RequestMapping(value = { "/api/spreadsheet" })
public class SpreadsheetController {
    final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private ReadSpreadsheetService spreadsheetService;

    @Autowired
    private TokenService tokenService;

    @ApiOperation(value = "加载电子表格基本信息", notes = "加载电子表格基本信息")
    @GetMapping("/info")
    public ResponseVO getInfo(@RequestAttribute String spreadsheetId, @RequestAttribute Boolean readonly,
            HttpServletResponse response) {
        SpreadsheetModel spreadsheet = spreadsheetService.getSpreadsheetInfo(spreadsheetId);
        if (spreadsheet == null) {
            response.setStatus(404);
            return ResponseVO.errorInstance("Not found");
        }
        return ResponseVO.successInstance(new SpreadsheetInfo(spreadsheet, readonly));
    }

    /**
     * 默认加载表格 分块
     * 
     * @param spreadsheetId
     * @param response
     * @return
     */
    @ApiOperation(value = "默认加载表格", notes = "默认加载表格")
    @PostMapping("/load")
    public String load(@RequestAttribute String spreadsheetId, HttpServletResponse response) {
        String jsonStr = "";
        List<JSONObject> blocks = spreadsheetService.getDefaultBySpreadsheetId(spreadsheetId);
        if (blocks != null) {
            delErrorKey(blocks);
            jsonStr = JsonUtil.toJson(blocks);
        }

        // 告诉浏览器，当前发送的是gzip格式的内容
        response.setHeader("Content-Encoding", "gzip");
        response.setContentType("text/html");
        try {
            OutputStream out = response.getOutputStream();
            out.write(GzipUtil.compress2(jsonStr));
            out.close();
            out.flush();
        } catch (Exception e) {
            log.error("Load spreadsheet I/O error:" + e);
        }
        return null;
    }

    /**
     * 加载指定表格
     * 
     * @param spreadsheetId
     * @param index
     * @param response
     * @return
     */
    @ApiOperation(value = "加载指定表格", notes = "加载指定表格")
    @PostMapping("/loadsheets")
    @SuppressWarnings("rawtypes")
    public byte[] loadSheets(@RequestAttribute String spreadsheetId,
            @RequestParam(defaultValue = "") String[] index,
            HttpServletResponse response) {
        String jsonStr = "";
        LinkedHashMap blocks = spreadsheetService.getBySpreadsheetIdAndIndexs(spreadsheetId, Arrays.asList(index));
        if (blocks != null) {
            jsonStr = JsonUtil.toJson(blocks);
        }

        byte zippedData[] = GzipUtil.compress2(jsonStr);
        OutputStream out = null;
        try {
            response.setHeader("Content-Encoding", "gzip");
            response.setContentType("text/html");
            out = response.getOutputStream();
            out.write(zippedData);
            out.close();
            out.flush();
        } catch (Exception ex) {
            log.error("Loadsheet I/O Error:" + ex);
        }

        return null;
    }

    /**
     * 数据返回时，去掉数组变字符串，引发错误的key
     * 删除会发生错误的对象
     */
    private void delErrorKey(List<JSONObject> dbObject) {
        if (dbObject != null) {
            for (JSONObject obj : dbObject) {
                delErrorKeyByCheck(obj, "calcChain");
                delErrorKeyByCheck(obj, "luckysheet_alternateformat_save");
                delErrorKeyByCheck(obj, "luckysheet_conditionformat_save");
            }
        }
    }

    private void delErrorKeyByCheck(JSONObject obj, String key) {
        if (obj.containsKey(key) && obj.get(key) instanceof String) {
            obj.remove(key);
        }
    }
}
