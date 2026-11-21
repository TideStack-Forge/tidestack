package com.ouroboros.data.initialization;

public interface InitializationLedger {
  InitializationRun begin(DataInitializationAsset asset, InitializationBlock block, LedgerKey key);

  void succeed(InitializationRun run, InitializationBlockSummary summary);

  void fail(InitializationRun run, Throwable cause);
}
