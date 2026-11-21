package com.ouroboros.dev.app.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Enumeration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.transport.HttpTunnelTransport;

@RestController
@RequestMapping("/api/tunnel")
public class RuntimeHttpTunnelProxyController {
  private final HttpTunnelTransport httpTunnelTransport;

  public RuntimeHttpTunnelProxyController(HttpTunnelTransport httpTunnelTransport) {
    this.httpTunnelTransport = httpTunnelTransport;
  }

  @RequestMapping("/{sessionId}/**")
  public void proxyRequest(
      @PathVariable("sessionId") String sessionId,
      HttpServletRequest request,
      HttpServletResponse response
  ) throws IOException {
    Try<HttpTunnelTransport.HttpTunnelSession> sessionResult = httpTunnelTransport.getSession(sessionId);
    if (sessionResult.isFailure()) {
      response.sendError(HttpStatus.NOT_FOUND.value(), "Tunnel session not found");
      return;
    }
    HttpTunnelTransport.HttpTunnelSession tunnelSession = sessionResult.get();
    Try<Socket> socketResult = tunnelSession.getConnector().connect(tunnelSession.getSession());
    if (socketResult.isFailure()) {
      response.sendError(HttpStatus.BAD_GATEWAY.value(), "Failed to connect to target: " + socketResult.getCause().getMessage());
      return;
    }
    try (Socket targetSocket = socketResult.get()) {
      forwardRequest(request, response, targetSocket);
    } catch (IOException error) {
      if (!response.isCommitted()) {
        response.sendError(HttpStatus.BAD_GATEWAY.value(), "Proxy error: " + error.getMessage());
      }
    }
  }

  private void forwardRequest(HttpServletRequest request, HttpServletResponse response, Socket targetSocket) throws IOException {
    String requestPath = extractRequestPath(request);
    String requestLine = request.getMethod() + " " + requestPath + " HTTP/1.1\r\n";
    OutputStream targetOut = targetSocket.getOutputStream();
    targetOut.write(requestLine.getBytes("UTF-8"));
    Enumeration<String> headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      if ("host".equalsIgnoreCase(headerName)) {
        continue;
      }
      String headerValue = request.getHeader(headerName);
      targetOut.write((headerName + ": " + headerValue + "\r\n").getBytes("UTF-8"));
    }
    targetOut.write(("Host: localhost:" + targetSocket.getPort() + "\r\n").getBytes("UTF-8"));
    targetOut.write("Connection: close\r\n".getBytes("UTF-8"));
    targetOut.write("\r\n".getBytes("UTF-8"));
    if (request.getContentLength() > 0) {
      copyStream(request.getInputStream(), targetOut, request.getContentLength());
    }
    targetOut.flush();
    InputStream targetIn = targetSocket.getInputStream();
    String statusLine = readLine(targetIn);
    if (statusLine == null || statusLine.isEmpty()) {
      throw new IOException("Empty response from target");
    }
    String[] statusParts = statusLine.split(" ", 3);
    if (statusParts.length < 2) {
      throw new IOException("Invalid status line: " + statusLine);
    }
    int statusCode = Integer.parseInt(statusParts[1]);
    response.setStatus(statusCode);
    String headerLine;
    while ((headerLine = readLine(targetIn)) != null && !headerLine.isEmpty()) {
      int colonIndex = headerLine.indexOf(':');
      if (colonIndex > 0) {
        String headerName = headerLine.substring(0, colonIndex).trim();
        String headerValue = headerLine.substring(colonIndex + 1).trim();
        if ("transfer-encoding".equalsIgnoreCase(headerName) || "connection".equalsIgnoreCase(headerName)) {
          continue;
        }
        response.addHeader(headerName, headerValue);
      }
    }
    copyStream(targetIn, response.getOutputStream(), -1);
    response.getOutputStream().flush();
  }

  private String extractRequestPath(HttpServletRequest request) {
    String requestUri = request.getRequestURI();
    String contextPath = request.getContextPath();
    String pathAfterContext = requestUri.substring(contextPath.length());
    int tunnelPrefixEnd = pathAfterContext.indexOf('/', "/api/tunnel/".length());
    if (tunnelPrefixEnd < 0) {
      return "/";
    }
    String pathAfterSessionId = pathAfterContext.substring(tunnelPrefixEnd);
    String queryString = request.getQueryString();
    if (queryString != null && !queryString.isEmpty()) {
      return pathAfterSessionId + "?" + queryString;
    }
    return pathAfterSessionId;
  }

  private String readLine(InputStream input) throws IOException {
    StringBuilder line = new StringBuilder();
    int ch;
    while ((ch = input.read()) >= 0) {
      if (ch == '\r') {
        int next = input.read();
        if (next == '\n') {
          break;
        }
        line.append((char) ch);
        if (next >= 0) {
          line.append((char) next);
        }
      } else if (ch == '\n') {
        break;
      } else {
        line.append((char) ch);
      }
    }
    return line.toString();
  }

  private void copyStream(InputStream input, OutputStream output, int contentLength) throws IOException {
    byte[] buffer = new byte[8192];
    int totalRead = 0;
    int read;
    if (contentLength > 0) {
      while (totalRead < contentLength && (read = input.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead))) >= 0) {
        output.write(buffer, 0, read);
        totalRead += read;
      }
    } else {
      while ((read = input.read(buffer)) >= 0) {
        output.write(buffer, 0, read);
      }
    }
  }
}
