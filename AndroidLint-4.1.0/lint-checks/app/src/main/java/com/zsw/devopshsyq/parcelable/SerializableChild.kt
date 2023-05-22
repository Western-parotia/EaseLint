package com.zsw.devopshsyq.parcelable

import android.os.Parcel
import android.os.Parcelable
import java.io.Serializable

class SerializableChild : Serializable, Parcelable {
    var name: String = "SerializableChild"
    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
    }
}