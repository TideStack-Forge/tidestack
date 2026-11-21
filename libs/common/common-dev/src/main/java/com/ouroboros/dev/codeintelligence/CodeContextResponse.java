package com.ouroboros.dev.codeintelligence;
import java.util.Map;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
public record CodeContextResponse(String documentId, long documentVersion, long draftRevision, String contextVersion,
    String runtimeStatus, Map<String, Object> runtimeIdentity, ScriptIntelligenceSnapshot snapshot, TypeShape expectedResult) {}
