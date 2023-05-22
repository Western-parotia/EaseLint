package com.zsw.devopshsyq

import android.app.Activity
import android.os.Bundle
import com.google.gson.Gson

const val json = """{
"data":["1","2","3","4"]
}"""

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val deque: DequeTest = Gson().fromJson(json, DequeTest::class.java)
        val first = deque.data?.first()
        println("deque:$first")

    }

}

class DequeTest(val data: ArrayDeque<String>? = null)