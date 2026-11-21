package com.ouroboros.logicflow.diagnostic;

/** A source considered by the actual argument mapper. A null sourceNode denotes external input. */
public record FlowInputTransfer(String sourceNode, int sourceOutputPort, String targetNode, int targetInputPort,
                                boolean active, boolean selected) {
}
