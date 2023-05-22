package com.zsw.devopshsyq.annotation

import android.annotation.SuppressLint


//抑制-原因
//@SuppressLint("ParseStringDetector")
/**
 * SuppressKotlin测试类
 *
 */
class SuppressKotlin {
    //reason:
    @Suppress("kotlin-testSuppressInside")
    val name = "zhangsan"


    //抑制-原因

//    @SuppressLint("kotlin-testSuppressLint")
    /**
     * testSuppressLint
     *rea---son:
     * @return
     */
    @SuppressLint("ParseStringDetector")
    fun testSuppressLint(): Int {
        return "".toInt()
    }

    //rea--son:
    @SuppressWarnings("kotlin-testSuppressWarnings")
    fun testSuppressWarnings() {
        testSuppressLint()
    }

    //抑制-原因
    @Suppress("kotlin-testSuppressOut")
    fun testSuppressOut() {
    }

    //抑制-原因
    fun testSuppressInside() {
        @Suppress("kotlin-testSuppressInside")
        val a = "aaaaa"
    }
}