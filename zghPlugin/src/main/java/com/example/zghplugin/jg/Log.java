package com.example.zghplugin.jg;

/**
 * 描述：
 * <p>
 * author: Tiger
 * date: 2021/10/8
 * version 1.0
 */
public class Log {

    private static final boolean enable = false;

    public static void d(String text) {
        if (enable) System.out.println(text);
    }
    public static void ddd(String text) {
        System.out.println(text);
    }
}
