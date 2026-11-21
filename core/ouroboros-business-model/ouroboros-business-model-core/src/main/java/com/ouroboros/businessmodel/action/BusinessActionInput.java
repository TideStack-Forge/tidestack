package com.ouroboros.businessmodel.action;

/** Business parameters and payload, independent of their transport. */
public interface BusinessActionInput<P, D> {
  boolean hasParams();

  P params();

  boolean hasData();

  D data();

  static <P, D> BusinessActionInput<P, D> of(P params, D data) {
    return new DefaultBusinessActionInput<>(true, params, true, data);
  }

  static <P> BusinessActionInput<P, Object> paramsOnly(P params) {
    return new DefaultBusinessActionInput<>(true, params, false, null);
  }

  static <D> BusinessActionInput<Object, D> dataOnly(D data) {
    return new DefaultBusinessActionInput<>(false, null, true, data);
  }

  static BusinessActionInput<Object, Object> empty() {
    return new DefaultBusinessActionInput<>(false, null, false, null);
  }
}
