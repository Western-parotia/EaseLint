package com.zsw.devopshsyq.parcelable;

import android.os.Parcel;
import android.os.Parcelable;

public class LintParcelableJava implements Parcelable {
    String name;
    int age;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

    }
}
