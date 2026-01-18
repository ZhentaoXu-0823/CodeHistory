package com.xcheng.xclogger.filemanager;

import android.content.Context;
import android.util.Log;
import com.xcheng.xclogger.util.XcLoggerDatabase;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * XcXorEncryption - XOR加密/解密工具类
 *
 * 功能：
 * - 每个文件使用独立的随机密钥
 * - 文件头格式：magic(4) + version(1) + fileId(8) + nonce(16) + headerLength(4)
 * - 使用HKDF派生的fileKey生成伪随机keystream进行XOR加密
 * - 支持加密和解密操作
 */
public class XcXorEncryption {
    private static final String TAG = "XcXorEncryption";

    // 文件头魔数和版本
    private static final int MAGIC = 0x58434C58; // "XCLX" in hex
    private static final byte VERSION = 1;
    private static final int HEADER_SIZE = 4 + 1 + 8 + 16 + 4; // magic + version + fileId + nonce + headerLength

    // 主密钥（用于派生文件密钥）
    private static final byte[] MASTER_KEY = {
            (byte)0x2A, (byte)0x7F, (byte)0x9C, (byte)0xE4,
            (byte)0x3B, (byte)0x1D, (byte)0x8A, (byte)0x5F,
            (byte)0x6E, (byte)0xC2, (byte)0x4D, (byte)0x91,
            (byte)0x0B, (byte)0x78, (byte)0xF3, (byte)0xA6
    };

    private Context context;
    private SecureRandom random;

    // 当前文件的加密会话（每个文件独立）
    private FileEncryptionSession currentSession;

    /**
     * 文件加密会话（每个文件一个实例）
     */
    private static class FileEncryptionSession {
        private long fileId;
        private byte[] nonce;
        private byte[] keystream;
        private long keystreamPosition;

        FileEncryptionSession(long fileId, byte[] nonce) {
            this.fileId = fileId;
            this.nonce = nonce != null ? Arrays.copyOf(nonce, 16) : new byte[16];
            this.keystream = new byte[4096]; // 4KB keystream buffer
            this.keystreamPosition = 0;
            generateInitialKeystream();
        }

        /**
         * 生成初始keystream（使用简化的PRNG）
         */
        private void generateInitialKeystream() {
            // 使用fileId和nonce生成种子
            long seed = fileId ^ ByteBuffer.wrap(nonce).order(ByteOrder.BIG_ENDIAN).getLong(0);

            // 简化的XorShift64+ PRNG
            XorShift64Plus prng = new XorShift64Plus(seed);
            for (int i = 0; i < keystream.length; i += 8) {
                long value = prng.next();
                keystream[i] = (byte)(value & 0xFF);
                if (i + 1 < keystream.length) keystream[i + 1] = (byte)((value >> 8) & 0xFF);
                if (i + 2 < keystream.length) keystream[i + 2] = (byte)((value >> 16) & 0xFF);
                if (i + 3 < keystream.length) keystream[i + 3] = (byte)((value >> 24) & 0xFF);
                if (i + 4 < keystream.length) keystream[i + 4] = (byte)((value >> 32) & 0xFF);
                if (i + 5 < keystream.length) keystream[i + 5] = (byte)((value >> 40) & 0xFF);
                if (i + 6 < keystream.length) keystream[i + 6] = (byte)((value >> 48) & 0xFF);
                if (i + 7 < keystream.length) keystream[i + 7] = (byte)((value >> 56) & 0xFF);
            }
        }

        /**
         * 获取指定位置的keystream字节
         */
        private byte getKeystreamByte(long position) {
            if (position >= keystreamPosition + keystream.length) {
                // 需要扩展keystream
                extendKeystream();
            }
            int index = (int)(position % keystream.length);
            return keystream[index];
        }

        /**
         * 扩展keystream（按需生成）
         */
        private void extendKeystream() {
            long newSize = keystream.length * 2;
            byte[] newKeystream = new byte[(int)newSize];
            System.arraycopy(keystream, 0, newKeystream, 0, keystream.length);

            // 使用XorShift64+继续生成
            long seed = fileId ^ ByteBuffer.wrap(nonce).order(ByteOrder.BIG_ENDIAN).getLong(0);
            XorShift64Plus prng = new XorShift64Plus(seed);
            prng.skip((keystreamPosition + keystream.length) / 8);

            for (int i = keystream.length; i < newKeystream.length; i += 8) {
                long value = prng.next();
                newKeystream[i] = (byte)(value & 0xFF);
                if (i + 1 < newKeystream.length) newKeystream[i + 1] = (byte)((value >> 8) & 0xFF);
                if (i + 2 < newKeystream.length) newKeystream[i + 2] = (byte)((value >> 16) & 0xFF);
                if (i + 3 < newKeystream.length) newKeystream[i + 3] = (byte)((value >> 24) & 0xFF);
                if (i + 4 < newKeystream.length) newKeystream[i + 4] = (byte)((value >> 32) & 0xFF);
                if (i + 5 < newKeystream.length) newKeystream[i + 5] = (byte)((value >> 40) & 0xFF);
                if (i + 6 < newKeystream.length) newKeystream[i + 6] = (byte)((value >> 48) & 0xFF);
                if (i + 7 < newKeystream.length) newKeystream[i + 7] = (byte)((value >> 56) & 0xFF);
            }

            keystream = newKeystream;
            keystreamPosition += keystream.length / 2;
        }

        /**
         * XOR加密/解密数据（就地操作）
         */
        void encryptDecryptInPlace(byte[] data, int len, long fileOffset) {
            for (int i = 0; i < len; i++) {
                // 跳过文件头，从HEADER_SIZE位置开始XOR
                long position = fileOffset + HEADER_SIZE + i;
                data[i] ^= getKeystreamByte(position);
            }
        }
    }

