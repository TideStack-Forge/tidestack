package com.ouroboros.dev.runtime.kubernetes.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.mockwebserver.Context;
import io.fabric8.mockwebserver.MockWebServer;
import io.fabric8.mockwebserver.ServerRequest;
import io.fabric8.mockwebserver.ServerResponse;
import io.fabric8.mockwebserver.dsl.MockServerExpectation;
import io.fabric8.mockwebserver.http.RecordedRequest;

public final class KubernetesMockServerSupport implements AutoCloseable {
  private final KubernetesMockServer server;
  private NamespacedKubernetesClient client;

  private KubernetesMockServerSupport(KubernetesMockServer server) {
    this.server = server;
  }

  public static KubernetesMockServerSupport crudHttps() {
    Map<ServerRequest, Queue<ServerResponse>> responses = new HashMap<ServerRequest, Queue<ServerResponse>>();
    KubernetesMockServer server = new KubernetesMockServer(
        new Context(Serialization.jsonMapper()),
        new MockWebServer(),
        responses,
        new KubernetesMixedDispatcher(responses),
        true
    );
    return new KubernetesMockServerSupport(server);
  }

  public void start() {
    server.init();
    client = server.createClient();
  }

  public NamespacedKubernetesClient getClient() {
    return client;
  }

  public MockServerExpectation expect() {
    return server.expect();
  }

  public RecordedRequest takeRequest(long timeout, TimeUnit unit) throws InterruptedException {
    return server.takeRequest(timeout, unit);
  }

  @Override
  public void close() {
    try {
      server.destroy();
    } finally {
      if (client != null) {
        client.close();
      }
    }
  }
}
