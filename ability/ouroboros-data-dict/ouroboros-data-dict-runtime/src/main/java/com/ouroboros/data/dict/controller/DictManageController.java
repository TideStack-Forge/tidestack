package com.ouroboros.data.dict.controller;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.data.dict.DataDict;
import com.ouroboros.data.dict.DataDictItem;
import com.ouroboros.data.dict.DataDictService;
import com.ouroboros.data.dict.service.DataDictCoreService;
import com.ouroboros.data.dict.service.DataDictItemService;
import com.ouroboros.data.dict.service.model.DataDictItemModel;
import com.ouroboros.data.dict.util.CollectionCalculateUtils;
import com.ouroboros.data.dict.util.DataDictItemStructureConvertUtils;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * @author sunquan
 */
@RestController
@RequestMapping("/api/sys/data-dict-manage")
public class DictManageController {

  @Autowired
  private DictManageController self;

  @Autowired
  private DataDictService dataDictService;

  @Autowired
  private DataDictCoreService dataDictCoreService;

  @Autowired
  private DataDictItemService dataDictItemService;

  @Operation(summary = "创建数据字典", description = "新增一个数据字典定义。", tags = {"dict"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "数据字典创建成功"),
      @ApiResponse(responseCode = "400", description = "数据字典参数不合法")
  })
  @PostMapping
  public WebApiResult<DataDict> add(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "数据字典定义", required = true)
      @RequestBody DataDict dict) throws Exception {
    return WebApiResult.ok(dataDictService.add(dict));
  }

  @Operation(summary = "获取数据字典详情", description = "根据数据字典名称查询定义详情。", tags = {"dict"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回数据字典详情"),
      @ApiResponse(responseCode = "404", description = "数据字典不存在")
  })
  @GetMapping("/{name}")
  public WebApiResult<DataDict> detail(
      @Parameter(description = "数据字典名称", required = true)
      @PathVariable(name = "name") String name) {
    return WebApiResult.of(404, "数据字典不存在", dataDictService.detail(name));
  }

  @Operation(summary = "分页查询数据字典", description = "按标题关键字分页查询数据字典。", tags = {"dict"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回数据字典分页结果")
  })
  @GetMapping
  public PagedWebApiResult<? extends DataDict> search(
      @Parameter(description = "字典标题关键字")
      @RequestParam(name = "title", required = false) String title,
      @Parameter(description = "页码")
      @RequestParam(required = false, defaultValue = "1") Integer page,
      @Parameter(description = "每页条数")
      @RequestParam(required = false, defaultValue = "20") Integer perPage) {
    Map<String, Object> where = new HashMap<>();
    if (ObjectUtils.isNotEmpty(title)) {
      where.put("title", Collections.singletonMap("like", title));
    }

    List<? extends DataDict> models = dataDictCoreService.searchAll();
    return PagedWebApiResult.ok(
        models.size(),
        models.stream()
            .filter(model -> StringUtils.isEmpty(title) || model.getTitle().contains(title))
            .skip((long) (page - 1) * perPage)
            .limit(perPage)
            .collect(Collectors.toList())
    );
  }

  @Operation(summary = "新增字典项", description = "为指定数据字典新增单个字典项。", tags = {"dict"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "字典项新增成功"),
      @ApiResponse(responseCode = "404", description = "数据字典不存在")
  })
  @PostMapping("/{name}/item")
  public WebApiResult<DataDictItem> addItem(
      @Parameter(description = "数据字典名称", required = true)
      @PathVariable(name = "name") String name,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "字典项定义", required = true)
      @RequestBody DataDictItem item) throws Exception {
    return WebApiResult.ok(dataDictService.addItem(name, item));
  }

  @Operation(summary = "批量新增字典项", description = "为指定数据字典批量新增字典项。", tags = {"dict"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "字典项批量新增成功"),
      @ApiResponse(responseCode = "404", description = "数据字典不存在")
  })
  @PostMapping("/{name}/items")
  public ItemsWebApiResult<? extends DataDictItem> addItems(
      @Parameter(description = "数据字典名称", required = true)
      @PathVariable(name = "name") String name,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "字典项列表", required = true)
      @RequestBody List<DataDictItem> items) throws Exception {
    return ItemsWebApiResult.ok(dataDictService.addItems(name, items));
  }

  @Operation(summary = "删除字典项", description = "删除指定数据字典下的单个字典项。", tags = {"dict"})
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "字典项删除成功")
  })
  @DeleteMapping("/{name}/item/{code}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteItem(
      @Parameter(description = "数据字典名称", required = true)
      @PathVariable(name = "name") String name,
      @Parameter(description = "字典项编码", required = true)
      @PathVariable(name = "code") String code) throws Exception {
    dataDictService.deleteItem(name, code);
  }

  @Operation(summary = "更新字典项", description = "更新指定数据字典下的单个字典项。", tags = {"dict"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "字典项更新成功"),
      @ApiResponse(responseCode = "404", description = "字典项不存在")
  })
  @PutMapping("/{name}/item/{code}")
  public WebApiResult<DataDictItem> updateItem(
      @Parameter(description = "数据字典名称", required = true)
      @PathVariable(name = "name") String name,
      @Parameter(description = "字典项编码", required = true)
      @PathVariable(name = "code") String code,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "更新后的字典项定义", required = true)
      @RequestBody DataDictItem item) throws Exception {
    var result = dataDictService.updateItem(name, code, item);
    if (!result) {
      throw new BusinessException(404, "字典项不存在");
    }
    return WebApiResult.ok(item);
  }

  @Operation(summary = "批量更新字典项", description = "用新的字典项列表整体同步指定数据字典的条目。", tags = {"dict"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "字典项批量更新成功"),
      @ApiResponse(responseCode = "404", description = "数据字典不存在")
  })
  @PutMapping("/{name}/items")
  public ItemsWebApiResult<? extends DataDictItem> updateItems(
      @Parameter(description = "数据字典名称", required = true)
      @PathVariable(name = "name") String name,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "新的字典项列表", required = true)
      @RequestBody List<DataDictItem> newItems) throws Exception {
    List<DataDictItemModel> oldModels = dataDictItemService.searchListByDict(name);
    List<DataDictItemModel> newModels = DataDictItemStructureConvertUtils.disassembleTree(newItems).stream()
        .peek(m -> m.setDict(name))
        .collect(Collectors.toList());

    BiFunction<DataDictItemModel, DataDictItemModel, Boolean> primaryEquals = (a, b) -> {
      // 查询时带着dictName、tenantId，所以这里不需要判断
      return Objects.equals(a.getCode(), b.getCode());
    };

    BiFunction<DataDictItemModel, DataDictItemModel, Boolean> dataEquals = (a, b) -> {
      if (!Objects.equals(a.getParent(), b.getParent())) {
        return false;
      }

      if (!Objects.equals(a.getLabel(), b.getLabel())) {
        return false;
      }

      if (!Objects.equals(a.getOrder(), b.getOrder())) {
        return false;
      }

      if (!Objects.equals(Json.toJsonString(a.getExtraProps()), Json.toJsonString(b.getExtraProps()))) {
        return false;
      }

      return true;
    };

    List<DataDictItemModel> addItems = CollectionCalculateUtils.findNewCollection(oldModels, newModels, primaryEquals);
    List<DataDictItemModel> removeItems = CollectionCalculateUtils.findRemoveCollection(oldModels, newModels, primaryEquals);
    List<DataDictItemModel> updateItems =
        CollectionCalculateUtils.findUpdateCollection(oldModels, newModels, primaryEquals, dataEquals);

    self.doUpdateItems(name, addItems, removeItems, updateItems);

    return ItemsWebApiResult.ok(dataDictService.searchListItemByDictName(name));
  }

  @Operation(summary = "列出数据字典全部字典项", description = "查询指定数据字典的全部字典项。", tags = {"dict"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回字典项列表")
  })
  @GetMapping("/{name}/items")
  public ItemsWebApiResult<? extends DataDictItem> searchListItem(
      @Parameter(description = "数据字典名称", required = true)
      @PathVariable(name = "name") String name) {
    return ItemsWebApiResult.ok(dataDictService.searchListItemByDictName(name));
  }

  @Transactional(rollbackFor = Exception.class)
  public void doUpdateItems(String dictName,
                            List<DataDictItemModel> addItems,
                            List<DataDictItemModel> removeItems,
                            List<DataDictItemModel> updateItems) throws Exception {
    if (ObjectUtils.isNotEmpty(addItems)) {
      try {
        boolean treeMode = dataDictCoreService.getByName(dictName).map(DataDict::isTreeMode).orElse(false);
        List<? extends DataDictItem> domainItems = addItems.stream()
            .map(model -> treeMode
                ? DataDictItemStructureConvertUtils.toTreeItem(model)
                : DataDictItemStructureConvertUtils.toFlatItem(model))
            .collect(Collectors.toList());
        dataDictService.addItems(dictName, domainItems);
      } catch (Exception e) {
        String addCodes = addItems.stream().map(DataDictItemModel::getCode).collect(Collectors.joining(","));
        throw new BusinessException(String.format("新增%s失败", addCodes), e);
      }
    }

    if (ObjectUtils.isNotEmpty(removeItems)) {
      try {
        List<String> removeCodes = removeItems.stream().map(DataDictItemModel::getCode).collect(Collectors.toList());
        dataDictService.deleteItems(dictName, removeCodes);
      } catch (Exception e) {
        List<String> removeCodes = removeItems.stream().map(DataDictItemModel::getCode).collect(Collectors.toList());
        throw new BusinessException(String.format("删除%s失败", String.join(",", removeCodes)), e);
      }
    }

    if (ObjectUtils.isNotEmpty(updateItems)) {
      for (DataDictItemModel item : updateItems) {
        try {
          String code = item.getCode();
          boolean treeMode = dataDictCoreService.getByName(dictName).map(DataDict::isTreeMode).orElse(false);
          dataDictService.updateItem(
              dictName,
              code,
              treeMode
                  ? DataDictItemStructureConvertUtils.toTreeItem(item)
                  : DataDictItemStructureConvertUtils.toFlatItem(item)
          );
        } catch (Exception e) {
          throw new BusinessException(String.format("更新%s失败", item.getCode()), e);
        }
      }
    }
  }
}
