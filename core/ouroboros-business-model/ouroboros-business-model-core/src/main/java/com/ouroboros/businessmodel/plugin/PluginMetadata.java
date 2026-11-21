package com.ouroboros.businessmodel.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Metadata of a business model feature plugin.
 */
public class PluginMetadata {
  private final String version;
  private final String description;
  private final String author;
  private final List<String> requiredPlugins;

  /**
   * Create plugin metadata.
   *
   * @param version plugin version
   * @param description plugin description
   * @param author plugin author
   * @param requiredPlugins required plugin ids
   */
  public PluginMetadata(String version, String description, String author, List<String> requiredPlugins) {
    this.version = version;
    this.description = description;
    this.author = author;
    if (requiredPlugins == null) {
      this.requiredPlugins = Collections.emptyList();
    } else {
      this.requiredPlugins = Collections.unmodifiableList(new ArrayList<>(requiredPlugins));
    }
  }

  /**
   * Plugin version.
   *
   * @return version
   */
  public String getVersion() {
    return version;
  }

  /**
   * Plugin description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Plugin author.
   *
   * @return author
   */
  public String getAuthor() {
    return author;
  }

  /**
   * Required plugin identifiers.
   *
   * @return immutable required plugin id list
   */
  public List<String> getRequiredPlugins() {
    return requiredPlugins;
  }
}
