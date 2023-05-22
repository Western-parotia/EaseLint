package com.zsw.devopshsyq.parcelable;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

public class LintParcelableJavaInnerClass implements Serializable {
    String name;

    Body body;

    class Body {

    }
}
