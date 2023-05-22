package com.zsw.devopshsyq.parcelable

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
class LintParcelableKt2() : Parcelable {
    var name: String = ""
    var age: Int = 1
    var child: ParcelableChild = ParcelableChild()
    var reference: MyReference? = null

    var map: HashMap<Int, String>? = null

    var ktList: List<List<Any>>? = null
    var ktMutableList: MutableList<MutableList<Any>>? = null

    var aaaaaaaaa: AAAAAAAA<Any>? = null

    companion object {
        val d = "1"
    }

    @Parcelize
    class AAAAAAAA<in T> : Parcelable {

    }
}