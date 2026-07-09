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
	private String filterTagBlacklist;
	private String filterPackageBlacklist;
	private String filterLevelBlacklist;
	private String filterContent;
	private String filterContentBlacklist;

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
		filterTagBlacklist = in.readString();
		filterPackageBlacklist = in.readString();
		filterLevelBlacklist = in.readString();
		filterContent = in.readString();
		filterContentBlacklist = in.readString();
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
		dest.writeString(filterTagBlacklist);
		dest.writeString(filterPackageBlacklist);
		dest.writeString(filterLevelBlacklist);
		dest.writeString(filterContent);
		dest.writeString(filterContentBlacklist);
	}

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
	public String getFilterTagBlacklist() { return filterTagBlacklist; }
	public void setFilterTagBlacklist(String v) { this.filterTagBlacklist = v; }
	public String getFilterPackageBlacklist() { return filterPackageBlacklist; }
	public void setFilterPackageBlacklist(String v) { this.filterPackageBlacklist = v; }
	public String getFilterLevelBlacklist() { return filterLevelBlacklist; }
	public void setFilterLevelBlacklist(String v) { this.filterLevelBlacklist = v; }
	public String getFilterContent() { return filterContent; }
	public void setFilterContent(String v) { this.filterContent = v; }
	public String getFilterContentBlacklist() { return filterContentBlacklist; }
	public void setFilterContentBlacklist(String v) { this.filterContentBlacklist = v; }
}