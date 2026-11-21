package com.ouroboros.logicflow.authoring.analysis;
import com.ouroboros.codeintelligence.type.TypeShape;
public record PortShape(TypeShape shape, String availability) {
  public static PortShape always(TypeShape shape) { return new PortShape(shape, "always"); }
}
