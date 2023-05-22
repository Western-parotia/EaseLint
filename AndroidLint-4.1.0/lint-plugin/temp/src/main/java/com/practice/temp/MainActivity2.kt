package com.practice.temp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        testLint()
    }

    fun testLint() {
        Log.e("Log.e", "testLint")
        Log.i("Log.i", "testLint")
    }
}