package com.ouroboros.businessmodel.action;

import java.util.List;

/** Side-effect-free description of whether a caller can currently invoke an Action. */
public record BusinessActionAvailability(Status status, List<String> reasons, Blockage blockage) {
  public BusinessActionAvailability(Status status, List<String> reasons) {
    this(status, reasons, status == Status.BLOCKED ? Blockage.AUTHORIZATION : Blockage.NONE);
  }

  public BusinessActionAvailability {
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
  }

  public enum Status {
    EXECUTABLE,
    BLOCKED,
    NEEDS_INPUT
  }

  public enum Blockage {
    NONE,
    AUTHORIZATION,
    CONTRACT,
    PRECONDITION
  }
}
