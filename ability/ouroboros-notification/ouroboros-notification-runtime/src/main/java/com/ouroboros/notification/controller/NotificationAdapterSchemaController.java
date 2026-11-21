package com.ouroboros.notification.controller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.ouroboros.notification.service.NotificationTemplateRuntimeManagementService;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.notification.adapter.NotificationAdapterCenter;
import com.ouroboros.notification.util.Classes;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/notification/adapter-schema")
public class NotificationAdapterSchemaController {
  @Autowired(required = false)
  private List<com.ouroboros.notification.adapter.NotificationAdapterSupplier> managedSuppliers = List.of();
  @Autowired
  private NotificationTemplateRuntimeManagementService management;

  @ModelAttribute
  public void preventCaching(jakarta.servlet.http.HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
  }

  @Operation(summary = "获取通知渠道管理描述", description = "沿原适配器 SPI 返回类型投影、配置分组及有效模板引用数，不返回连接参数值。", tags = {"notification"})
  @GetMapping("/management")
  public WebApiResult<List<Map<String, Object>>> getManagement() {
    var suppliers = NotificationAdapterCenter.getAllSuppliers(managedSuppliers);
    var references = new TreeMap<String, Long>();
    management.getRuntimeDefinitions().forEach(template -> {
      if (template.effectiveTemplate().get("channel") instanceof Map<?, ?> channels) {
        channels.keySet().forEach(key -> references.merge(String.valueOf(key), 1L, Long::sum));
      }
    });
    var names = new java.util.TreeSet<>(suppliers.keySet()); names.addAll(references.keySet());
    var channels = names.stream().map(name -> {
      var supplier = suppliers.get(name);
      var adapter = supplier == null ? null : supplier.getAdapter();
      var description = supplier == null ? java.util.Optional.<com.ouroboros.notification.adapter.NotificationChannelManagement>empty() : supplier.getManagement();
      var channel = new LinkedHashMap<String, Object>();
      channel.put("id", name); channel.put("title", description.map(item -> item.title()).orElse(name));
      channel.put("installed", adapter != null); channel.put("manageable", adapter != null && description.isPresent());
      channel.put("referenceCount", references.getOrDefault(name, 0L));
      channel.put("configurationGroups", description.map(item -> item.configurationGroups()).orElse(List.of()));
      if (adapter != null && description.isPresent()) {
        channel.put("schema", Classes.toManagementSchema(adapter.getChannelSchemaClass(), description.get().schemaFields()));
        channel.put("options", Classes.toManagementSchema(adapter.getChannelOptionsClass(), description.get().optionsFields()));
      }
      return (Map<String, Object>) channel;
    }).toList();
    return WebApiResult.ok(channels);
  }


  @Operation(
      summary = "获取通知适配器配置 Schema",
      description = "返回各通知适配器的渠道配置 Schema 和选项 Schema。",
      tags = {"notification"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回通知适配器 Schema")
  })
  @GetMapping
  public WebApiResult<?> getAdapterSchema() {
    var adapterMap = NotificationAdapterCenter.getAllAdapter(managedSuppliers);
    Map<String, Map<String, Object>> adapterSchemaMap = adapterMap.keySet().stream()
        .map(adapter -> Tuple.of(adapter, adapterMap.get(adapter)))
        .map(tuple -> {
          Map<String, Object> map = new HashMap<>();

          map.put("schema", Classes.toMapDefine(tuple._2().getChannelSchemaClass()));
          map.put("options", Classes.toMapDefine(tuple._2().getChannelOptionsClass()));

          return Tuple.of(tuple._1(), map);
        })
        .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2, (a, b) -> a));

    return WebApiResult.ok(adapterSchemaMap);
  }
}
