package com.xcheng.xclogger.util;

import android.os.Parcel;
import android.os.Parcelable;

public class XcLoggerConfigUpdateResult implements Parcelable {
    public static final int SUCCESS = 0;
    public static final int NO_CHANGES = 1;
    public static final int INVALID_ARGUMENT = 2;
    public static final int NOT_BOUND = 3;
    public static final int UNSUPPORTED_SERVICE_VERSION = 4;
    public static final int PERSIST_FAILED = 5;
    public static final int APPLIED_RESTART_FAILED = 6;
    public static final int REMOTE_ERROR = 7;
    public static final int TIMEOUT_PENDING = 8;
    public static final int INTERNAL_ERROR = 9;

    private String requestId;
    private int status;
    private String message;
    private String changedFields;
    private boolean serviceRestarted;

    public XcLoggerConfigUpdateResult() {
    }

    public XcLoggerConfigUpdateResult(String requestId, int status, String message,
                                      String changedFields, boolean serviceRestarted) {
        this.requestId = requestId;
        this.status = status;
        this.message = message;
        this.changedFields = changedFields;
        this.serviceRestarted = serviceRestarted;
    }

    protected XcLoggerConfigUpdateResult(Parcel in) {
        requestId = in.readString();
        status = in.readInt();
        message = in.readString();
        changedFields = in.readString();
        serviceRestarted = in.readByte() != 0;
    }

    public static final Creator<XcLoggerConfigUpdateResult> CREATOR = new Creator<XcLoggerConfigUpdateResult>() {
        @Override
        public XcLoggerConfigUpdateResult createFromParcel(Parcel in) {
            return new XcLoggerConfigUpdateResult(in);
        }

        @Override
        public XcLoggerConfigUpdateResult[] newArray(int size) {
            return new XcLoggerConfigUpdateResult[size];
        }
    };

    public boolean isSuccess() { return status == SUCCESS || status == NO_CHANGES; }
    public String getRequestId() { return requestId; }
    public int getStatus() { return status; }
    public String getMessage() { return message; }
    public String getChangedFields() { return changedFields; }
    public boolean isServiceRestarted() { return serviceRestarted; }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(requestId);
        dest.writeInt(status);
        dest.writeString(message);
        dest.writeString(changedFields);
        dest.writeByte((byte) (serviceRestarted ? 1 : 0));
    }
}
