package com.ouroboros.data.dict.controller;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.data.dict.DataDictItem;
import com.ouroboros.data.dict.DataDict;
import com.ouroboros.data.dict.pojo.TreeDataDictItem;
import com.ouroboros.data.dict.service.DataDictCoreService;
import com.ouroboros.data.dict.service.DataDictItemService;
import com.ouroboros.data.dict.service.model.DataDictItemModel;
import com.ouroboros.data.dict.util.DataDictItemStructureConvertUtils;
import com.ouroboros.util.Trees;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.SelectOption;

/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/sys/data-dict")
public class DictController {

  @Autowired
  private DataDictCoreService dataDictCoreService;

  @Autowired
  private DataDictItemService dataDictItemService;

  @PermitAll
  @Operation(
      summary = "列出数据字典",
      description = "返回当前系统可用的数据字典列表。",
      tags = {"dict"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回数据字典列表")
  })
  @GetMapping
  public ItemsWebApiResult<?> list() {
    return ItemsWebApiResult.ok(
        dataDictCoreService.searchAll().stream()
            .map(dict -> new SelectOption(dict.getName(), dict.getTitle()))
            .collect(Collectors.toList())
    );
  }

  @PermitAll
  @Operation(
      summary = "查询数据字典详情",
      description = "根据字典名称获取树形或平铺模式的数据字典项列表。",
      tags = {"dict"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回数据字典项列表"),
      @ApiResponse(responseCode = "404", description = "指定数据字典不存在")
  })
  @GetMapping("/{name}")
  public ItemsWebApiResult<? extends DataDictItem> detail(
      @Parameter(description = "数据字典名称", required = true)
      @PathVariable(name = "name") String name,
      @Parameter(description = "树形查询的单个根节点编码")
      @RequestParam(name = "root", required = false) String root,
      @Parameter(description = "树形查询的多个根节点编码，逗号分隔")
      @RequestParam(name = "roots", required = false) String roots,
      @Parameter(description = "字典项关键字")
      @RequestParam(name = "keyword", required = false) String keyword,
      @Parameter(description = "字典项编码列表，逗号分隔")
      @RequestParam(name = "code", required = false) String code) {
    Optional<? extends DataDict> dictOptional = dataDictCoreService.getByName(name);
    if (!dictOptional.isPresent()) {
      return ItemsWebApiResult.ok(Collections.emptyList());
    }

    List<DataDictItemModel> models = dataDictItemService.searchListByDict(name);
    if (dictOptional.get().isTreeMode()) {
      List<DataDictItemModel> rootNodes = new ArrayList<>();
      if (ObjectUtils.isNotEmpty(root)) {
        models.stream().filter(item -> Objects.equals(root, item.getParent())).forEach(rootNodes::add);
      } else if (ObjectUtils.isNotEmpty(roots)) {
        List<String> rootList = Arrays.asList(roots.split(","));
        models.stream().filter(item -> rootList.contains(item.getCode())).forEach(rootNodes::add);
      } else {
        models.stream().filter(i -> ObjectUtils.isEmpty(i.getParent())).forEach(rootNodes::add);
      }

      return ItemsWebApiResult.ok(
          Trees.buildTree(
              rootNodes,
              (node) ->
                  models.stream()
                      .filter(m -> Objects.equals(node.getCode(), m.getParent()))
                      .collect(Collectors.toList()),
              (node, children) -> {
                return new TreeDataDictItem(
                    DataDictItemStructureConvertUtils.toTreeItem(node),
                    node.getParent(),
                    children.stream().map(it -> {
                      TreeDataDictItem item = new TreeDataDictItem(it);
                      item.setParent(node.getCode());
                      if (it instanceof TreeDataDictItem treeItem) {
                        item.setChildren(treeItem.getChildren());
                      }

                      return item;
                    }).collect(Collectors.toList())
                );
              }
          )
      );
    }

    List<String> codes = ObjectUtils.isNotEmpty(code) ? Arrays.asList(code.split(",")) : Collections.emptyList();
    return ItemsWebApiResult.ok(
        models.stream()
            .filter(item -> ObjectUtils.isEmpty(codes) || codes.contains(item.getCode()))
            .filter(item -> ObjectUtils.isEmpty(keyword) || item.getLabel().contains(keyword))
            .map(DataDictItemStructureConvertUtils::toFlatItem)
            .collect(Collectors.toList())
    );
  }
}
