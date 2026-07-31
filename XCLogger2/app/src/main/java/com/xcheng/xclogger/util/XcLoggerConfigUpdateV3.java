package com.xcheng.xclogger.util;

import android.os.Parcel;
import android.os.Parcelable;

/** API 3 configuration envelope. Keeps the API 2 Parcelable byte layout unchanged. */
public final class XcLoggerConfigUpdateV3 implements Parcelable {
    private XcLoggerConfigUpdate baseUpdate;
    private String packageFilterMode;

    public XcLoggerConfigUpdateV3() {
    }

    private XcLoggerConfigUpdateV3(Parcel in) {
        baseUpdate = in.readParcelable(XcLoggerConfigUpdate.class.getClassLoader());
        packageFilterMode = in.readString();
    }

    public XcLoggerConfigUpdate getBaseUpdate() { return baseUpdate; }
    public void setBaseUpdate(XcLoggerConfigUpdate value) { baseUpdate = value; }
    public String getPackageFilterMode() { return packageFilterMode; }
    public void setPackageFilterMode(String value) { packageFilterMode = value; }
    public boolean hasPackageFilterMode() { return packageFilterMode != null; }
    public boolean hasChanges() {
        return (baseUpdate != null && baseUpdate.hasChanges()) || hasPackageFilterMode();
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(baseUpdate, flags);
        dest.writeString(packageFilterMode);
    }

    public static final Creator<XcLoggerConfigUpdateV3> CREATOR =
            new Creator<XcLoggerConfigUpdateV3>() {
                @Override public XcLoggerConfigUpdateV3 createFromParcel(Parcel in) {
                    return new XcLoggerConfigUpdateV3(in);
                }
                @Override public XcLoggerConfigUpdateV3[] newArray(int size) {
                    return new XcLoggerConfigUpdateV3[size];
                }
            };
}
