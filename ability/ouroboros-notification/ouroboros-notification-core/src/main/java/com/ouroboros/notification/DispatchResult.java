package com.ouroboros.notification;

import java.util.List;

/** 发送器完成渠道调用后的结果。可靠投递方必须检查 success。 */
public record DispatchResult(boolean success, List<ChannelResult> channels) {
  public DispatchResult {
    channels = channels == null ? List.of() : List.copyOf(channels);
  }

  public record ChannelResult(String channel, boolean success, String error) {}

  public static DispatchResult unknown() {
    return new DispatchResult(false, List.of());
  }
}