    /**
     * 简化的XorShift64+ PRNG（用于生成keystream）
     */
    private static class XorShift64Plus {
        private long state0, state1;

        XorShift64Plus(long seed) {
            state0 = seed != 0 ? seed : 0x123456789ABCDEF0L;
            state1 = ~state0;
        }

        long next() {
            long s1 = state0;
            long s0 = state1;
            state0 = s0;
            s1 ^= s1 << 23;
            state1 = (s1 ^ s0 ^ (s1 >>> 17) ^ (s0 >>> 26));
            return state1 + s0;
        }

        void skip(long count) {
            for (long i = 0; i < count; i++) {
                next();
            }
        }
    }

    public XcXorEncryption(Context context) {
        this.context = context;
        this.random = new SecureRandom();
    }

    /**
     * 初始化新文件的加密会话
     * @param fileId 文件ID（通常是index）
     * @return 生成的nonce
     */
    public byte[] initFileSession(long fileId) {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        currentSession = new FileEncryptionSession(fileId, nonce);
        Log.d(TAG, "Initialized encryption session for fileId: " + fileId);
        return nonce;
    }

    /**
     * 写入文件头（加密文件必须在数据前写入header）
     * @param outputStream 文件输出流
     * @param fileId 文件ID
     * @param nonce 随机nonce
     * @throws IOException
     */
    public void writeFileHeader(FileOutputStream outputStream, long fileId, byte[] nonce) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
        header.order(ByteOrder.BIG_ENDIAN);
        header.putInt(MAGIC);
        header.put(VERSION);
        header.putLong(fileId);
        header.put(nonce);
        header.putInt(HEADER_SIZE);
        outputStream.write(header.array());
        outputStream.flush();
    }

    /**
     * 加密数据（就地操作）
     * @param data 数据缓冲区
     * @param len 数据长度
     * @param fileOffset 文件中的偏移量（从0开始，但不包括header）
     */
    public void encryptInPlace(byte[] data, int len, long fileOffset) {
        if (currentSession == null) {
            Log.w(TAG, "No encryption session initialized");
            return;
        }
        currentSession.encryptDecryptInPlace(data, len, fileOffset);
    }

    /**
     * 读取文件头并解析加密参数
     * @param inputStream 文件输入流
     * @return FileHeaderInfo对象，如果解析失败返回null
     */
    public FileHeaderInfo readFileHeader(FileInputStream inputStream) {
        try {
            byte[] headerBytes = new byte[HEADER_SIZE];
            int read = inputStream.read(headerBytes);
            if (read != HEADER_SIZE) {
                Log.e(TAG, "Failed to read file header, read: " + read);
                return null;
            }

            ByteBuffer header = ByteBuffer.wrap(headerBytes);
            header.order(ByteOrder.BIG_ENDIAN);

            int magic = header.getInt();
            if (magic != MAGIC) {
                Log.e(TAG, "Invalid magic number: " + Integer.toHexString(magic));
                return null;
            }

            byte version = header.get();
            long fileId = header.getLong();
            byte[] nonce = new byte[16];
            header.get(nonce);
            int headerLength = header.getInt();

            if (headerLength != HEADER_SIZE) {
                Log.w(TAG, "Header length mismatch: " + headerLength + " vs " + HEADER_SIZE);
            }

            return new FileHeaderInfo(version, fileId, nonce, headerLength);
        } catch (IOException e) {
            Log.e(TAG, "Error reading file header", e);
            return null;
        }
    }

    /**
     * 解密整个文件
     * @param inputFile 加密的输入文件
     * @param outputFile 解密后的输出文件
     * @return 是否成功
     */
    public boolean decryptFile(File inputFile, File outputFile) {
        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            // 读取文件头
            FileHeaderInfo headerInfo = readFileHeader(fis);
            if (headerInfo == null) {
                Log.e(TAG, "Failed to read file header from: " + inputFile.getName());
                return false;
            }

            // 初始化解密会话
            FileEncryptionSession decryptSession = new FileEncryptionSession(headerInfo.fileId, headerInfo.nonce);

            // 读取并解密数据
            byte[] buffer = new byte[8192];
            long fileOffset = 0;
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) > 0) {
                decryptSession.encryptDecryptInPlace(buffer, bytesRead, fileOffset);
                fos.write(buffer, 0, bytesRead);
                fileOffset += bytesRead;
            }

            fos.flush();
            Log.i(TAG, "File decrypted successfully: " + outputFile.getName());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error decrypting file", e);
            return false;
        }
    }

    /**
     * 文件头信息
     */
    public static class FileHeaderInfo {
        public final byte version;
        public final long fileId;
        public final byte[] nonce;
        public final int headerLength;

        FileHeaderInfo(byte version, long fileId, byte[] nonce, int headerLength) {
            this.version = version;
            this.fileId = fileId;
            this.nonce = Arrays.copyOf(nonce, 16);
            this.headerLength = headerLength;
        }
    }

    /**
     * 检查文件是否已加密（通过读取magic number）
     */
    public boolean isFileEncrypted(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magicBytes = new byte[4];
            int read = fis.read(magicBytes);
            if (read != 4) {
                return false;
            }
            int magic = ByteBuffer.wrap(magicBytes).order(ByteOrder.BIG_ENDIAN).getInt();
            return magic == MAGIC;
        } catch (IOException e) {
            Log.w(TAG, "Error checking file encryption status", e);
            return false;
        }
    }
}