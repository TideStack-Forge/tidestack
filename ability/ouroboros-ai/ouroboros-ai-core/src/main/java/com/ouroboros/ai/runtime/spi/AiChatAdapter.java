package com.ouroboros.ai.runtime.spi;

import com.ouroboros.ai.AiProviderFamily;
import com.ouroboros.ai.chat.ChatRequest;
import com.ouroboros.ai.chat.ChatResponse;
import com.ouroboros.ai.descriptor.AiModelDescriptor;

public interface AiChatAdapter {

  AiProviderFamily getProviderFamily();

  ChatResponse chat(AiModelDescriptor modelDescriptor, ChatRequest request);
}
