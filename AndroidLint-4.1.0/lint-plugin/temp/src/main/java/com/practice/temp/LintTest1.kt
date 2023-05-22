package com.practice.temp

import android.util.Log

class LintTest1 {
    fun log() {
        Log.i("", "11")
        //报告中，println 没检测出来
        println("this is foo log")
    }
}