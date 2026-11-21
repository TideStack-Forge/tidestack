package com.ouroboros.security.rbac.profile;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.station.Migratable;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.lock.DistributedLockManager;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;
import com.ouroboros.security.rbac.entity.UserEntity;

/** Runs only in the maintenance rollout; a node can issue only after checking the physical schema. */
@Component
public class AccountIdentityReadiness {
  private static final Logger logger = LoggerFactory.getLogger(AccountIdentityReadiness.class);
  private final AccountIdentityMigration migration;
  private final TypedDataModel<String, UserEntity> users;
  private final boolean verifyOnStartup;
  private volatile boolean ready;

  public AccountIdentityReadiness(AccountIdentityMigration migration, TypedDataModel<String, UserEntity> users,
      @Value("${ouroboros.security.rbac.public-subject.verify-on-startup:false}") boolean verifyOnStartup) {
    this.migration = migration;
    this.users = users;
    this.verifyOnStartup = verifyOnStartup;
  }

  public boolean isReady() {
    return ready;
  }

  @EventListener(OuroborosContextRefreshedEvent.class)
  public void verify() {
    ready = false;
    var model = users.getDataModel();
    String column = model.getField("publicSubjectId").orElseThrow().getRawName();
    if (!verifyOnStartup) {
      ready = model.getDataStation() instanceof Migratable station
          && station.hasRequiredUniqueColumn(model, column).getOrElse(false);
      if (!ready) { logger.warn("公开标识约束未就绪；须在维护阶段启用 public-subject.verify-on-startup 完成补齐"); }
      return;
    }
    var lock = DistributedLockManager.getLockManager().getLock("security:public-subject-migration:" + model.getFullName());
    try {
      if (!lock.tryLock(60, 300, TimeUnit.SECONDS)) {
        logger.warn("公开标识核验未取得迁移锁；本节点保持停止签发");
        return;
      }
      try {
        String cursor = null;
        int updated = 0;
        for (;;) {
          String after = cursor;
          var result = CurrentSecurityContextHolder.withIdentity(SystemSecurityIdentities.system(),
              () -> migration.batch(after, 500));
          updated += (int) result.get("updated");
          if (!Boolean.TRUE.equals(result.get("hasMore"))) { break; }
          cursor = (String) result.get("lastIdentity");
        }
        ready = model.getDataStation() instanceof Migratable station
            && station.hasRequiredUniqueColumn(model, column).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
        if (ready) {
          logger.info("公开标识核验通过；补齐 {} 条账号", updated);
        } else {
          logger.warn("公开标识已补齐 {} 条账号；数据库非空或唯一约束尚未就绪，须完成模型迁移后重启", updated);
        }
      } finally {
        lock.unlock();
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      logger.warn("公开标识核验已中断；本节点保持停止签发", error);
    } catch (RuntimeException error) {
      ready = false;
      logger.error("公开标识核验失败；本节点保持停止签发", error);
    }
  }
}
