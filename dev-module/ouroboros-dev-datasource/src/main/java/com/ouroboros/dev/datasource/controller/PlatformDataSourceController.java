package com.ouroboros.dev.datasource.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.security.PermitAll;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.dev.datasource.model.PlatformDataSourceModel;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * @author lianshuze
 **/
@RestController
@RequestMapping("/api/platform-datasource")
public class PlatformDataSourceController {

  final private PlatformDataSourceService platformDatasourceService;

  public PlatformDataSourceController(PlatformDataSourceService platformDatasourceService) {
    this.platformDatasourceService = platformDatasourceService;
  }

  @PostMapping()
  public WebApiResult<?> add(@RequestBody PlatformDataSourceModel model) {
    if (ObjectUtils.isEmpty(model.getName())) {
      return WebApiResult.fail("数据源名称不能为空");
    }

    if (ObjectUtils.isEmpty(model.getTitle())) {
      return WebApiResult.fail("数据源标题不能为空");
    }

    if (ObjectUtils.isEmpty(model.getDefinition())) {
      return WebApiResult.fail("数据源定义不能为空");
    }

    return WebApiResult.of(platformDatasourceService.add(model));
  }

  @DeleteMapping("/{id}")
  public WebApiResult<?> delete(@PathVariable String id) {
    return WebApiResult.of(platformDatasourceService.delete(id));
  }

  @PutMapping("/{id}")
  public WebApiResult<?> update(@PathVariable String id, @RequestBody PlatformDataSourceModel model) {
    if (ObjectUtils.isEmpty(model.getName())) {
      return WebApiResult.fail("数据源名称不能为空");
    }

    if (ObjectUtils.isEmpty(model.getTitle())) {
      return WebApiResult.fail("数据源标题不能为空");
    }

    if (ObjectUtils.isEmpty(model.getDefinition())) {
      return WebApiResult.fail("数据源定义不能为空");
    }

    return WebApiResult.of(platformDatasourceService.update(id, model));
  }

  @GetMapping("/{id}")
  public WebApiResult<?> get(@PathVariable String id) {
    return WebApiResult.of(platformDatasourceService.getByPrimaryKey(id));
  }

  @GetMapping("")
  @PermitAll
  public PagedWebApiResult<?> search(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int perPage) {
    Map<String, Object> where = new HashMap<>();
    Long total = platformDatasourceService.count(where);

    Map<String, Object> statement = new HashMap<>();
    statement.put(Keyword.WHERE.toString(), where);
    statement.put(Keyword.LIMIT.toString(), perPage);
    statement.put(Keyword.OFFSET.toString(), (page - 1) * perPage);
    statement.put(Keyword.ORDER.toString(), "createdAt desc");

    List<PlatformDataSourceModel> models = total > 0
        ? platformDatasourceService.query(statement)
        : Collections.emptyList();

    return PagedWebApiResult.ok(total, models);
  }
}
