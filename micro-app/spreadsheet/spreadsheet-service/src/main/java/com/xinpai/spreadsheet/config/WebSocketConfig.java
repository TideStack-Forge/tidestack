package com.xinpai.spreadsheet.config;

import com.xinpai.spreadsheet.utils.IpAndPortUtil;
import com.xinpai.spreadsheet.websocket.SpreadsheetWebSocketHandler;
import com.xinpai.spreadsheet.websocket.WebSocketSessionInterceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * @author Administrator
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private SpreadsheetWebSocketHandler spreadsheetWebSocketHandler;

    @Autowired
    private WebSocketSessionInterceptor webSocketSessionInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 1.注册WebSocket
        // 设置websocket的地址
        String websocket_url = "/websocket";
        // 注册Handler
        registry.addHandler(spreadsheetWebSocketHandler, websocket_url).
        // 注册Interceptor
                addInterceptors(webSocketSessionInterceptor)
                // 配置*代表允许所有的ip进行调用
                .setAllowedOrigins("*");

        // 2.注册SockJS，提供SockJS支持(主要是兼容ie8)
        // 设置sockjs的地址
        String sockjs_url = "/sockjs";
        // 注册Handler
        registry.addHandler(spreadsheetWebSocketHandler, sockjs_url).
        // 注册Interceptor
                addInterceptors(webSocketSessionInterceptor)
                // 配置*代表允许所有的ip进行调用
                .setAllowedOrigins("*").withSockJS();

        // 获取系统ip:port
        SpreadsheetWebSocketHandler.ipAndPort = IpAndPortUtil.getIpAddressAndPort();

    }
}
