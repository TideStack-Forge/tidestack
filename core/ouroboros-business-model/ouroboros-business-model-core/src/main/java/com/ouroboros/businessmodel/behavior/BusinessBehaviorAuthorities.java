package com.ouroboros.businessmodel.behavior;

/** Model-management access to contract definitions, separate from invoking Actions or reading Event facts. */
public final class BusinessBehaviorAuthorities {
  private BusinessBehaviorAuthorities() { }

  public static String inspect(String modelFullName) { return modelFullName + ".behaviors.inspect"; }
}
