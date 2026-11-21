package com.ouroboros.notification.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.notification.service.NotificationCoreService;
import com.ouroboros.notification.service.NotificationStateService;
import com.ouroboros.notification.service.model.NotificationModel;
import com.ouroboros.notification.service.model.NotificationStateModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

  @Test
  void addShouldPropagateSavedNotificationIdToAllStates() {
    NotificationCoreService coreService = mock(NotificationCoreService.class);
    NotificationStateService stateService = mock(NotificationStateService.class);
    NotificationServiceImpl service = new NotificationServiceImpl();
    ReflectionTestUtils.setField(service, "coreService", coreService);
    ReflectionTestUtils.setField(service, "stateService", stateService);

    NotificationModel request = new NotificationModel();
    request.setTemplate("tpl");

    NotificationModel saved = new NotificationModel();
    saved.setId("notification-1");
    when(coreService.add(request)).thenReturn(saved);

    NotificationStateModel state1 = new NotificationStateModel();
    state1.setChannel("email");
    NotificationStateModel state2 = new NotificationStateModel();
    state2.setChannel("sms");
    List<NotificationStateModel> states = new ArrayList<>();
    states.add(state1);
    states.add(state2);

    service.add(request, states);

    ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
    verify(stateService).add(captor.capture());

    @SuppressWarnings("unchecked")
    Collection<NotificationStateModel> savedStates = (Collection<NotificationStateModel>) captor.getValue();
    assertEquals(2, savedStates.size());
    savedStates.forEach(state -> assertEquals("notification-1", state.getNotificationId()));
  }
}
