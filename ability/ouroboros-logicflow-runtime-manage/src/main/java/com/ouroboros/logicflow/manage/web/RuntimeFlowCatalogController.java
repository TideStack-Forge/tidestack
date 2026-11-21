package com.ouroboros.logicflow.manage.web;

import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;

import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;
import com.ouroboros.logicflow.authoring.model.NodeDefine;
import com.ouroboros.logicflow.authoring.provider.LogicFlowNodeCatalog;
import com.ouroboros.logicflow.authoring.provider.LogicFlowNodeDefineProvider;
import com.ouroboros.logicflow.model.FlowModelCenter;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeFactory;
import com.ouroboros.logicflow.support.LogicFlowServices;
import com.ouroboros.mq.binding.MQBindingProvider;
import com.ouroboros.mq.binding.MQConsumerProperties;
import com.ouroboros.notification.template.NotificationTemplateProvider;
import com.ouroboros.rpc.RPCModelDefinitionCenter;
import com.ouroboros.util.SPI;
import com.ouroboros.web.WebApiResult;

/** Catalogs contain identifiers and labels, never configuration values, credentials or executable objects. */
@RestController
@RequestMapping("/api/runtime/logicflows/catalog")
public class RuntimeFlowCatalogController {
  private final RuntimeFlowAccess access;
  public RuntimeFlowCatalogController(RuntimeFlowAccess access) { this.access = access; }

  @ModelAttribute public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

  @Operation(operationId = "runtimeLogicFlowNodeDefinitions", summary = "读取当前运行环境支持的节点编排定义", description = "需要 systemSetting.logicflow.read；按正式 authoring 合并，并用当前 NodeFactory 能力筛选。")
  @GetMapping("/node-definitions")
  public WebApiResult<List<NodeDefine>> nodes() {
    access.require(RuntimeFlowAccess.READ);
    return WebApiResult.ok(OuroborosContextRefreshLifecycle.withStableContext(() ->
        LogicFlowNodeCatalog.merge(LogicFlowServices.cachedSortedServiceStream(LogicFlowNodeDefineProvider.class))
            .stream().filter(node -> NodeFactory.supports(FlowNodeMeta.builder().name("catalog").type(node.getType()).build())).toList()));
  }

  @Operation(operationId = "runtimeLogicFlowReferences", summary = "查询运行环境引用目录", description = "需要 read；kind 支持 flow、data-model、configuration、rpc-model、notification-template、mq-binding，最多返回 500 项。MQ bindingType 为 producer 或 consumer。不返回配置值或凭据。")
  @GetMapping("/references/{kind}")
  public WebApiResult<List<Option>> references(@PathVariable String kind,
      @RequestParam(defaultValue = "") String term, @RequestParam(defaultValue = "producer") String bindingType) {
    access.require(RuntimeFlowAccess.READ);
    return WebApiResult.ok(OuroborosContextRefreshLifecycle.withStableContext(() -> {
      var values = new TreeMap<String, Option>(String::compareToIgnoreCase);
      Consumer<Option> add = option -> values.putIfAbsent(option.value(), option);
      switch (kind) {
        case "flow" -> FlowModelCenter.getFlowModelMap().values().forEach(model -> add.accept(option(model.getName(), model.getDescription())));
        case "data-model" -> DataModelCenter.getDataModelMap().keySet().forEach(name -> add.accept(option(name, name)));
        case "configuration" -> SPI.getCachedSortedServiceStream(ConfigurationMetadataProvider.class)
            .flatMap(provider -> provider.getAllConfigurationMetadata().stream())
            .forEach(meta -> add.accept(option(meta.getKey(), meta.getLabel())));
        case "rpc-model" -> RPCModelDefinitionCenter.getAllDefinitions().keySet().forEach(name -> add.accept(option(name, name)));
        case "notification-template" -> SPI.getCachedSortedServiceStream(NotificationTemplateProvider.class)
            .flatMap(provider -> provider.getAllTemplate().stream()).filter(template -> template.isEnabled())
            .forEach(template -> add.accept(option(template.getName(), template.getTitle())));
        case "mq-binding" -> {
          if (!List.of("producer", "consumer").contains(bindingType)) { throw new BusinessException(400, "不支持的消息绑定类型"); }
          SPI.getCachedSortedServiceStream(MQBindingProvider.class).flatMap(provider -> provider.getAllBindings().stream())
              .filter(binding -> (binding instanceof MQConsumerProperties) == bindingType.equals("consumer"))
              .forEach(binding -> add.accept(new Option(binding.getBindingName(), binding.getBindingName(), binding.getDestination(), binding.getDescription())));
        }
        default -> throw new BusinessException(400, "不支持的运行态引用目录");
      }
      return values.values().stream().filter(option -> option.value().contains(term) || option.label().contains(term)).limit(500).toList();
    }));
  }

  private static Option option(String value, String label) { return new Option(value, label == null ? value : label, null, null); }
  public record Option(String value, String label, String destination, String description) { }
}
