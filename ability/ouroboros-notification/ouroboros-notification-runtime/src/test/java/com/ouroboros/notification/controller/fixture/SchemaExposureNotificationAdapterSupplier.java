package com.ouroboros.notification.controller.fixture;

import java.util.Collection;
import java.util.Map;

import com.ouroboros.notification.adapter.NotificationAdapter;
import com.ouroboros.notification.adapter.NotificationAdapterSupplier;
import com.ouroboros.notification.adapter.NotificationChannelOptions;
import com.ouroboros.notification.adapter.NotificationChannelSchema;

public class SchemaExposureNotificationAdapterSupplier implements NotificationAdapterSupplier {

  @Override
  public String getName() {
    return "schema-fixture-channel";
  }

  @SuppressWarnings("unchecked")
  @Override
  public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
    return (NotificationAdapter<D, O>) new SchemaExposureNotificationAdapter();
  }

  public static class SchemaExposureNotificationAdapter implements NotificationAdapter<SchemaFixtureChannelSchema, SchemaFixtureChannelOptions> {

    @Override
    public void send(Collection<String> addresses, SchemaFixtureChannelSchema channelSchema, Map<String, Object> payload,
        SchemaFixtureChannelOptions channelOptions) {
    }
  }

  public static class SchemaFixtureChannelSchema implements NotificationChannelSchema {
    private String token;
  }

  public static class SchemaFixtureChannelOptions extends SchemaFixtureChannelOptionsSupport implements NotificationChannelOptions {
  }

  public static class SchemaFixtureChannelOptionsSupport {
    private String endpoint;
    private String sender;
  }
}
