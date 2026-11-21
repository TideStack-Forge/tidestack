package com.ouroboros.dev.collaboration;

/** A model module exposes its existing VCS files to the collaboration service through a Spring bean. */
public interface CollaborativeResource {
  String collaborationType();
  String collaborationDirectory();
  String collaborationSuffix();
  void validateCollaborationSeed(String id, String json);
}
