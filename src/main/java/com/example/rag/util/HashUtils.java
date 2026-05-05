package com.example.rag.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 哈希工具类
 */
public class HashUtils {

    private HashUtils() {
        // 工具类不允许实例化
    }

    /**
     * 计算字符串的 SHA-256 哈希值
     *
     * @param text 输入文本
     * @return SHA-256 哈希值（十六进制字符串）
     */
    public static String sha256(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("输入文本不能为空");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));

            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("生成 hash 失败", e);
        }
    }

    /**
     * 计算字节数组的 SHA-256 哈希值
     *
     * @param bytes 输入字节数组
     * @return SHA-256 哈希值（十六进制字符串）
     */
    public static String sha256(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("输入字节数组不能为空");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);

            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("生成文件hash失败", e);
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
