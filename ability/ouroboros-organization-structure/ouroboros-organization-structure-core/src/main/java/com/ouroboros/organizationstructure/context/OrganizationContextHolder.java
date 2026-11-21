package com.ouroboros.organizationstructure.context;

import java.util.Optional;

/**
 * @author liansz
 */
public class OrganizationContextHolder {

  private static final ThreadLocal<OrganizationContext> CONTEXT_HOLDER = new ThreadLocal<>();

  public static void clear() {
    CONTEXT_HOLDER.remove();
  }

  public static Optional<OrganizationContext> getContext() {
    return Optional.ofNullable(CONTEXT_HOLDER.get());
  }

  public static void setContext(OrganizationContext context) {
    CONTEXT_HOLDER.set(context);
  }
}
