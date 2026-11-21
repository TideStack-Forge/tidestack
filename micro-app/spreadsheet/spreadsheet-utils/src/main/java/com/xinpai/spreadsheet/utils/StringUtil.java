package com.xinpai.spreadsheet.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {

    private final static Pattern p = Pattern.compile("\\s*|\t|\r|\n");

    public static String replaceBlank(String str) {
        String dest = "";
        if (str != null) {
            Matcher m = p.matcher(str);
            dest = m.replaceAll("");
        }
        return dest;
    }

    public static Boolean isNullOrEmpty(String str) {
        return null == str || "".equals(str);
    }

    public static String toString(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}
