package com.ouroboros.enterprise.runtime.callback;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.callback.EnterpriseCallbackRequest;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.security.annotation.Anonymous;

/** Unified business ingress. OAuth callbacks retain their original security controller. */
@RestController
@RequestMapping("/api/enterprise-callbacks/{connectionId}/callback")
public final class EnterpriseCallbackController {
  private final EnterpriseCallbackDispatcher dispatcher;
  private final int maxBodyBytes;
  private final long timeoutMillis;

  public EnterpriseCallbackController(EnterpriseCallbackDispatcher dispatcher,
      @Value("${ouroboros.enterprise.callbacks.max-body-bytes:1048576}") int maxBodyBytes,
      @Value("${ouroboros.enterprise.callbacks.timeout-ms:2500}") long timeoutMillis) {
    if (maxBodyBytes < 1 || maxBodyBytes == Integer.MAX_VALUE || timeoutMillis < 1) {
      throw new ValidationException("企业回调接收限制必须为正数");
    }
    this.dispatcher = dispatcher;
    this.maxBodyBytes = maxBodyBytes;
    this.timeoutMillis = timeoutMillis;
  }

  @Anonymous
  @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST})
  public ResponseEntity<String> receive(@PathVariable String connectionId, HttpServletRequest request) {
    try {
      return EnterpriseDeadline.afterMillis(timeoutMillis).execute(() -> accept(connectionId, request));
    } catch (java.util.concurrent.TimeoutException ex) {
      return failure(503, "callback acceptance timed out");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return failure(503, "callback acceptance interrupted");
    } catch (ValidationException ex) {
      return failure(401, "invalid callback");
    } catch (Exception ex) {
      org.slf4j.LoggerFactory.getLogger(EnterpriseCallbackController.class).warn(
          "Enterprise callback not acknowledged: connectionId={}, errorType={}", connectionId, ex.getClass().getSimpleName());
      return failure(503, "callback not accepted");
    }
  }

  private ResponseEntity<String> accept(String connectionId, HttpServletRequest request) throws IOException {
    if (request.getContentLengthLong() > maxBodyBytes) { return failure(413, "callback too large"); }
    byte[] body = request.getInputStream().readNBytes(maxBodyBytes + 1);
    if (body.length > maxBodyBytes) { return failure(413, "callback too large"); }
    Map<String, String> headers = new LinkedHashMap<>();
    for (String name : java.util.Collections.list(request.getHeaderNames())) {
      List<String> values = java.util.Collections.list(request.getHeaders(name));
      if (values.size() != 1) { return failure(400, "ambiguous callback header"); }
      headers.put(name, values.getFirst());
    }
    Map<String, String> parameters = new LinkedHashMap<>();
    for (var entry : request.getParameterMap().entrySet()) {
      if (entry.getValue().length != 1) { return failure(400, "ambiguous callback parameter"); }
      parameters.put(entry.getKey(), entry.getValue()[0]);
    }
    var input = new EnterpriseCallbackRequest(request.getMethod(), headers, parameters,
        new String(body, java.nio.charset.StandardCharsets.UTF_8));
    var response = dispatcher.receive(connectionId, input);
    return ResponseEntity.ok().header("Cache-Control", "no-store").contentType(
        org.springframework.http.MediaType.parseMediaType(response.contentType())).body(response.body());
  }

  private static ResponseEntity<String> failure(int status, String message) {
    return ResponseEntity.status(status).header("Cache-Control", "no-store").body(message);
  }
}
