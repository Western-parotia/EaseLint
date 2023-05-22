package com.zsw.devopshsyq.parse

import android.graphics.Color

class ColorParse {

    val a: String? = null

    init {
        Color.parseColor("@@@@")

        try {
            Color.parseColor("@@@@")
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        try {
            Color.parseColor("@@@@")
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }

    }
}