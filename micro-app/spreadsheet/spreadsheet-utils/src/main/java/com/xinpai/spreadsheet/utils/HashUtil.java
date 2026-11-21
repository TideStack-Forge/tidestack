package com.xinpai.spreadsheet.utils;

import java.security.MessageDigest;

public class HashUtil {
  public static String getSHA1HashString(String str) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      md.update(str.getBytes("UTF-8"));
      byte[] digest = md.digest();
      StringBuffer sb = new StringBuffer();
      for (byte b : digest) {
        sb.append(String.format("%02x", b & 0xff));
      }
      return sb.toString();
    } catch (Exception e) {
      return null;
    }
  }
}