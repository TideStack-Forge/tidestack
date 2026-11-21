package com.ouroboros.enterprise.feishu;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Feishu event signature and encrypted payload support. */
public final class FeishuCrypto {

  private FeishuCrypto() {
  }

  public static String signature(String timestamp, String nonce, String encryptKey, String body) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest((timestamp + nonce + encryptKey + body).getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte item : digest) {
        hex.append(String.format("%02x", item));
      }
      return hex.toString();
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 不可用", ex);
    }
  }

  public static String decrypt(String encryptKey, String encrypted) {
    try {
      byte[] key = MessageDigest.getInstance("SHA-256")
          .digest(encryptKey.getBytes(StandardCharsets.UTF_8));
      byte[] framed = Base64.getDecoder().decode(encrypted);
      if (framed.length <= 16) {
        throw new IllegalStateException("飞书加密回调长度非法");
      }
      byte[] iv = Arrays.copyOfRange(framed, 0, 16);
      byte[] ciphertext = Arrays.copyOfRange(framed, 16, framed.length);
      Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
      byte[] padded = cipher.doFinal(ciphertext);
      int padding = padded[padded.length - 1] & 0xff;
      if (padding < 1 || padding > 16 || padding > padded.length) {
        throw new IllegalStateException("飞书加密回调填充非法");
      }
      for (int index = padded.length - padding; index < padded.length; index++) {
        if ((padded[index] & 0xff) != padding) {
          throw new IllegalStateException("飞书加密回调填充不一致");
        }
      }
      return new String(
          Arrays.copyOf(padded, padded.length - padding), StandardCharsets.UTF_8);
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("飞书回调解密失败", ex);
    }
  }
}
