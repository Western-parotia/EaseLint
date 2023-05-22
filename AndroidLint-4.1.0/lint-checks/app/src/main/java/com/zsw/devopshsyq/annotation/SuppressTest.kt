//reason:
@file:Suppress("unused")

package com.zsw.devopshsyq.annotation

/**
 * @describe:
 * @author: ljj
 * @creattime: 2022/10/17 17:41
 *
 */
class SuppressTest {

    fun test() {

        val kotlin = SuppressKotlin()
        kotlin.testSuppressLint()
        kotlin.testSuppressWarnings()
        kotlin.testSuppressOut()
        kotlin.testSuppressInside()


        val java = SuppressJava()
        java.testSuppressLint()
        java.testSuppressWarnings()

    }
}