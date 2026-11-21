package com.ouroboros.devhelper.command;

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.ouroboros.devhelper.metadata.MetadataCollector;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.util.HelperRuntimeIdentity;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshEvent;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;

@Component
@ConditionalOnProperty(name = "ouroboros.dev-helper.test-app", havingValue = "false", matchIfMissing = true)
public class RefreshMetadataCommand extends AbstractAsyncCommand {
  private static final Logger logger = LoggerFactory.getLogger(RefreshMetadataCommand.class);
  private static final String COMMAND = Commands.REFRESH_METADATA.getCommand();
  private final ApplicationEventPublisher applicationEventPublisher;
  private final com.ouroboros.devhelper.metadata.HelperMetadataSnapshots snapshots;

  @Autowired
  public RefreshMetadataCommand(ApplicationEventPublisher publisher, com.ouroboros.devhelper.metadata.HelperMetadataSnapshots snapshots) {
    super(COMMAND);
    this.applicationEventPublisher = publisher;
    this.snapshots = snapshots;
  }

  public RefreshMetadataCommand(ApplicationEventPublisher publisher) {
    this(publisher, new com.ouroboros.devhelper.metadata.HelperMetadataSnapshots());
  }

  RefreshMetadataCommand(
      ApplicationEventPublisher applicationEventPublisher,
      Supplier<Stream<MetadataCollector>> metadataCollectorStream
  ) {
    this(applicationEventPublisher, metadataCollectorStream, HelperRuntimeIdentity.current());
  }

  RefreshMetadataCommand(
      ApplicationEventPublisher applicationEventPublisher,
      Supplier<Stream<MetadataCollector>> metadataCollectorStream,
      HelperIdentity helperIdentity
  ) {
    super(COMMAND);
    this.applicationEventPublisher = applicationEventPublisher;
    this.snapshots = new com.ouroboros.devhelper.metadata.HelperMetadataSnapshots(metadataCollectorStream, helperIdentity);
  }

  @Override
  protected void doExecute(CommandRequest request) {
    if (!snapshots.accepts(request)) {
      failure(request, "开发助手元数据快照协议或实例不匹配");
      return;
    }
    logger.info("Triggering metadata refresh for app: {}", request.getAppName());
    applicationEventPublisher.publishEvent(new OuroborosContextRefreshEvent(request));
  }

  // 当上下文完成刷新时判断是否为命令触发的刷新事件，如果是则返回刷新结果
  @EventListener
  public void onContextRefresh(OuroborosContextRefreshedEvent event) {
    if (!(event.getSource() instanceof OuroborosContextRefreshEvent eventSource)) {
      return;
    }
    if (!(eventSource.getSource() instanceof CommandRequest request)) {
      return;
    }

    try {
      logger.info("Context refreshed, collecting metadata for app: {}", request.getAppName());
      var snapshot = snapshots.collect(request);
      success(request, snapshot);
    } catch (RuntimeException exception) {
      logger.error("Metadata collection failed for app: {}", request.getAppName(), exception);
      failure(request, exception.getMessage() == null ? "元数据收集失败" : exception.getMessage());
    }
  }

}
