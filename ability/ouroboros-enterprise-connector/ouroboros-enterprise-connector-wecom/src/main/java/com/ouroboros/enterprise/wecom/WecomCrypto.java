package com.ouroboros.enterprise.wecom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Enterprise WeChat callback signature and AES framing implementation. */
public final class WecomCrypto {

  private WecomCrypto() {
  }

  public static String sha1Signature(String token, String timestamp, String nonce, String encrypted) {
    try {
      String[] parts = {token, timestamp, nonce, encrypted};
      Arrays.sort(parts);
      byte[] digest = MessageDigest.getInstance("SHA-1")
          .digest(String.join("", parts).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte item : digest) {
        hex.append(String.format("%02x", item));
      }
      return hex.toString();
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-1 不可用", ex);
    }
  }

  public static String decrypt(String encodingAesKey, String encrypted, String receiveId) {
    try {
      byte[] key = aesKey(encodingAesKey);
      Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
          new IvParameterSpec(Arrays.copyOfRange(key, 0, 16)));
      byte[] padded = cipher.doFinal(Base64.getDecoder().decode(encrypted));
      byte[] plain = unpad(padded);
      if (plain.length < 20) {
        throw new IllegalStateException("回调明文缺少 framing 头");
      }
      int messageLength = ((plain[16] & 0xff) << 24) | ((plain[17] & 0xff) << 16)
          | ((plain[18] & 0xff) << 8) | (plain[19] & 0xff);
      int suffixStart = 20 + messageLength;
      if (messageLength < 0 || suffixStart > plain.length) {
        throw new IllegalStateException("回调明文长度非法");
      }
      String message = new String(plain, 20, messageLength, StandardCharsets.UTF_8);
      String actualReceiveId = new String(
          plain, suffixStart, plain.length - suffixStart, StandardCharsets.UTF_8);
      if (receiveId == null || !receiveId.equals(actualReceiveId)) {
        throw new IllegalStateException("回调 receiveId 校验失败");
      }
      return message;
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("企业微信回调解密失败", ex);
    }
  }

  private static byte[] aesKey(String encodingAesKey) {
    String padded = encodingAesKey.length() % 4 == 0 ? encodingAesKey : encodingAesKey + "=";
    byte[] decoded = Base64.getDecoder().decode(padded);
    if (decoded.length != 32) {
      throw new IllegalArgumentException("企业微信 EncodingAESKey 解码后必须为 32 字节");
    }
    return decoded;
  }

  private static byte[] unpad(byte[] padded) {
    if (padded.length == 0) {
      throw new IllegalStateException("企业微信回调填充为空");
    }
    int padding = padded[padded.length - 1] & 0xff;
    if (padding < 1 || padding > 32 || padding > padded.length) {
      throw new IllegalStateException("企业微信回调 PKCS#7 填充非法");
    }
    for (int index = padded.length - padding; index < padded.length; index++) {
      if ((padded[index] & 0xff) != padding) {
        throw new IllegalStateException("企业微信回调 PKCS#7 填充不一致");
      }
    }
    return Arrays.copyOf(padded, padded.length - padding);
  }
}
