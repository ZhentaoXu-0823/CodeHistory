package com.xcheng.xclogger.util;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class XcLoggerConfigUpdate implements Parcelable {
    public static final long FIELD_TOTAL_SIZE_MB = 1L;
    public static final long FIELD_FILE_SIZE_MB = 1L << 1;
    public static final long FIELD_BUFFER_SIZE_BYTES = 1L << 2;
    public static final long FIELD_LOG_DIR = 1L << 3;
    public static final long FIELD_LOG_PERIOD_HOURS = 1L << 4;
    public static final long FIELD_FILTER_LEVEL = 1L << 5;

    private String requestId;
    private long presentFields;
    private int totalSizeMb;
    private int fileSizeMb;
    private int bufferSizeBytes;
    private String logDir;
    private int logPeriodHours;
    private String filterLevel;
    private ListMutation tagWhitelist;
    private ListMutation packageWhitelist;
    private ListMutation tagBlacklist;
    private ListMutation packageBlacklist;

    public XcLoggerConfigUpdate() {
    }

    protected XcLoggerConfigUpdate(Parcel in) {
        requestId = in.readString();
        presentFields = in.readLong();
        totalSizeMb = in.readInt();
        fileSizeMb = in.readInt();
        bufferSizeBytes = in.readInt();
        logDir = in.readString();
        logPeriodHours = in.readInt();
        filterLevel = in.readString();
        tagWhitelist = in.readParcelable(ListMutation.class.getClassLoader());
        packageWhitelist = in.readParcelable(ListMutation.class.getClassLoader());
        tagBlacklist = in.readParcelable(ListMutation.class.getClassLoader());
        packageBlacklist = in.readParcelable(ListMutation.class.getClassLoader());
    }

    public static final Creator<XcLoggerConfigUpdate> CREATOR = new Creator<XcLoggerConfigUpdate>() {
        @Override
        public XcLoggerConfigUpdate createFromParcel(Parcel in) {
            return new XcLoggerConfigUpdate(in);
        }

        @Override
        public XcLoggerConfigUpdate[] newArray(int size) {
            return new XcLoggerConfigUpdate[size];
        }
    };

    public boolean hasField(long field) {
        return (presentFields & field) != 0;
    }

    public boolean hasChanges() {
        return presentFields != 0
                || hasMutation(tagWhitelist)
                || hasMutation(packageWhitelist)
                || hasMutation(tagBlacklist)
                || hasMutation(packageBlacklist);
    }

    private static boolean hasMutation(ListMutation mutation) {
        return mutation != null && mutation.hasOperations();
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public long getPresentFields() { return presentFields; }
    public void setPresentFields(long presentFields) { this.presentFields = presentFields; }
    public int getTotalSizeMb() { return totalSizeMb; }
    public void setTotalSizeMb(int totalSizeMb) { this.totalSizeMb = totalSizeMb; }
    public int getFileSizeMb() { return fileSizeMb; }
    public void setFileSizeMb(int fileSizeMb) { this.fileSizeMb = fileSizeMb; }
    public int getBufferSizeBytes() { return bufferSizeBytes; }
    public void setBufferSizeBytes(int bufferSizeBytes) { this.bufferSizeBytes = bufferSizeBytes; }
    public String getLogDir() { return logDir; }
    public void setLogDir(String logDir) { this.logDir = logDir; }
    public int getLogPeriodHours() { return logPeriodHours; }
    public void setLogPeriodHours(int logPeriodHours) { this.logPeriodHours = logPeriodHours; }
    public String getFilterLevel() { return filterLevel; }
    public void setFilterLevel(String filterLevel) { this.filterLevel = filterLevel; }
    public ListMutation getTagWhitelist() { return tagWhitelist; }
    public void setTagWhitelist(ListMutation tagWhitelist) { this.tagWhitelist = tagWhitelist; }
    public ListMutation getPackageWhitelist() { return packageWhitelist; }
    public void setPackageWhitelist(ListMutation packageWhitelist) { this.packageWhitelist = packageWhitelist; }
    public ListMutation getTagBlacklist() { return tagBlacklist; }
    public void setTagBlacklist(ListMutation tagBlacklist) { this.tagBlacklist = tagBlacklist; }
    public ListMutation getPackageBlacklist() { return packageBlacklist; }
    public void setPackageBlacklist(ListMutation packageBlacklist) { this.packageBlacklist = packageBlacklist; }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(requestId);
        dest.writeLong(presentFields);
        dest.writeInt(totalSizeMb);
        dest.writeInt(fileSizeMb);
        dest.writeInt(bufferSizeBytes);
        dest.writeString(logDir);
        dest.writeInt(logPeriodHours);
        dest.writeString(filterLevel);
        dest.writeParcelable(tagWhitelist, flags);
        dest.writeParcelable(packageWhitelist, flags);
        dest.writeParcelable(tagBlacklist, flags);
        dest.writeParcelable(packageBlacklist, flags);
    }

    public static class ListMutation implements Parcelable {
        private final boolean replace;
        private final ArrayList<String> replacement;
        private final ArrayList<String> additions;
        private final ArrayList<String> removals;

        public ListMutation(boolean replace, List<String> replacement,
                            List<String> additions, List<String> removals) {
            this.replace = replace;
            this.replacement = copy(replacement);
            this.additions = copy(additions);
            this.removals = copy(removals);
        }

        protected ListMutation(Parcel in) {
            replace = in.readByte() != 0;
            replacement = readList(in);
            additions = readList(in);
            removals = readList(in);
        }

        public static final Creator<ListMutation> CREATOR = new Creator<ListMutation>() {
            @Override
            public ListMutation createFromParcel(Parcel in) { return new ListMutation(in); }
            @Override
            public ListMutation[] newArray(int size) { return new ListMutation[size]; }
        };

        private static ArrayList<String> copy(List<String> source) {
            return source == null ? new ArrayList<>() : new ArrayList<>(source);
        }

        private static ArrayList<String> readList(Parcel in) {
            ArrayList<String> values = in.createStringArrayList();
            return values == null ? new ArrayList<>() : values;
        }

        public boolean isReplace() { return replace; }
        public List<String> getReplacement() { return Collections.unmodifiableList(replacement); }
        public List<String> getAdditions() { return Collections.unmodifiableList(additions); }
        public List<String> getRemovals() { return Collections.unmodifiableList(removals); }
        public boolean hasOperations() {
            return replace || !additions.isEmpty() || !removals.isEmpty();
        }

        @Override
        public int describeContents() { return 0; }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeByte((byte) (replace ? 1 : 0));
            dest.writeStringList(replacement);
            dest.writeStringList(additions);
            dest.writeStringList(removals);
        }
    }
}
