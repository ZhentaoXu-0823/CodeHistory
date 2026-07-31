package com.xcheng.xclogger.util;

import android.os.Parcel;

/** Structured configuration update used by updateConfiguration2. */
public final class XcLoggerConfig2 extends XcLoggerConfigUpdate {
    public static final String PACKAGE_FILTER_MODE_OFF = "off";
    public static final String PACKAGE_FILTER_MODE_WHITELIST = "whitelist";
    public static final String PACKAGE_FILTER_MODE_BLACKLIST = "blacklist";

    private String packageFilterMode;

    public XcLoggerConfig2() {
    }

    private XcLoggerConfig2(Parcel in) {
        super(in);
        packageFilterMode = in.readString();
    }

    public String getPackageFilterMode() { return packageFilterMode; }
    public void setPackageFilterMode(String value) { packageFilterMode = value; }
    public boolean hasPackageFilterMode() { return packageFilterMode != null; }
    @Override
    public boolean hasChanges() {
        return super.hasChanges() || hasPackageFilterMode();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(packageFilterMode);
    }

    public static final Creator<XcLoggerConfig2> CREATOR =
            new Creator<XcLoggerConfig2>() {
                @Override public XcLoggerConfig2 createFromParcel(Parcel in) {
                    return new XcLoggerConfig2(in);
                }
                @Override public XcLoggerConfig2[] newArray(int size) {
                    return new XcLoggerConfig2[size];
                }
            };
}
