package com.zsw.devopshsyq.parse;

/**
 * @describe:
 * @author: ljj
 * @creattime: 2022/8/29 14:13
 */
public class ParseJava {
    public static void main(String[] args) {
        Integer.parseInt("");
        Long.parseLong("");
        Float.parseFloat("");

        try {
            Double.parseDouble("");
        } catch (Exception e) {
            e.printStackTrace();
        }

        Double.valueOf("");
        Float.valueOf("");
        Integer.valueOf("");
        Long.valueOf("");

        Integer.valueOf(1);

    }
}
