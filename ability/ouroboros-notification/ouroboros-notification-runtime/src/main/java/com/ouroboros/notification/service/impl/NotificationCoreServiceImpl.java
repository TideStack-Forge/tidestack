package com.ouroboros.notification.service.impl;

import org.springframework.stereotype.Service;

import com.ouroboros.data.service.SimpleModelServiceImpl;
import com.ouroboros.notification.service.NotificationCoreService;
import com.ouroboros.notification.service.model.NotificationModel;

/**
 * @author liansz
 */
@Service
public class NotificationCoreServiceImpl extends SimpleModelServiceImpl<String, NotificationModel> implements NotificationCoreService {
}
