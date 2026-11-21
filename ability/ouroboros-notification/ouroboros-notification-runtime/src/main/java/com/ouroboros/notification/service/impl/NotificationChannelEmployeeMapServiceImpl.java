package com.ouroboros.notification.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.notification.service.NotificationChannelEmployeeMapService;
import com.ouroboros.notification.service.model.NotificationChannelEmployeeMapModel;

/**
 * @author liansz
 */
@Service
public class NotificationChannelEmployeeMapServiceImpl
    extends SimpleModelServiceImpl<String, NotificationChannelEmployeeMapModel>
    implements NotificationChannelEmployeeMapService {

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private List<com.ouroboros.notification.adapter.NotificationAdapterSupplier> managedSuppliers = List.of();

  @Override
  public void bind(String channel, String employeeId, String address, String nikeName) throws OuroborosException {
    if (com.ouroboros.notification.adapter.NotificationAdapterCenter.findSupplier(channel, managedSuppliers)
        .flatMap(supplier -> supplier.getEmployeeRecipients()).isPresent()) {
      throw new com.ouroboros.exception.ValidationException("此渠道的收件地址由组织关联维护，请在集成管理中关联企业成员");
    }
    Map<String, Object> where = new HashMap<>() {{
      put("or", new HashMap<>() {{
        put("employeeId", employeeId);
        put("address", address);
      }});
      put("channel", channel);
    }};

    NotificationChannelEmployeeMapModel model = new NotificationChannelEmployeeMapModel();
    model.setChannel(channel);
    model.setEmployeeId(employeeId);
    model.setAddress(address);
    model.setRemarks(nikeName);
    model.setOperateTime(LocalDateTime.now());
    getDataModel()
        .delete(where)
        .mapTry(r -> add(model))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public void unbind(String channel, String employeeId) throws OuroborosException {
    Map<String, Object> where = new HashMap<>() {{
      put("channel", channel);
      put("employeeId", employeeId);
    }};

    getDataModel()
        .delete(where)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public NotificationChannelEmployeeMapModel getByEmployeeId(String channel, String employeeId) {
    Map<String, Object> statement = new HashMap<>(
        Collections.singletonMap(
            "where",
            new HashMap<>() {{
              put("channel", channel);
              put("employeeId", employeeId);
            }}
        )
    );

    return searchOne(statement);
  }

  @Override
  public List<? extends NotificationChannelEmployeeMapModel> searchByEmployeeIds(String channel, List<String> employeeIds) {
    Map<String, Object> statement = new HashMap<>(
        Collections.singletonMap(
            "where",
            new HashMap<>() {{
              put("channel", channel);
              put("employeeId", employeeIds);
            }}
        )
    );
    return searchList(statement);
  }
}
