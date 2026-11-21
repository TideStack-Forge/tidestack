package com.ouroboros.dev.datasource.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.dev.datasource.model.AppDataSourceModel;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * @author lianshuze
 **/
@RestController
@RequestMapping("/api/dev/{appName}/datasource")
@SuppressWarnings("unused")
public class AppDataSourceController {
  private final AppDataSourceService appDataSourceService;

  public AppDataSourceController(AppDataSourceService appDataSourceService) {
    this.appDataSourceService = appDataSourceService;
  }

  @PostMapping("")
  public WebApiResult<?> add(@PathVariable String appName, @RequestBody AppDataSourceModel model) {
    if (ObjectUtils.isEmpty(model.getAppName())) {
      return WebApiResult.fail("APP名称不能为空");
    }


    if (ObjectUtils.isEmpty(model.getDataStationName())) {
      return WebApiResult.fail("APP数据源名称不能为空");
    }


    if (ObjectUtils.isEmpty(model.getPlatformDataSourceName())) {
      return WebApiResult.fail("平台数据源名称不能为空");
    }


    Map<String, Object> where = new HashMap<String, Object>();
    where.put("appName", appName);
    where.put("dataStationName", model.getDataStationName());
    if (appDataSourceService.count(where) > 0) {
      return WebApiResult.fail("当前应用已分配过" + model.getDataStationName() + "数据源");
    }


    model.setAppName(appName);

    return WebApiResult.of(appDataSourceService.add(model));
  }

  @DeleteMapping("/{id}")
  public WebApiResult<?> delete(@PathVariable String appName, @PathVariable String id) {
    return WebApiResult.of(appDataSourceService.delete(id));
  }

  @PutMapping("/{id}")
  public WebApiResult<?> update(@PathVariable String appName, @PathVariable String id, @RequestBody AppDataSourceModel model) {
    if (ObjectUtils.isEmpty(appName)) {
      return WebApiResult.fail("AP名称不能为空");
    }


    if (ObjectUtils.isEmpty(model.getDataStationName())) {
      return WebApiResult.fail("APP数据源名称不能为空");
    }


    if (ObjectUtils.isEmpty(model.getPlatformDataSourceName())) {
      return WebApiResult.fail("平台数据源名称不能为空");
    }


    Map<String, Object> where = new HashMap<String, Object>();
    where.put("appName", appName);
    where.put("dataStationName", model.getDataStationName());
    where.put("id", Collections.singletonMap("!=", id));
    if (appDataSourceService.count(where) > 0) {
      return WebApiResult.fail("当前应用已分配过" + model.getDataStationName() + "数据源");
    }


    model.setAppName(appName);

    return WebApiResult.of(appDataSourceService.update(id, model));
  }

  @GetMapping("/{id}")
  public WebApiResult<?> getOne(@PathVariable String appName, @PathVariable String id) {
    return WebApiResult.of(appDataSourceService.getByPrimaryKey(id));
  }

  @GetMapping("")
  public PagedWebApiResult<?> search(@PathVariable String appName, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int perPage) {
    Map<String, Object> where = new HashMap<String, Object>();
    where.put("appName", appName);
    Long total = appDataSourceService.count(where);

    Map<String, Object> statement = new HashMap<String, Object>();
    statement.put(Keyword.WHERE.toString(), where);
    statement.put(Keyword.LIMIT.toString(), perPage);
    statement.put(Keyword.OFFSET.toString(), (page - 1) * perPage);

    List<AppDataSourceModel> models = total > 0 ? appDataSourceService.query(statement) : Collections.emptyList();

    return PagedWebApiResult.ok(total, models);
  }

  @GetMapping("/{dataStationName}/table")
  public WebApiResult<?> getTables(@PathVariable String appName, @PathVariable String dataStationName) {
    var tables = appDataSourceService.getTableInfo(appName, dataStationName, "dev");
    return WebApiResult.ok(tables);
  }
}
