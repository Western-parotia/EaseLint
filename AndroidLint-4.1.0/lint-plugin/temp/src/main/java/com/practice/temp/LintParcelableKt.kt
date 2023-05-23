package com.practice.temp

import android.os.Parcel
import android.os.Parcelable

class LintParcelableKt() : Parcelable {
    var name: String = ""
    var age: Int = 1
    var reference: Child? = null

    constructor(parcel: Parcel) : this() {
        name = parcel.readString()
        age = parcel.readInt()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeInt(age)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<LintParcelableKt> {
        override fun createFromParcel(parcel: Parcel): LintParcelableKt {
            return LintParcelableKt(parcel)
        }

        override fun newArray(size: Int): Array<LintParcelableKt?> {
            return arrayOfNulls(size)
        }
    }
}