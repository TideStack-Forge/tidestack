package com.ouroboros.businesslog;

public interface BusinessLogStorage<T extends BusinessLog> {
  void save(T businessLog);
}
