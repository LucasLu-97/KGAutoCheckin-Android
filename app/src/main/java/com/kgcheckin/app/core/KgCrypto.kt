package com.kgcheckin.app.core

import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 酷狗接口加密算法（移植自 kgcheckin2/api/util/crypto.js）
 * - AES-256-CBC：登录参数加密 / token 刷新
 * - RSA NoPadding：登录 pk / p 字段
 */
object KgCrypto {

    /** 酷狗 Android 客户端 RSA 公钥（X509 DER base64，原样移植自 crypto.js） */
    private val RSA_PUBLIC_KEY_B64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5" +
        "HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXng" +
        "peIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB"

    private val secureRandom = SecureRandom()
    private const val KEY_CHARS = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /** 随机 key（等价 JS randomString：大写字母+数字，调用处再 toLowerCase） */
    fun randomKey(len: Int = 16): String {
        val sb = StringBuilder(len)
        repeat(len) {
            val idx = secureRandom.nextInt(KEY_CHARS.length)
            sb.append(KEY_CHARS[idx])
        }
        return sb.toString()
    }

    /**
     * AES-256-CBC 加密（cryptoAesEncrypt 的 tempKey 模式）
     * key = md5Hex(tempKey)（32 字符），iv = key 的后 16 字符
     * @return (hex 密文, tempKey)
     */
    fun aesEncryptWithRandomKey(plain: String): Pair<String, String> {
        val tempKey = randomKey(16).lowercase()
        val encrypted = aesEncrypt(plain, tempKey)
        return encrypted to tempKey
    }

    /** AES-256-CBC 加密：由 tempKey 派生 key/iv（等价 cryptoAesEncrypt(data, {key: tempKey})） */
    fun aesEncrypt(plain: String, tempKey: String): String {
        val keyHex = KgSign.md5Hex(tempKey)
        val iv = keyHex.substring(keyHex.length - 16)
        return aesEncryptFixed(plain, keyHex, iv)
    }

    /** AES-256-CBC 加密（固定 key/iv 模式，如 login_token 的 p3 字段） */
    fun aesEncryptFixed(plain: String, keyHex: String, ivHex: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyHex.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(ivHex.toByteArray(Charsets.UTF_8))
        )
        val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return out.joinToString("") { "%02x".format(it) }
    }

    /** AES-256-CBC 解密（cryptoAesDecrypt：tempKey -> md5 派生 key/iv） */
    fun aesDecrypt(hex: String, tempKey: String): String {
        val keyHex = KgSign.md5Hex(tempKey)
        val iv = keyHex.substring(keyHex.length - 16)
        val data = ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyHex.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
        )
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    /**
     * RSA NoPadding 加密（等价 Node crypto.publicEncrypt RSA_NO_PADDING）
     * 数据右侧零填充至 128 字节后加密，输出 hex。
     */
    fun rsaEncryptNoPadding(data: String): String {
        val keyBytes = java.util.Base64.getDecoder().decode(RSA_PUBLIC_KEY_B64)
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(keyBytes))

        val input = data.toByteArray(Charsets.UTF_8)
        require(input.size <= 128) { "RSA plaintext too long" }
        val padded = ByteArray(128)
        System.arraycopy(input, 0, padded, 0, input.size)

        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val out = cipher.doFinal(padded)
        return out.joinToString("") { "%02x".format(it) }
    }
}
