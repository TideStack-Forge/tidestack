package com.ouroboros.businessmodel.event;

/** Persists an occurrence in the caller's transaction so a committed fact can be delivered later. */
public interface BusinessEventOccurrenceRecorder {
  void record(BusinessEventOccurrence<?> occurrence);
}
