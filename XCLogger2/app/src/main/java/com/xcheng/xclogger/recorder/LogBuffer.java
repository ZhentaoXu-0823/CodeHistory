package com.xcheng.xclogger.recorder;

import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.util.XcLoggerConfig;

/**
 * LogBuffer - 日志缓冲区，管理内存中的日志数据
 *
 * 功能方法：
 * - LogBuffer() - 构造函数，初始化日志缓冲区
 * - addLogLine(String) - 添加日志行到缓冲区
 * - flush() - 刷新缓冲区数据
 * - isFull() - 检查缓冲区是否已满
 * - getUsedLength() - 获取已使用长度
 * - setOnFlushListener(OnFlushListener) - 设置刷新监听器
 * - getMaxSize() - 获取缓冲区最大大小
 * - clear() - 清空缓冲区
 */
public class LogBuffer {
    private StringBuilder buffer;
    private int maxSize;
    private OnFlushListener flushListener;

    /**
     * 刷新监听器接口
     */
    public interface OnFlushListener {
        /**
         * 刷新时回调
         * @param data 要刷新的数据
         */
        void onFlush(String data);
    }

    /**
     * 构造函数，初始化日志缓冲区
     */
    public LogBuffer() {
        XcLoggerConfig config = ConfigLoader.current();
        this.maxSize = (config != null) ? config.getBufferSizeBytes() : 4096;
        this.buffer = new StringBuilder();
    }

    /**
     * 添加日志行到缓冲区
     * @param line 日志行
     */
    public void addLogLine(String line) {
        if (line == null) return;

        // 如果单行超过缓冲区大小，直接刷新
        if (line.length() > maxSize) {
            flush();
            if (flushListener != null) {
                flushListener.onFlush(line + "\n");
            }
            return;
        }

        // 检查添加后是否超过缓冲区大小
        if (buffer.length() + line.length() + 1 > maxSize) {
            flush();
        }

        buffer.append(line).append("\n");
    }

    /**
     * 刷新缓冲区数据
     */
    public void flush() {
        if (buffer.length() > 0 && flushListener != null) {
            flushListener.onFlush(buffer.toString());
            buffer.setLength(0);
        }
    }

    /**
     * 检查缓冲区是否已满
     * @return 是否已满
     */
    public boolean isFull() {
        return buffer.length() >= maxSize;
    }

    /**
     * 获取已使用长度
     * @return 已使用字节数
     */
    public int getUsedLength() {
        return buffer.length();
    }

    /**
     * 设置刷新监听器
     * @param listener 刷新监听器
     */
    public void setOnFlushListener(OnFlushListener listener) {
        this.flushListener = listener;
    }

    /**
     * 获取缓冲区最大大小
     * @return 最大大小（字节）
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * 清空缓冲区
     */
    public void clear() {
        buffer.setLength(0);
    }
}