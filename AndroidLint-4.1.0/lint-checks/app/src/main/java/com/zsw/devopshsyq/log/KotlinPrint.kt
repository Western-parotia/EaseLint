package com.zsw.devopshsyq.log

import android.util.Log

class KotlinPrint {
    fun log() {
        //报告中，println 没检测出来
        Log.i("1","2")
        println("this is foo log")
    }
}