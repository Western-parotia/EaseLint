package com.zsw.devopshsyq.parcelable;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class LintParcelableJava2<T> implements Parcelable {
    String name;
    int age;
    Integer height;
    MyReference reference;
    Child empty;
    SerializableChild serializableChild;

    static MyReference reference1;

    List<String> list = null;

    HashMap<String, MyReference> map2 = null;

    HashMap<String, Integer> map1 = null;

    LinkedHashMap<String, Object> lMap = null;
    ArrayDeque<Object> a = null;

    List<List<List<Object>>> l = null;
    List<T> t = null;


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name);

//        dest.writeParcelableList();
    }

    public void readFromParcel(Parcel source) {
        this.name = source.readString();
//        source.readParcelableList()
    }

    public LintParcelableJava2() {
    }

    protected LintParcelableJava2(Parcel in) {
        this.name = in.readString();
    }

    public static final Creator<LintParcelableJava2> CREATOR = new Creator<LintParcelableJava2>() {
        @Override
        public LintParcelableJava2 createFromParcel(Parcel source) {
            return new LintParcelableJava2(source);
        }

        @Override
        public LintParcelableJava2[] newArray(int size) {
            return new LintParcelableJava2[size];
        }
    };
}
