package com.ouroboros.businessmodel.event.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.web.WebApiResult;

@RestController
@ConditionalOnBean(BusinessEventOperations.class)
public class BusinessEventOperationsController {
  private final BusinessEventOperations operations;

  public record DiagnosticsResponse(BusinessEventOperations.Fact fact,
                                    java.util.List<BusinessEventOperations.Delivery> deliveries, boolean canRedrive) {
  }

  public BusinessEventOperationsController(BusinessEventOperations operations) {
    this.operations = operations;
  }

  @GetMapping("/api/business-events/{eventId}/deliveries")
  public WebApiResult<DiagnosticsResponse> inspect(@PathVariable("eventId") String eventId) {
    var diagnostics = operations.inspect(eventId);
    return WebApiResult.ok(new DiagnosticsResponse(diagnostics.fact().orElse(null), diagnostics.deliveries(), diagnostics.canRedrive()));
  }

  @PostMapping("/api/business-event-deliveries/{deliveryId}/redrive")
  public WebApiResult<Boolean> redrive(@PathVariable("deliveryId") String deliveryId) {
    return WebApiResult.ok(operations.redrive(deliveryId));
  }
}
