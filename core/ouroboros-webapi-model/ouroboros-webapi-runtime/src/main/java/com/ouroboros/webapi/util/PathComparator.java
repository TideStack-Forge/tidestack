package com.ouroboros.webapi.util;

import java.util.Comparator;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.webapi.WebAPIModel;

public class PathComparator implements Comparator<WebAPIModel> {
  // 路径变量检测正则（含正则约束的变量如 {id:\\d+}）
  private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{(\\w+):((?:\\\\\\\\.|[^}])+)\\}");

  @Override
  public int compare(WebAPIModel api1, WebAPIModel api2) {
    var path1 = splitPathParts(getApiPath(api1));
    var path2 = splitPathParts(getApiPath(api2));

    // 维度1：路径部分数量（深度优先）
    int partCountCompare = Integer.compare(countNonEmpty(path2), countNonEmpty(path1));
    if (partCountCompare != 0) {
      return partCountCompare;
    }

    // 维度2：路径组成部分评分
    var score1 = computeSpecificityScore(path1);
    var score2 = computeSpecificityScore(path2);
    //分数越高越宽泛，排序越靠后
    return Integer.compare(score1, score2);
  }

  private static String getApiPath(WebAPIModel model) {
    var path = model.getPath();
    if (StringUtils.isBlank(path)) {
      return "";
    }
    if (StringUtils.startsWithIgnoreCase(path, model.getScope().getPrefix())) {
      return StringUtils.substringAfter(path, model.getScope().getPrefix());
    }
    return path;
  }

  // 辅助方法：分割路径部分
  private String[] splitPathParts(String path) {
    return path.split("/");
  }

  private int countNonEmpty(String[] segments) {
    int count = 0;
    for (String segment : segments) {
      if (!segment.isEmpty()) {
        count++;
      }
    }
    return count;
  }

  private int computeSpecificityScore(String[] segments) {
    int score = 0;

    for (String segment : segments) {
      if (segment.isEmpty()) {
        continue;
      }
      if (isRegexVariable(segment)) {
        score += hasStaticPart(segment) ? 1 : 2;
      } else if (hasVariable(segment)) {
        score += hasStaticPart(segment) ? 3 : 4;
      } else if ("**".equals(segment)) {
        score += 10;
      } else if (segment.contains("*")) {
        score += 6;
      } else if (segment.contains("?")) {
        score += 5;
      }
    }

    return score;
  }

  private boolean hasStaticPart(String segment) {
    return !segment.startsWith("{") || !segment.endsWith("}");
  }

  private boolean hasVariable(String segment) {
    return segment.contains("{") && segment.contains("}");
  }

  private boolean isRegexVariable(String segment) {
    var matcher = VARIABLE_PATTERN.matcher(segment);
    return matcher.matches() && matcher.group(2) != null;
  }
}
