package com.xcheng.xclogger.recorder;

import android.util.Log;
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
    private static final String TAG = "LogBuffer";

    // 缓冲区相关
    private byte[] buffer;
    private int currentSize;
    private int maxSize;
    private OnFlushListener flushListener;

    // 统计信息
    private long totalAppended = 0;
    private long totalFlushed = 0;

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
//        Log.d(TAG, "LogBuffer initialized with maxSize: " + maxSize + " bytes");
    }

    /**
     * 添加字节数据到缓冲区
     * @param data 字节数据
     * @param len 数据长度
     */
    public void append(byte[] data, int len) {
        if (data == null || len <= 0) {
            Log.w(TAG, "append called with null data or zero length");
            return;
        }

        totalAppended += len;
        
//        if (totalAppended == len) {
//            Log.i(TAG, "First data appended to buffer, bytes: " + len);
//        }

        // 如果单次数据超过缓冲区大小，直接刷新
        if (len > maxSize) {
//            Log.w(TAG, "Data size (" + len + ") exceeds buffer size (" + maxSize + "), flushing and writing directly");
            flush();
            if (flushListener != null) {
                flushListener.onFlush(data, len);
                totalFlushed += len;
            } else {
                Log.e(TAG, "flushListener is null, data will be lost! bytes: " + len);
            }
            return;
        }

        // 检查添加后是否超过缓冲区大小
        if (currentSize + len > maxSize) {
//            Log.d(TAG, "Buffer will overflow, flushing before append. currentSize: " + currentSize + ", new data: " + len);
            flush();
        }

        // 添加数据到缓冲区
        System.arraycopy(data, 0, buffer, currentSize, len);
        currentSize += len;
        
//        Log.d(TAG, "Data appended to buffer. currentSize: " + currentSize + "/" + maxSize + ", total appended: " + totalAppended);
    }

    /**
     * 刷新缓冲区数据
     */
    public void flush() {
        if (currentSize > 0) {
            if (flushListener != null) {
//                Log.d(TAG, "Flushing buffer, bytes: " + currentSize);
                flushListener.onFlush(buffer, currentSize);
                totalFlushed += currentSize;
//                Log.d(TAG, "Buffer flushed successfully. Total flushed: " + totalFlushed);
            } else {
                Log.e(TAG, "flushListener is null, buffer data will be lost! bytes: " + currentSize);
            }
            currentSize = 0;
        } else {
            Log.d(TAG, "flush called but buffer is empty");
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
        Log.d(TAG, "Flush listener set: " + (listener != null ? "not null" : "null"));
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

    /**
     * 获取统计信息
     * @return 统计信息字符串
     */
    public String getStatistics() {
        return "Total appended: " + totalAppended + " bytes, Total flushed: " + totalFlushed + " bytes, Current size: " + currentSize + " bytes";
    }
}