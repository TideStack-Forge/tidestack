package com.xinpai.spreadsheet.entity;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSONObject;

/**
 * config 中 merge 对象
 * 
 * @author Administrator
 */

public class ConfigMergeModel {
    /**
     * 行
     */
    private int r;
    /**
     * 列
     */
    private int c;
    /**
     * 行数
     */
    private int rs;
    /**
     * 列数
     */
    private int cs;

    /**
     * 最大行
     */
    private int maxr;
    /**
     * 最大列
     */
    private int maxc;

    /**
     * 将config的merge对象转换为对象
     * 
     * @param mc
     * @return
     */
    public static List<ConfigMergeModel> getListByDBObject(JSONObject mc) {
        List<ConfigMergeModel> _list = new ArrayList<ConfigMergeModel>();
        if (mc != null) {
            for (String k : mc.keySet()) {
                try {
                    ConfigMergeModel c = getByDBObject(mc.getJSONObject(k));
                    if (c != null) {
                        _list.add(c);
                    }
                } catch (Exception ex) {

                }
            }
        }
        return _list;
    }

    private static ConfigMergeModel getByDBObject(JSONObject mc) {
        if (mc != null) {
            if (mc.containsKey("r") && mc.containsKey("c") && mc.containsKey("rs") && mc.containsKey("cs")) {
                try {
                    ConfigMergeModel c = new ConfigMergeModel();
                    c.r = Integer.parseInt(mc.get("r").toString());
                    c.c = Integer.parseInt(mc.get("c").toString());
                    c.rs = Integer.parseInt(mc.get("rs").toString());
                    c.cs = Integer.parseInt(mc.get("cs").toString());

                    // { "2_1" : { "rs" : 5 , "cs" : 2 , "r" : 2 , "c" : 1}}
                    // 1~2 c=1 cs=2
                    // 2~6 r=2 rs=5
                    c.maxc = c.c + c.cs - 1;
                    c.maxr = c.r + c.rs - 1;

                    return c;
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 是否在范围内
     * 
     * @param r
     * @param c
     * @return
     */
    public boolean isRange(int r, int c) {
        if (r >= this.r && r <= this.maxr && c >= this.c && c <= this.maxc) {
            return true;
        }
        return false;
    }

    public int getR() {
        return this.r;
    }

    public void setR(int r) {
        this.r = r;
    }

    public int getC() {
        return this.c;
    }

    public void setC(int c) {
        this.c = c;
    }

    public int getRs() {
        return this.rs;
    }

    public void setRs(int rs) {
        this.rs = rs;
    }

    public int getCs() {
        return this.cs;
    }

    public void setCs(int cs) {
        this.cs = cs;
    }

    public int getMaxr() {
        return this.maxr;
    }

    public void setMaxr(int maxr) {
        this.maxr = maxr;
    }

    public int getMaxc() {
        return this.maxc;
    }

    public void setMaxc(int maxc) {
        this.maxc = maxc;
    }

}
