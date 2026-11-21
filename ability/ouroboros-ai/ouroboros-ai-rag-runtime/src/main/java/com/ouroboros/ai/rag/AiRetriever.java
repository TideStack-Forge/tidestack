package com.ouroboros.ai.rag;

import com.ouroboros.ai.retrieval.RetrieverRequest;
import com.ouroboros.ai.retrieval.RetrieverResponse;

@FunctionalInterface
public interface AiRetriever {

  RetrieverResponse retrieve(RetrieverRequest request);
}
