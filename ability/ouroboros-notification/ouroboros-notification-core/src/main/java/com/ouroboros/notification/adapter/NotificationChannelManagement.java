package com.ouroboros.notification.adapter;

import java.util.List;
import java.util.Map;

/** Supplements adapter Java properties with display and validation rules; does not define types. */
public record NotificationChannelManagement(String title, List<String> configurationGroups,
    Map<String, Field> schemaFields, Map<String, Field> optionsFields) {
  public NotificationChannelManagement {
    configurationGroups = List.copyOf(configurationGroups);
    schemaFields = Map.copyOf(schemaFields); optionsFields = Map.copyOf(optionsFields);
  }

  public record Field(String label, boolean required, Integer minimum, Integer maximum) {
    public static Field required(String label) { return new Field(label, true, null, null); }
    public static Field optional(String label) { return new Field(label, false, null, null); }
    public static Field range(String label, int minimum, int maximum) { return new Field(label, false, minimum, maximum); }
  }

  public record Problem(String path, String code, String message) { }
}
