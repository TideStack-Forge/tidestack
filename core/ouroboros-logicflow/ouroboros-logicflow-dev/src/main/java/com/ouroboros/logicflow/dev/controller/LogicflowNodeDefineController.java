package com.ouroboros.logicflow.dev.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.logicflow.authoring.model.NodeDefine;
import com.ouroboros.logicflow.authoring.provider.LogicFlowNodeDefineProvider;
import com.ouroboros.util.SPI;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/logicflow/node-define")
public class LogicflowNodeDefineController {
  @GetMapping("")
  public WebApiResult<List<NodeDefine>> getNodeDefines() {
    return WebApiResult.ok(mergeNodeDefines(SPI.getCachedSortedServiceStream(LogicFlowNodeDefineProvider.class)));
  }

  static List<NodeDefine> mergeNodeDefines(Stream<LogicFlowNodeDefineProvider> providers) {
    return com.ouroboros.logicflow.authoring.provider.LogicFlowNodeCatalog.merge(providers);
  }
}
