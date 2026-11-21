package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkCrypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 钉钉回调加密测试辅助（与 main 的 {@link DingtalkCrypto#decrypt} 对应）：
 * 按官方 framing（random16 + msg_len(4B 大端) + msg + receiveId）构造加密载荷。
 *
 * <p>random16 可注入（默认全零）以构造确定性载荷；官方已知测试向量不在调研文档内，
 * 覆盖以往返 + framing 负例（receiveId 不匹配/长度不匹配）为准。
 */
final class DingtalkCryptoTestSupport {

  private DingtalkCryptoTestSupport() {
  }

  static String encrypt(String encodingAesKey, String plainText) {
    return encrypt(encodingAesKey, plainText, "corp-1");
  }

  static String encrypt(String encodingAesKey, String plainText, String receiveId) {
    return encrypt(encodingAesKey, plainText, receiveId, new byte[16]);
  }

  static String encrypt(String encodingAesKey, String plainText, String receiveId, byte[] random16) {
    try {
      byte[] msg = plainText.getBytes(StandardCharsets.UTF_8);
      byte[] receive = receiveId.getBytes(StandardCharsets.UTF_8);
      ByteBuffer framed = ByteBuffer.allocate(16 + 4 + msg.length + receive.length);
      framed.put(random16);
      framed.putInt(msg.length);
      framed.put(msg);
      framed.put(receive);
      byte[] key = DingtalkCrypto.aesKeyBytes(encodingAesKey);
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
          new IvParameterSpec(Arrays.copyOfRange(key, 0, 16)));
      return Base64.getEncoder().encodeToString(cipher.doFinal(framed.array()));
    } catch (Exception ex) {
      throw new IllegalStateException("钉钉回调加密失败: " + ex.getMessage(), ex);
    }
  }
}
