package com.zsw.devopshsyq.parcelable

import android.os.Parcelable
import com.zsw.devopshsyq.MyReference

class LintParcelableKt : Parcelable {
    var name: String = ""
    var age: Int = 1
    var child: ParcelableChild = ParcelableChild()
    var reference: MyReference? = null


}