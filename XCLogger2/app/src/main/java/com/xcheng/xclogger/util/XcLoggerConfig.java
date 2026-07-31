package com.xcheng.xclogger.util;

import android.os.Parcel;
import android.os.Parcelable;

public class XcLoggerConfig implements Parcelable {
	public static final String PACKAGE_FILTER_MODE_OFF = "off";
	public static final String PACKAGE_FILTER_MODE_WHITELIST = "whitelist";
	public static final String PACKAGE_FILTER_MODE_BLACKLIST = "blacklist";
	private int totalSizeMb;
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
	// Internal field; excluded from the legacy Parcelable layout for old AAR compatibility.
	private String packageFilterMode = PACKAGE_FILTER_MODE_OFF;

	public XcLoggerConfig() {}

	protected XcLoggerConfig(Parcel in) {
		totalSizeMb = in.readInt();
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
		dest.writeInt(totalSizeMb);
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

	// --- Getter 和 Setter (原有内容保持不变) ---
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
	public String getPackageFilterMode() {
		if (PACKAGE_FILTER_MODE_BLACKLIST.equals(packageFilterMode)) {
			return PACKAGE_FILTER_MODE_BLACKLIST;
		}
		if (PACKAGE_FILTER_MODE_WHITELIST.equals(packageFilterMode)) {
			return PACKAGE_FILTER_MODE_WHITELIST;
		}
		return PACKAGE_FILTER_MODE_OFF;
	}
	public void setPackageFilterMode(String value) {
		if (PACKAGE_FILTER_MODE_BLACKLIST.equalsIgnoreCase(value)) {
			packageFilterMode = PACKAGE_FILTER_MODE_BLACKLIST;
		} else if (PACKAGE_FILTER_MODE_WHITELIST.equalsIgnoreCase(value)) {
			packageFilterMode = PACKAGE_FILTER_MODE_WHITELIST;
		} else {
			packageFilterMode = PACKAGE_FILTER_MODE_OFF;
		}
	}
}
