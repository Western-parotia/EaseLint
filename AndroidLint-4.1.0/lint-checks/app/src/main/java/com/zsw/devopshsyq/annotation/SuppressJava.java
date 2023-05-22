package com.zsw.devopshsyq.annotation;

import android.annotation.SuppressLint;

/**
 * @describe:
 * @author: ljj
 * @creattime: 2022/10/17 17:25
 */

//抑制-原因
@SuppressLint("java-类外")
public class SuppressJava {

    /**
     * SuppressLint
     *
     * @return int
     * rea-son:
     */
    @SuppressLint({"java-testSuppressLint", "ParseStringDetector"})
    public int testSuppressLint() {
        return Integer.parseInt("");
    }

    @SuppressWarnings("java-testSuppressWarnings")
    public void testSuppressWarnings() {
        testSuppressLint();
    }

}
