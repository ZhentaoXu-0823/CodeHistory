package com.xcheng.xclogger.util;

import android.os.Parcel;
import android.os.Parcelable;

public class XcLoggerConfig implements Parcelable {
	private int totalSizeGb;
	private int fileSizeMb;
	private int bufferSizeBytes;
	private String logDir;
	private int logPeriodHours;
	private String filterTag;
	private String filterLevel;
	private String filterPackage;

	public XcLoggerConfig() {}

	protected XcLoggerConfig(Parcel in) {
		totalSizeGb = in.readInt();
		fileSizeMb = in.readInt();
		bufferSizeBytes = in.readInt();
		logDir = in.readString();
		logPeriodHours = in.readInt();
		filterTag = in.readString();
		filterLevel = in.readString();
		filterPackage = in.readString();
	}

	public static final Creator<XcLoggerConfig> CREATOR = new Creator<XcLoggerConfig>() {
		@Override
		public XcLoggerConfig createFromParcel(Parcel in) { return new XcLoggerConfig(in); }
		@Override
		public XcLoggerConfig[] newArray(int size) { return new XcLoggerConfig[size]; }
	};

	@Override
	public int describeContents() { return 0; }

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(totalSizeGb);
		dest.writeInt(fileSizeMb);
		dest.writeInt(bufferSizeBytes);
		dest.writeString(logDir);
		dest.writeInt(logPeriodHours);
		dest.writeString(filterTag);
		dest.writeString(filterLevel);
		dest.writeString(filterPackage);
	}

	// --- Getter 和 Setter (原有内容保持不变) ---
	public int getTotalSizeGb() { return totalSizeGb; }
	public void setTotalSizeGb(int totalSizeGb) { this.totalSizeGb = totalSizeGb; }
	public int getFileSizeMb() { return fileSizeMb; }
	public void setFileSizeMb(int fileSizeMb) { this.fileSizeMb = fileSizeMb; }
	public int getBufferSizeBytes() { return bufferSizeBytes; }
	public void setBufferSizeBytes(int bufferSizeBytes) { this.bufferSizeBytes = bufferSizeBytes; }
	public String getLogDir() { return logDir; }
	public void setLogDir(String logDir) { this.logDir = logDir; }
	public int getLogPeriodHours() { return logPeriodHours; }
	public void setLogPeriodHours(int logPeriodHours) { this.logPeriodHours = logPeriodHours; }
	public String getFilterTag() { return filterTag; }
	public void setFilterTag(String filterTag) { this.filterTag = filterTag; }
	public String getFilterLevel() { return filterLevel; }
	public void setFilterLevel(String filterLevel) { this.filterLevel = filterLevel; }
	public String getFilterPackage() { return filterPackage; }
	public void setFilterPackage(String filterPackage) { this.filterPackage = filterPackage; }
}