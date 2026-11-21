package com.ouroboros.notification.call.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.data.model.DataModelExecutionContext;
import com.ouroboros.notification.call.adapter.CallStateQueryResult;
import com.ouroboros.notification.call.enums.CallState;
import com.ouroboros.notification.call.service.model.CallNotificationRecordModel;

/**
 * @author liansz
 */
@Service
public class CallNotificationRecordService extends SimpleModelServiceImpl<String, CallNotificationRecordModel> {

  /** Provider receipts are platform records and must survive an unrelated caller rollback. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public List<? extends CallNotificationRecordModel> recordDispatch(Collection<CallNotificationRecordModel> records) {
    return DataModelExecutionContext.withPlatformWrite(() -> add(records));
  }

  public List<? extends CallNotificationRecordModel> searchPendingRecords(int limit)
      throws OuroborosException {
    Map<String, Object> where = new LinkedHashMap<>();
    where.put("state", Arrays.asList(CallState.PENDING.name(), CallState.UNKNOWN.name()));

    Map<String, Object> statement = new LinkedHashMap<>();
    statement.put("WHERE", where);
    statement.put("LIMIT", limit > 0 ? limit : 100);
    statement.put("ORDER", "sendTime asc");
    return searchList(statement);
  }

  public CallNotificationRecordModel applyQueryResult(String id, CallStateQueryResult result)
      throws OuroborosException {
    CallNotificationRecordModel model = getByPrimaryKey(id);
    if (result.getState() != null) {
      model.setState(result.getState().name());
    } else if (model.getState() == null) {
      model.setState(CallState.UNKNOWN.name());
    }

    if (result.getStartTime() != null && model.getSendTime() == null) {
      model.setSendTime(result.getStartTime());
    }
    if (result.getEndTime() != null) {
      model.setFinishTime(result.getEndTime());
    } else if (result.getState() != null && result.getState().isFinished()) {
      model.setFinishTime(LocalDateTime.now());
    }

    update(id, model);
    return model;
  }
}
