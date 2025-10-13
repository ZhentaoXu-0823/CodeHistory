package com.xcheng.xclogger.recorder;

import com.xcheng.xclogger.processctr.ConfigLoader;
import com.xcheng.xclogger.util.XcLoggerConfig;

/**
 * LogBuffer - 日志缓冲区，管理内存中的日志数据
 *
 * 功能方法：
 * - LogBuffer() - 构造函数，初始化日志缓冲区
 * - append(byte[], int) - 添加字节数据到缓冲区
 * - flush() - 刷新缓冲区数据
 * - isFull() - 检查缓冲区是否已满
 * - getUsedLength() - 获取已使用长度
 * - setOnFlushListener(OnFlushListener) - 设置刷新监听器
 * - getMaxSize() - 获取缓冲区最大大小
 * - clear() - 清空缓冲区
 */
public class LogBuffer {
    // 缓冲区相关
    private byte[] buffer;
    private int currentSize;
    private int maxSize;
    private OnFlushListener flushListener;

    /**
     * 刷新监听器接口
     */
    public interface OnFlushListener {
        /**
         * 刷新时回调
         * @param data 要刷新的数据
         * @param len 数据长度
         */
        void onFlush(byte[] data, int len);
    }

    /**
     * 构造函数，初始化日志缓冲区
     */
    public LogBuffer() {
        XcLoggerConfig config = ConfigLoader.current();
        this.maxSize = (config != null) ? config.getBufferSizeBytes() : 4096; // 默认4096字节
        this.buffer = new byte[maxSize];
        this.currentSize = 0;
    }

    /**
     * 添加字节数据到缓冲区
     * @param data 字节数据
     * @param len 数据长度
     */
    public void append(byte[] data, int len) {
        if (data == null || len <= 0) return;

        // 如果单次数据超过缓冲区大小，直接刷新
        if (len > maxSize) {
            flush();
            if (flushListener != null) {
                flushListener.onFlush(data, len);
            }
            return;
        }

        // 检查添加后是否超过缓冲区大小
        if (currentSize + len > maxSize) {
            flush();
        }

        // 添加数据到缓冲区
        System.arraycopy(data, 0, buffer, currentSize, len);
        currentSize += len;
    }

    /**
     * 刷新缓冲区数据
     */
    public void flush() {
        if (currentSize > 0 && flushListener != null) {
            flushListener.onFlush(buffer, currentSize);
            currentSize = 0;
        }
    }

    /**
     * 检查缓冲区是否已满
     * @return 是否已满
     */
    public boolean isFull() {
        return currentSize >= maxSize;
    }

    /**
     * 获取已使用长度
     * @return 已使用字节数
     */
    public int getUsedLength() {
        return currentSize;
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
        currentSize = 0;
    }
}