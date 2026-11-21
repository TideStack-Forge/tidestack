package com.ouroboros.devhelper.command;

import com.ouroboros.devhelper.metadata.HelperMetadataSnapshots;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Reads loaded application metadata without rebuilding the application's context. */
@Component
@ConditionalOnProperty(name = "ouroboros.dev-helper.test-app", havingValue = "false", matchIfMissing = true)
public final class ReadMetadataCommand extends AbstractAsyncCommand {
  private final HelperMetadataSnapshots snapshots;
  public ReadMetadataCommand(HelperMetadataSnapshots snapshots) {
    super(Commands.READ_METADATA.getCommand()); this.snapshots = snapshots;
  }
  @Override protected void doExecute(CommandRequest request) {
    if (!snapshots.accepts(request)) { failure(request, "开发助手元数据快照协议或实例不匹配"); return; }
    success(request, snapshots.collect(request));
  }
}
