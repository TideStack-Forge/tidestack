package com.ouroboros.dev.codeintelligence;
import com.fasterxml.jackson.databind.JsonNode;
public record CodeContextRequest(String documentId, long documentVersion, long draftRevision, String codeSite,
    String language, Resource resource, Location location, JsonNode draft) {
  public record Resource(String type, String name) {}
  public record Location(String nodeName, String pointer) {}
}
