package com.zsw.devopshsyq.parcelable;

import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;

public class ParcelableChild implements Parcelable {

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

    }
}
