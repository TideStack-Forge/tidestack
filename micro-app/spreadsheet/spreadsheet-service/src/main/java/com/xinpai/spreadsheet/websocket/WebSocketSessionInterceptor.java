package com.xinpai.spreadsheet.websocket;

import java.util.Map;

import com.xinpai.spreadsheet.dto.AccessTokenPayload;
import com.xinpai.spreadsheet.service.TokenService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/**
 * Socket建立连接（握手）和断开
 * 
 * @author Administrator
 */
@Component
public class WebSocketSessionInterceptor extends HttpSessionHandshakeInterceptor {
    final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private TokenService tokenService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {

            // 取出token并验证
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            String token = servletRequest.getServletRequest().getParameter("token");
            Object payload = tokenService.getPayload(token);
            if (payload == null || !(payload instanceof AccessTokenPayload)) {
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put("token", token);
            attributes.put("spreadsheetId", ((AccessTokenPayload) payload).getSpreadsheetId());
            attributes.put("appId", ((AccessTokenPayload) payload).getAppId());
            attributes.put("editorName", ((AccessTokenPayload) payload).getEditorName());
            attributes.put("readonly", ((AccessTokenPayload) payload).getReadonly());
            return true;
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
            Exception exception) {
        if (exception == null)
            return;
        log.error("Websocket handshake error:{}", exception);
    }
}
