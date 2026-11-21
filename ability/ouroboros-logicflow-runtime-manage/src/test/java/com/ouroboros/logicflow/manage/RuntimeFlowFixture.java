package com.ouroboros.logicflow.manage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;
import com.ouroboros.lifecycle.event.*;
import com.ouroboros.logicflow.AppLogicModelMetaProvider;
import com.ouroboros.logicflow.DefaultLogicFlowProvider;
import com.ouroboros.logicflow.manage.provider.DatabaseFlowModelMetaProvider;
import com.ouroboros.logicflow.manage.repository.DataRuntimeFlowStore;
import com.ouroboros.logicflow.manage.runtime.RuntimeFlowCoordinator;
import com.ouroboros.logicflow.manage.service.RuntimeFlowService;
import com.ouroboros.logicflow.model.*;

final class RuntimeFlowFixture implements AutoCloseable {
  final RuntimeFlowDatabaseFixture database;
  final DataRuntimeFlowStore store;
  final DatabaseFlowModelMetaProvider source = new DatabaseFlowModelMetaProvider();
  final DefaultLogicFlowProvider runtime = new DefaultLogicFlowProvider();
  final RuntimeFlowCoordinator coordinator;
  final RuntimeFlowService service;
  final AppLogicModelMetaProvider app;
  final RuntimeFlowObservationFixture observation;

  RuntimeFlowFixture(Path appHome) throws IOException { this(appHome, new RuntimeFlowDatabaseFixture()); }

  RuntimeFlowFixture(Path appHome, RuntimeFlowDatabaseFixture database) throws IOException {
    this.database = database;
    store = database.store();
    source.accept(store.initialize());
    var directory = appHome.resolve("logic-flow");
    Files.createDirectories(directory);
    if (Files.notExists(directory.resolve("child.logicflow.json"))) {
      Files.writeString(directory.resolve("child.logicflow.json"), FlowModelDefinitions.write(constant("child", "default")));
      Files.writeString(directory.resolve("parent.logicflow.json"), FlowModelDefinitions.write(parent("parent", "child")));
      Files.writeString(directory.resolve("other.logicflow.json"), FlowModelDefinitions.write(constant("other", "other-default")));
    }
    app = new AppLogicModelMetaProvider(() -> appHome.toString());
    coordinator = new RuntimeFlowCoordinator(store, source, runtime,
        new DefaultListableBeanFactory().getBeanProvider(ConfigurationService.class));
    service = new RuntimeFlowService(store, runtime, coordinator);
    refresh();
    observation = new RuntimeFlowObservationFixture(database, coordinator);
  }

  void refresh() {
    OuroborosContextRefreshLifecycle.publish(this, event -> {
      if (event instanceof OuroborosMetadataRefreshingEvent value) { runtime.onMetadataRefreshing(value); }
      if (event instanceof OuroborosModelRefreshingEvent value) { source.accept(store.read()); runtime.onModelRefreshing(value); }
      if (event instanceof OuroborosModelRefreshedEvent value) { runtime.onModelRefreshed(value); }
    });
  }

  static FlowModelMeta constant(String name, String value) {
    return flow(name, FlowNodeMeta.builder().name("source").type("const").extraProp("value", value).build());
  }
  static FlowModelMeta parent(String name, String child) {
    return flow(name, FlowNodeMeta.builder().name("source").type("callFlow").inputPort("input").extraProp("flowName", child).build());
  }
  private static FlowModelMeta flow(String name, FlowNodeMeta source) {
    return FlowModelMeta.builder().name(name).description("运行时管理验收示例").entryNode("source").exitNode("result")
        .nodes(List.of(source, FlowNodeMeta.builder().name("result").type("return").inputPort("input").build()))
        .connections(List.of(FlowConnectionMeta.builder().sourceNode("source").sourceOutputPort(0)
            .targetNode("result").targetInputPort(0).build())).build();
  }
  @Override public void close() { observation.close(); coordinator.close(); }
}
