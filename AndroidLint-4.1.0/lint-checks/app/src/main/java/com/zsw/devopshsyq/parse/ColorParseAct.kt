package com.zsw.devopshsyq.parse

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log

class ColorParseAct : Activity() {

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        Log.i("", "")

        Color.parseColor("123")
        Color2.parseColor("")
        Color2().parseColor2("")

    }
}

