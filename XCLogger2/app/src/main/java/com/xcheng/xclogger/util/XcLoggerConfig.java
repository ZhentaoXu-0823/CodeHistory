package com.xcheng.xclogger.util;

/**
 * XcLoggerConfig - 配置数据模型，存储所有配置参数
 *
 * 功能方法：
 * - getTotalSizeGb() - 获取总大小限制（GB）
 * - setTotalSizeGb(int) - 设置总大小限制（GB）
 * - getFileSizeMb() - 获取单文件大小限制（MB）
 * - setFileSizeMb(int) - 设置单文件大小限制（MB）
 * - getBufferSizeBytes() - 获取缓冲区大小（字节）
 * - setBufferSizeBytes(int) - 设置缓冲区大小（字节）
 * - getLogDir() - 获取日志存储目录
 * - setLogDir(String) - 设置日志存储目录
 * - getLogPeriodHours() - 获取日志保存周期（小时）
 * - setLogPeriodHours(int) - 设置日志保存周期（小时）
 * - getFilterTag() - 获取过滤标签
 * - setFilterTag(String) - 设置过滤标签
 * - getFilterLevel() - 获取过滤级别
 * - setFilterLevel(String) - 设置过滤级别
 * - getFilterPackage() - 获取过滤包名
 * - setFilterPackage(String) - 设置过滤包名
 */
public class XcLoggerConfig {
	// 配置参数
	private int totalSizeGb;
	private int fileSizeMb;
	private int bufferSizeBytes;
	private String logDir;
	private int logPeriodHours;
	private String filterTag;
	private String filterLevel;
	private String filterPackage;

	/**
	 * 获取总大小限制（GB）
	 * @return 总大小限制
	 */
	public int getTotalSizeGb() { return totalSizeGb; }

	/**
	 * 设置总大小限制（GB）
	 * @param totalSizeGb 总大小限制
	 */
	public void setTotalSizeGb(int totalSizeGb) { this.totalSizeGb = totalSizeGb; }

	/**
	 * 获取单文件大小限制（MB）
	 * @return 单文件大小限制
	 */
	public int getFileSizeMb() { return fileSizeMb; }

	/**
	 * 设置单文件大小限制（MB）
	 * @param fileSizeMb 单文件大小限制
	 */
	public void setFileSizeMb(int fileSizeMb) { this.fileSizeMb = fileSizeMb; }

	/**
	 * 获取缓冲区大小（字节）
	 * @return 缓冲区大小
	 */
	public int getBufferSizeBytes() { return bufferSizeBytes; }

	/**
	 * 设置缓冲区大小（字节）
	 * @param bufferSizeBytes 缓冲区大小
	 */
	public void setBufferSizeBytes(int bufferSizeBytes) { this.bufferSizeBytes = bufferSizeBytes; }

	/**
	 * 获取日志存储目录
	 * @return 日志存储目录
	 */
	public String getLogDir() { return logDir; }

	/**
	 * 设置日志存储目录
	 * @param logDir 日志存储目录
	 */
	public void setLogDir(String logDir) { this.logDir = logDir; }

	/**
	 * 获取日志保存周期（小时）
	 * @return 日志保存周期
	 */
	public int getLogPeriodHours() { return logPeriodHours; }

	/**
	 * 设置日志保存周期（小时）
	 * @param logPeriodHours 日志保存周期
	 */
	public void setLogPeriodHours(int logPeriodHours) { this.logPeriodHours = logPeriodHours; }

	/**
	 * 获取过滤标签
	 * @return 过滤标签
	 */
	public String getFilterTag() { return filterTag; }

	/**
	 * 设置过滤标签
	 * @param filterTag 过滤标签
	 */
	public void setFilterTag(String filterTag) { this.filterTag = filterTag; }

	/**
	 * 获取过滤级别
	 * @return 过滤级别
	 */
	public String getFilterLevel() { return filterLevel; }

	/**
	 * 设置过滤级别
	 * @param filterLevel 过滤级别
	 */
	public void setFilterLevel(String filterLevel) { this.filterLevel = filterLevel; }

	/**
	 * 获取过滤包名
	 * @return 过滤包名
	 */
	public String getFilterPackage() { return filterPackage; }

	/**
	 * 设置过滤包名
	 * @param filterPackage 过滤包名
	 */
	public void setFilterPackage(String filterPackage) { this.filterPackage = filterPackage; }
}