package com.kgcheckin.app.core

import java.security.MessageDigest

/**
 * 酷狗 API 签名算法（移植自 kgcheckin2/api/util/helper.js）
 *
 * 三种签名方式：
 * - Android: MD5(secret + sorted("k=v") + data + secret)
 * - Web:     MD5(secret + sorted("k=v") + secret)
 * - Register: MD5("1014" + sorted(values) + "1014")
 */
object KgSign {

    private const val ANDROID_SECRET = "OIlwieks28dk2k092lksi2UIkp"
    private const val WEB_SECRET = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
    private const val REGISTER_SECRET = "1014"

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Android 版签名（多数接口）
     * JS: Object.keys(params).sort().map(k => `${k}=${params[k]}`).join('') —— 按 key 排序
     */
    fun signAndroid(params: Map<String, String>, data: String): String {
        val paramsString = params.keys.sorted()
            .joinToString("") { key -> "$key=${params[key]}" }
        return md5Hex(ANDROID_SECRET + paramsString + data + ANDROID_SECRET)
    }

    /**
     * Web 版签名（二维码登录接口）
     * JS: Object.keys(params).map(k => `${k}=${params[k]}`).sort().join('') —— 对 "k=v" 字符串排序
     */
    fun signWeb(params: Map<String, String>): String {
        val paramsString = params.keys
            .map { key -> "$key=${params[key]}" }
            .sorted()
            .joinToString("")
        return md5Hex(WEB_SECRET + paramsString + WEB_SECRET)
    }

    /**
     * Register 版签名（设备注册接口）
     * JS: Object.keys(params).map(k => params[k]).sort().join('')
     */
    fun signRegister(params: Map<String, String>): String {
        val paramsString = params.values.sorted().joinToString("")
        return md5Hex(REGISTER_SECRET + paramsString + REGISTER_SECRET)
    }

    /**
     * signParamsKey：登录验证码接口的 key 字段
     * JS: md5(`${appid}${str}${clientver}${data}`)
     */
    fun signParamsKey(dataTime: Long): String =
        md5Hex(KgConfig.APPID + ANDROID_SECRET + KgConfig.CLIENTVER + dataTime)
}
