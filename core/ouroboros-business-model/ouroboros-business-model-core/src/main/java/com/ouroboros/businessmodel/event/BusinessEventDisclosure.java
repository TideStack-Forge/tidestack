package com.ouroboros.businessmodel.event;

/** Rechecks full Event disclosure for the current real user against historical and published contracts. */
@FunctionalInterface
public interface BusinessEventDisclosure {
  boolean canRead(BusinessEventOccurrence<?> occurrence);
}
