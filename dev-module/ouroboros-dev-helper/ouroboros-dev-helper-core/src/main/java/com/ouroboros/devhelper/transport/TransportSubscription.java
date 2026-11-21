package com.ouroboros.devhelper.transport;

/** A helper message subscription. Closing it detaches the receiver, not the shared transport. */
@FunctionalInterface
public interface TransportSubscription extends AutoCloseable {
  @Override
  void close();
}
