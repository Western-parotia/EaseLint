package com.zsw.devopshsyq.parse

/**
 * @describe:
 * @author: ljj
 * @creattime: 2022/9/27 13:38
 *
 */
class ParseKotlin {
    fun test() {
        try {
            "".toInt()

        } catch (e: Exception) {

        }

        try {
            "".toLong()
        } catch (e: NumberFormatException) {

        }

        "".toFloat()
        "".toDouble()
    }
}