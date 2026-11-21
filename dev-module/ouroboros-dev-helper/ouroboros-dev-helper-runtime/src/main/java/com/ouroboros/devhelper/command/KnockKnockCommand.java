package com.ouroboros.devhelper.command;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.ouroboros.devhelper.util.JarUtil;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.util.HelperRuntimeIdentity;

@Component
@ConditionalOnProperty(name = "ouroboros.dev-helper.test-app", havingValue = "false", matchIfMissing = true)
public class KnockKnockCommand extends AbstractAsyncCommand {
  private static final String COMMAND = Commands.KNOCK_KNOCK.getCommand();

  private final com.ouroboros.devhelper.metadata.HelperMetadataSnapshots snapshots;
  public KnockKnockCommand() { this(null); }
  @org.springframework.beans.factory.annotation.Autowired
  public KnockKnockCommand(com.ouroboros.devhelper.metadata.HelperMetadataSnapshots snapshots) {
    super(COMMAND); this.snapshots = snapshots;
  }

  @Override
  protected void doExecute(CommandRequest request) {
    Map<String, Object> payload = new HashMap<>();
    if (request.getParams() != null) {
      payload.putAll(request.getParams());
    }
    payload.put("version", JarUtil.getHelperVersion());
    if (snapshots != null) payload.put("metadataEpoch", snapshots.epoch());
    MetadataRefreshProtocol.addHelperIdentity(payload, HelperRuntimeIdentity.current());
    success(request, payload);
  }

}
