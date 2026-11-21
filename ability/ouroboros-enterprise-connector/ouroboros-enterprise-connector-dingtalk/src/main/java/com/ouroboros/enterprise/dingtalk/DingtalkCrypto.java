package com.ouroboros.enterprise.dingtalk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 钉钉回调加解密工具（research：签名 sha1(sort(token,timestamp,nonce,msg_encrypt))；
 * EncodingAESKey 43 字符解码为 32 字节 AESKey；AES-CBC、PKCS#7，IV 为 AESKey 前 16 字节）。
 */
public final class DingtalkCrypto {

  private DingtalkCrypto() {
  }

  public static String sha1Signature(String token, String timestamp, String nonce, String msgEncrypt) {
    try {
      String[] parts = {token, timestamp, nonce, msgEncrypt};
      Arrays.sort(parts);
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] hash = digest.digest(String.join("", parts).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-1 不可用", ex);
    }
  }

  public static byte[] aesKeyBytes(String encodingAesKey) {
    String padded = encodingAesKey.length() % 4 == 0 ? encodingAesKey : encodingAesKey + "=";
    byte[] decoded = Base64.getDecoder().decode(padded);
    if (decoded.length != 32) {
      throw new IllegalArgumentException("EncodingAESKey 解码后必须为 32 字节");
    }
    return Arrays.copyOfRange(decoded, 0, 32);
  }

  /**
   * 解密钉钉回调 encrypt 字段并按官方 framing 解析：random16(16B) + msg_len(4B 大端) + msg + receiveId；
   * receiveId 与配置的 corpId 不匹配视为非法载荷。
   */
  public static String decrypt(String encodingAesKey, String msgEncrypt, String receiveId) {
    try {
      byte[] key = aesKeyBytes(encodingAesKey);
      Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
          new IvParameterSpec(Arrays.copyOfRange(key, 0, 16)));
      byte[] padded = cipher.doFinal(Base64.getDecoder().decode(msgEncrypt));
      if (padded.length == 0) { throw new IllegalStateException("钉钉回调填充为空"); }
      int padding = padded[padded.length - 1] & 0xff;
      if (padding < 1 || padding > 32 || padding > padded.length) { throw new IllegalStateException("钉钉回调填充非法"); }
      for (int index = padded.length - padding; index < padded.length; index++) {
        if ((padded[index] & 0xff) != padding) { throw new IllegalStateException("钉钉回调填充不一致"); }
      }
      byte[] plain = Arrays.copyOf(padded, padded.length - padding);
      if (plain.length < 20) {
        throw new IllegalStateException("钉钉回调解密长度非法（缺 framing 头）");
      }
      int msgLen = ((plain[16] & 0xFF) << 24) | ((plain[17] & 0xFF) << 16)
          | ((plain[18] & 0xFF) << 8) | (plain[19] & 0xFF);
      int suffixStart = 20 + msgLen;
      if (msgLen < 0 || msgLen > plain.length - 20) {
        throw new IllegalStateException("钉钉回调解密长度与消息长度不匹配");
      }
      String msg = new String(plain, 20, msgLen, StandardCharsets.UTF_8);
      String received = new String(plain, suffixStart, plain.length - suffixStart, StandardCharsets.UTF_8);
      if (receiveId == null || !receiveId.equals(received)) {
        throw new IllegalStateException("钉钉回调 receiveId 校验失败");
      }
      return msg;
    } catch (Exception ex) {
      throw new IllegalStateException("钉钉回调解密失败: " + ex.getMessage(), ex);
    }
  }

  public static String encrypt(String encodingAesKey, String message, String receiveId) {
    try {
      byte[] key = aesKeyBytes(encodingAesKey);
      byte[] content = message.getBytes(StandardCharsets.UTF_8);
      byte[] owner = receiveId.getBytes(StandardCharsets.UTF_8);
      byte[] random = new byte[16];
      new java.security.SecureRandom().nextBytes(random);
      int size = 20 + content.length + owner.length;
      int padding = 32 - size % 32;
      var frame = java.nio.ByteBuffer.allocate(size + padding);
      frame.put(random).putInt(content.length).put(content).put(owner);
      Arrays.fill(frame.array(), size, size + padding, (byte) padding);
      Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(key, 0, 16));
      return Base64.getEncoder().encodeToString(cipher.doFinal(frame.array()));
    } catch (java.security.GeneralSecurityException ex) {
      throw new IllegalStateException("钉钉回调加密失败", ex);
    }
  }

}
