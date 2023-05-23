package com.zsw.devopshsyq.parcelable

import android.os.Parcel
import android.os.Parcelable

class LintParcelableKt : Parcelable {
    var name: String = ""
    var age: Int = 1
    var child: ParcelableChild = ParcelableChild()
    var reference: MyReference? = null
    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(p0: Parcel?, p1: Int) {
    }


}