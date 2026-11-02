package com.kgcheckin.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

/** 账号凭证（等价脚本中的 cookie：token / userid / dfid） */
data class Cred(
    val token: String = "",
    val userid: String = "0",
    val dfid: String = ""
)

/** 签名方式 */
enum class SignType { ANDROID, WEB, REGISTER }

/**
 * 酷狗 API 客户端。
 *
 * 完整移植 kgcheckin2 项目的请求模型（api/util/request.js）：
 * - 默认参数：dfid / mid / uuid / appid / clientver / userid / clienttime [/ token]
 * - mid = md5(dfid) + md5(dfid)[0,7)；uuid = md5(dfid + mid)
 * - 三种签名方式按接口选择，签名追加到 query 参数
 * - 请求头携带 dfid / clienttime / mid / User-Agent
 * - 网络失败指数退避重试 3 次（等价脚本 utils.js send()）
 */
class KgApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    private data class Spec(
        val method: String,
        val baseUrl: String,
        val path: String,
        val extraParams: Map<String, String> = emptyMap(),
        val body: String? = null,
        val bodyMediaType: String = "application/json; charset=utf-8",
        val signType: SignType = SignType.ANDROID,
        val cred: Cred = Cred(),
        val headers: Map<String, String> = emptyMap(),
        val userAgent: String? = null,
    )

    /** 构建带签名的完整 URL + 发起请求，返回解析后的 JSON；网络失败重试 3 次（指数退避） */
    private suspend fun execute(spec: Spec): JsonObject = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return@withContext executeOnce(spec)
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) delay(1000L * (1 shl attempt))
            }
        }
        throw lastError!!
    }

    private fun executeOnce(spec: Spec): JsonObject {
        val cred = spec.cred
        val dfid = cred.dfid.ifEmpty { "-" }
        val md5Dfid = KgSign.md5Hex(dfid)
        val mid = md5Dfid + md5Dfid.substring(0, 7)
        val uuid = KgSign.md5Hex(dfid + mid)
        val clienttime = (System.currentTimeMillis() / 1000).toString()

        // 默认参数（extra 覆盖同名默认参数，等价 Object.assign）
        val params = LinkedHashMap<String, String>()
        params["dfid"] = dfid
        params["mid"] = mid
        params["uuid"] = uuid
        params["appid"] = KgConfig.APPID
        params["clientver"] = KgConfig.CLIENTVER
        params["userid"] = cred.userid.ifEmpty { "0" }
        params["clienttime"] = clienttime
        if (cred.token.isNotEmpty()) params["token"] = cred.token
        params.putAll(spec.extraParams)

        // 签名（POST 的 body 字符串参与 Android 签名）
        val signature = when (spec.signType) {
            SignType.ANDROID -> KgSign.signAndroid(params, spec.body ?: "")
            SignType.WEB -> KgSign.signWeb(params)
            SignType.REGISTER -> KgSign.signRegister(params)
        }
        params["signature"] = signature

        // URL
        val urlBuilder = (spec.baseUrl + spec.path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

        // 请求头（等价 request.js：dfid/clienttime/mid/UA + 额外头）
        val builder = Request.Builder().url(urlBuilder.build())
        builder.header("dfid", dfid)
        builder.header("clienttime", clienttime)
        builder.header("mid", mid)
        builder.header("User-Agent", spec.userAgent ?: KgConfig.UA_DEFAULT)
        spec.headers.forEach { (k, v) -> builder.header(k, v) }

        if (spec.method == "POST") {
            val mediaType = spec.bodyMediaType.toMediaType()
            builder.post((spec.body ?: "").toByteArray(Charsets.UTF_8).toRequestBody(mediaType))
        }

        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string() ?: "{}"
            if (!resp.isSuccessful) {
                throw KugouHttpException(resp.code, text)
            }
            return text.trim().let {
                if (it.isEmpty()) JsonObject(emptyMap()) else json.parseToJsonElement(it).jsonObject
            }
        }
    }

    class KugouHttpException(val code: Int, val body: String) : Exception("HTTP $code")

    // ══════════════════════════════ 登录相关 ══════════════════════════════

    /**
     * 获取扫码登录二维码 key（login_qr_key.js）
     * 返回 data.qrcode，二维码内容 = QR_LOGIN_URL_PREFIX + qrcode
     */
    suspend fun qrKey(): JsonObject = execute(
        Spec(
            method = "GET",
            baseUrl = KgConfig.LOGIN_USER_HTTPS,
            path = "/v2/qrcode",
            extraParams = mapOf(
                "appid" to "1001",
                "type" to "1",
                "plat" to "4",
                "qrcode_txt" to "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=${KgConfig.APPID}&",
                "srcappid" to KgConfig.SRCAPPID,
            ),
            signType = SignType.WEB,
        )
    )

    /**
     * 轮询扫码状态（login_qr_check.js）
     * data.status: 0=过期 1=待扫码 2=已扫待确认 4=登录成功（返回 token/userid）
     */
    suspend fun qrCheck(qrcode: String): JsonObject = execute(
        Spec(
            method = "GET",
            baseUrl = KgConfig.LOGIN_USER_HTTPS,
            path = "/v2/get_userinfo_qrcode",
            extraParams = mapOf(
                "plat" to "4",
                "appid" to KgConfig.APPID,
                "srcappid" to KgConfig.SRCAPPID,
                "qrcode" to qrcode,
            ),
            signType = SignType.WEB,
        )
    )

    /** 发送短信验证码（captcha_sent.js） */
    suspend fun sendMobileCode(mobile: String): JsonObject {
        val body = buildJsonObject {
            put("businessid", 5)
            put("mobile", mobile)
            put("plat", 3)
        }.toString()
        return execute(
            Spec(
                method = "POST",
                baseUrl = KgConfig.LOGIN_USER_HTTP,
                path = "/v7/send_mobile_code",
                body = body,
                signType = SignType.ANDROID,
            )
        )
    }

    /**
     * 手机验证码登录（login_cellphone.js，非 lite 分支）
     * 成功返回 data.token / data.userid
     */
    suspend fun loginByVerifyCode(mobile: String, code: String): JsonObject {
        val dateTime = System.currentTimeMillis()
        val plain = buildJsonObject {
            put("mobile", mobile)
            put("code", code)
        }.toString()
        val (encStr, tempKey) = KgCrypto.aesEncryptWithRandomKey(plain)
        val maskedMobile = mobile.substring(0, 2) + "*****" + mobile.substring(10, 11)
        val pkPlain = buildJsonObject {
            put("clienttime_ms", dateTime)
            put("key", tempKey)
        }.toString()
        val body = buildJsonObject {
            put("plat", 1)
            put("support_multi", 1)
            put("t1", 0)
            put("t2", 0)
            put("clienttime_ms", dateTime)
            put("mobile", maskedMobile)
            put("key", KgSign.signParamsKey(dateTime))
            put("t3", "MCwwLDAsMCwwLDAsMCwwLDA=")
            put("params", encStr)
            put("pk", KgCrypto.rsaEncryptNoPadding(pkPlain).uppercase())
        }.toString()
        return execute(
            Spec(
                method = "POST",
                baseUrl = KgConfig.GATEWAY,
                path = "/v7/login_by_verifycode",
                body = body,
                signType = SignType.ANDROID,
                headers = mapOf("x-router" to "login.user.kugou.com"),
            )
        )
    }

    /**
     * 刷新登录 token（login_token.js，非 lite 分支）
     * 成功返回新 data.token
     */
    suspend fun loginByToken(cred: Cred): JsonObject {
        val dateNow = System.currentTimeMillis()
        val p3Plain = buildJsonObject {
            put("clienttime", dateNow / 1000)
            put("token", cred.token)
        }.toString()
        val p3 = KgCrypto.aesEncryptFixed(
            p3Plain,
            KgConfig.TOKEN_REFRESH_KEY,
            KgConfig.TOKEN_REFRESH_IV,
        )
        val (encEmpty, emptyKey) = KgCrypto.aesEncryptWithRandomKey("{}")
        val pkPlain = buildJsonObject {
            put("clienttime_ms", dateNow)
            put("key", emptyKey)
        }.toString()
        val body = buildJsonObject {
            put("dfid", cred.dfid.ifEmpty { "-" })
            put("p3", p3)
            put("plat", 1)
            put("t1", 0)
            put("t2", 0)
            put("t3", "MCwwLDAsMCwwLDAsMCwwLDA=")
            put("pk", KgCrypto.rsaEncryptNoPadding(pkPlain))
            put("params", encEmpty)
            put("userid", cred.userid)
            put("clienttime_ms", dateNow)
        }.toString()
        return execute(
            Spec(
                method = "POST",
                baseUrl = KgConfig.LOGIN_USER_HTTP,
                path = "/v5/login_by_token",
                body = body,
                signType = SignType.ANDROID,
                cred = cred,
                headers = mapOf("x-router" to "login.user.kugou.com"),
            )
        )
    }

    // ══════════════════════════════ 账号信息 ══════════════════════════════

    /** 获取我的信息（user_detail.js）：data.nickname 用于校验 token 有效性 */
    suspend fun getMyInfo(cred: Cred): JsonObject {
        val nowSec = System.currentTimeMillis() / 1000
        val pPlain = buildJsonObject {
            put("token", cred.token)
            put("clienttime", nowSec)
        }.toString()
        val body = buildJsonObject {
            put("visit_time", nowSec)
            put("usertype", 1)
            put("p", KgCrypto.rsaEncryptNoPadding(pPlain).uppercase())
            put("userid", cred.userid.toLongOrNull() ?: 0L)
        }.toString()
        return execute(
            Spec(
                method = "POST",
                baseUrl = KgConfig.GATEWAY,
                path = "/v3/get_my_info",
                extraParams = mapOf("plat" to "1"),
                body = body,
                signType = SignType.ANDROID,
                cred = cred,
                headers = mapOf("x-router" to "usercenter.kugou.com"),
            )
        )
    }

    /**
     * 注册设备获取 dfid（register_dev.js）
     * 注意：module params 会覆盖默认参数（mid/uuid/userid 均为 "0"/""，等价脚本行为）
     */
    suspend fun registerDev(cred: Cred): JsonObject {
        val dataMap = """{"mid":"","uuid":"","appid":"1014","userid":"0"}"""
        return execute(
            Spec(
                method = "POST",
                baseUrl = KgConfig.USERSERVICE,
                path = "/risk/v1/r_register_dev",
                extraParams = mapOf(
                    "mid" to "",
                    "uuid" to "",
                    "appid" to "1014",
                    "userid" to "0",
                    "p.token" to "",
                    "platid" to "4",
                ),
                body = Base64.getEncoder().encodeToString(dataMap.toByteArray(Charsets.UTF_8)),
                signType = SignType.REGISTER,
                cred = cred,
            )
        )
    }

    /** 查询概念 VIP 详情（user_vip_detail.js）：data.busi_vip 数组 */
    suspend fun getUnionVip(cred: Cred): JsonObject = execute(
        Spec(
            method = "GET",
            baseUrl = KgConfig.KUGOU_VIP,
            path = "/v1/get_union_vip",
            extraParams = mapOf("busi_type" to "concept"),
            signType = SignType.ANDROID,
            cred = cred,
        )
    )

    /**
     * 查询 VIP 领取记录（youth_month_vip_record.js）：签到日历服务器同步数据源。
     * 上游模块无月份参数（默认当月）；month 传 "YYYY-MM" 尝试查询历史月份，
     * 服务器是否支持以实测为准，调用方需对解析出的日期做目标月过滤。
     */
    suspend fun getMonthVipRecord(cred: Cred, month: String? = null): JsonObject = execute(
        Spec(
            method = "GET",
            baseUrl = KgConfig.GATEWAY,
            path = "/youth/v1/activity/get_month_vip_record",
            extraParams = if (month != null) mapOf("month" to month) else emptyMap(),
            signType = SignType.ANDROID,
            cred = cred,
        )
    )

    // ══════════════════════════════ 签到领取 ══════════════════════════════

    /** 听歌上报领取（youth_listen_song.js） */
    suspend fun reportListenSong(cred: Cred): JsonObject {
        val body = buildJsonObject {
            put("mixsongid", KgConfig.MIX_SONG_ID)
        }.toString()
        return execute(
            Spec(
                method = "POST",
                baseUrl = KgConfig.GATEWAY,
                path = "/youth/v2/report/listen_song",
                extraParams = mapOf("clientver" to "10566"),
                body = body,
                signType = SignType.ANDROID,
                cred = cred,
                userAgent = KgConfig.UA_LISTEN_SONG,
            )
        )
    }

    /** 广告播放上报领取（youth_vip.js）：每次成功约得 2 小时 VIP，单日最多 8 次 */
    suspend fun adPlayReport(cred: Cred): JsonObject {
        val now = System.currentTimeMillis()
        val body = buildJsonObject {
            put("ad_id", KgConfig.AD_ID)
            put("play_end", now)
            put("play_start", now - 30000)
        }.toString()
        return execute(
            Spec(
                method = "POST",
                baseUrl = KgConfig.GATEWAY,
                path = "/youth/v1/ad/play_report",
                body = body,
                signType = SignType.ANDROID,
                cred = cred,
            )
        )
    }

    /** 领取一天概念 VIP（youth_day_vip.js） */
    suspend fun receiveVipListenSong(cred: Cred): JsonObject = execute(
        Spec(
            method = "POST",
            baseUrl = KgConfig.GATEWAY,
            path = "/youth/v1/recharge/receive_vip_listen_song",
            extraParams = mapOf("source_id" to KgConfig.RECEIVE_VIP_SOURCE_ID),
            signType = SignType.ANDROID,
            cred = cred,
        )
    )

    /** 升级超级 VIP（youth_day_vip_upgrade.js） */
    suspend fun upgradeVipReward(cred: Cred): JsonObject = execute(
        Spec(
            method = "POST",
            baseUrl = KgConfig.GATEWAY,
            path = "/youth/v1/listen_song/upgrade_vip_reward",
            extraParams = mapOf(
                "kugouid" to cred.userid,
                "ad_type" to "1",
            ),
            signType = SignType.ANDROID,
            cred = cred,
        )
    )
}
