package com.ouroboros.upload.generator;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

public class TemplateUtils {

  public static String processDateTimeParts(String template) {
    var now = LocalDateTime.now();
    var timestamp = String.valueOf(System.currentTimeMillis());
    var year = String.valueOf(now.getYear());
    var month = (now.getMonthValue() > 9 ? "" : "0") + now.getMonthValue();
    var day = (now.getDayOfMonth() > 9 ? "" : "0") + now.getDayOfMonth();
    var hour = (now.getHour() > 9 ? "" : "0") + now.getHour();
    var minute = (now.getMinute() > 9 ? "" : "0") + now.getMinute();
    var seconds = (now.getSecond() > 9 ? "" : "0") + now.getSecond();
    var name = template
        .replaceAll("\\{yyyy}", year)
        .replaceAll("\\{MM}", month)
        .replaceAll("\\{dd}", day)
        .replaceAll("\\{HH}", hour)
        .replaceAll("\\{mm}", minute)
        .replaceAll("\\{ss}", seconds)
        .replaceAll("\\{timestamp}", timestamp);
    return name;
  }

  public static String processRandomPart(String template) {
    var randomPattern = Pattern.compile("\\{random:(\\d+)\\}");
    var randomMatcher = randomPattern.matcher(template);
    var result = template;
    while (randomMatcher.find()) {
      var randomLength = Integer.parseInt(randomMatcher.group(1));
      var random = padLeft(String.valueOf((int) (Math.random() * Math.pow(10, randomLength))), randomLength, '0');
      result = result.replaceFirst("\\{random:" + randomLength + "\\}", random);
      randomMatcher = randomPattern.matcher(result);
    }
    return result;
  }

  private static String padLeft(String str, int length, char padChar) {
    if (str.length() >= length) {
      return str;
    }
    var left = new StringBuilder();
    for (int i = 0; i < length - str.length(); i++) {
      left.append(padChar);
    }
    return left.append(str).toString();
  }
}
