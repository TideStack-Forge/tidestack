package com.xinpai.spreadsheet.utils;

import java.net.URLDecoder;
import java.net.URLEncoder;

public class UrlUtil {
    /**
     * 解码
     * 
     * @param str
     * @return
     */
    public static String urlDecode(String str) {
        try {
            String strReturn = URLDecoder.decode(str, "UTF-8");
            return strReturn;
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * 编码
     * 
     * @param str
     * @return
     */
    public static String urlEncode(String str) {
        try {
            String strReturn = URLEncoder.encode(str, "UTF-8");
            return strReturn;
        } catch (Exception e) {
        }
        return null;
    }
}
