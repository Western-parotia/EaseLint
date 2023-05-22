package com.zsw.devopshsyq.parcelable

import java.io.Serializable

class LintSerializableKt<T> : Serializable {
    val name: String = ""
    val age: Int = 1
    val code: Char = 'A'
    var empty: Child? = null
    var reference: MyReference? = null
    var data: List<String>? = null

    var mt: MutableList<in T>? = null
    var ot: MutableList<out T>? = null
    var tt: List<Child>? = null
    var aat: ArrayList<T>? = null
    var aas: ArrayList<String>? = null
    var ttttttt: ArrayList<ArrayList<ArrayList<String>>>? = null
    var aaas: ArrayList<ArrayList<ArrayList<Child>>>? = null
    var sc: ArrayList<Child>? = null

}