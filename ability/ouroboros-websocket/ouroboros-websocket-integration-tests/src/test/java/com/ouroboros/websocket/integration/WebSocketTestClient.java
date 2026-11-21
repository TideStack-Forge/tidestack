package com.ouroboros.websocket.integration;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

public class WebSocketTestClient {

  private final String url;
  private final WebSocketStompClient stompClient;
  private final BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();

  private StompSession session;

  public WebSocketTestClient(String url) {
    this.url = url;
    List<Transport> transports = new ArrayList<>();
    transports.add(new WebSocketTransport(new StandardWebSocketClient()));
    SockJsClient sockJsClient = new SockJsClient(transports);

    this.stompClient = new WebSocketStompClient(sockJsClient);
    this.stompClient.setMessageConverter(new StringMessageConverter());
  }

  public void connect() throws Exception {
    connect(null);
  }

  public void connect(String token) throws Exception {
    var headers = new StompHeaders();
    if (token != null) headers.add("token", token);
    this.session = stompClient.connectAsync(url, new org.springframework.web.socket.WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {
    }).get(10, TimeUnit.SECONDS);
  }

  public void subscribe(String destination) {
    session.subscribe(destination, new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return String.class;
      }

      @Override
      public void handleFrame(StompHeaders headers, Object payload) {
        receivedMessages.offer(payload == null ? null : payload.toString());
      }
    });
  }

  public void send(String destination, Object payload) {
    session.send(destination, payload);
  }

  public String waitForMessage(long timeoutSeconds) throws InterruptedException {
    return receivedMessages.poll(timeoutSeconds, TimeUnit.SECONDS);
  }

  public void disconnect() {
    if (session != null && session.isConnected()) {
      session.disconnect();
    }
    stompClient.stop();
  }
}
