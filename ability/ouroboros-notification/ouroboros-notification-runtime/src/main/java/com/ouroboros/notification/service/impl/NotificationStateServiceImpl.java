package com.ouroboros.notification.service.impl;

import org.springframework.stereotype.Service;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.notification.service.NotificationStateService;
import com.ouroboros.notification.service.model.NotificationStateModel;

/**
 * @author liansz
 */
@Service
public class NotificationStateServiceImpl extends SimpleModelServiceImpl<String, NotificationStateModel> implements NotificationStateService {
}
