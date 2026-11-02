package com.kgcheckin.app.data

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.kgcheckin.app.core.Cred
import com.kgcheckin.app.core.KgApi
import com.kgcheckin.app.core.KgConfig
import com.kgcheckin.app.core.LogLevel
import com.kgcheckin.app.core.LogEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/** 扫码登录会话状态 */
sealed interface QrLoginState {
    data object Idle : QrLoginState
    data object Loading : QrLoginState
    /** 二维码已生成，等待用户扫描（约 2 分钟有效） */
    data class Ready(val qrContent: String, val bitmap: Bitmap, val createdAt: Long) : QrLoginState
    /** 用户已扫码，等待手机端确认 */
    data object Scanned : QrLoginState
    data object Expired : QrLoginState
    /** 登录成功 */
    data class Success(val account: Account) : QrLoginState
    data class Error(val message: String) : QrLoginState
}

/** 解析出的 VIP 状态 */
data class VipInfo(
    val endTime: String,
    val productType: String,
    val remainDays: Int,
)

/**
 * 业务仓库：登录、签到、VIP 状态。
 * 签到流程完整移植自 kgcheckin2/main.js（听歌领取 → 8 次广告领取 → 查 VIP → 领一天畅听 VIP → 升级超级 VIP）。
 */
class KgRepository(
    private val api: KgApi,
    private val store: AccountStore,
    private val recordDao: CheckinRecordDao,
) {
    val accounts get() = store.accounts

    // ══════════════════════════ 通用工具 ══════════════════════════

    private fun JsonObject.status(): Int = this["status"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
    private fun JsonObject.errorCode(): Int =
        this["error_code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

    private fun JsonObject.strMsg(): String {
        val direct = this["msg"]?.jsonPrimitive?.contentOrNull
            ?: this["error_msg"]?.jsonPrimitive?.contentOrNull
        if (!direct.isNullOrEmpty()) return direct
        val data = this["data"]
        if (data != null) {
            val obj = runCatching { data.jsonObject }.getOrNull()
            obj?.get("msg")?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) return it }
            data.jsonPrimitive.contentOrNull?.let { if (it.isNotEmpty()) return it }
        }
        return ""
    }

    /** 接口错误详情（等价脚本 bizErrDetail） */
    private fun JsonObject.bizErr(): String {
        val parts = mutableListOf<String>()
        strMsg().takeIf { it.isNotEmpty() }?.let { parts.add("msg=$it") }
        parts.add("error_code=${this["error_code"]?.jsonPrimitive?.contentOrNull ?: this["status"]?.jsonPrimitive?.contentOrNull ?: "未知"}")
        return parts.joinToString(", ")
    }

    /** 解析 VIP 到期时间（兼容 'YYYY-MM-DD HH:mm:ss' / 'YYYY-MM-DD' / 秒或毫秒时间戳） */
    private fun parseVipTime(value: String): LocalDateTime? {
        if (value.isEmpty()) return null
        val trimmed = value.trim()
        if (trimmed.all { it.isDigit() }) {
            val num = trimmed.toLongOrNull() ?: return null
            val millis = if (num < 1_000_000_000_000L) num * 1000 else num
            return runCatching { LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(millis),
                java.time.ZoneId.systemDefault()
            ) }.getOrNull()
        }
        return runCatching { LocalDateTime.parse(trimmed.replace(' ', 'T')) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed).atStartOfDay() }.getOrNull()
    }

    private fun daysUntil(end: LocalDateTime): Int {
        val ms = java.time.Duration.between(LocalDateTime.now(), end).toMillis()
        return ceil(ms / 86_400_000.0).toInt()
    }

    /** 从 /user/vip/detail 响应解析全部有效会员权益（svip 优先） */
    private fun parseVipList(body: JsonObject): List<com.kgcheckin.app.data.VipEntry> {
        val busiVip = runCatching {
            body["data"]?.jsonObject?.get("busi_vip")?.jsonArray
        }.getOrNull() ?: return emptyList()
        val now = LocalDateTime.now()
        val effective = busiVip.mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val isVip = obj["is_vip"]?.jsonPrimitive?.contentOrNull == "1" ||
                obj["is_vip"]?.jsonPrimitive?.booleanOrNull == true
            val type = obj["product_type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val endTime = obj["vip_end_time"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val end = parseVipTime(endTime) ?: return@mapNotNull null
            if (isVip && end.isAfter(now)) {
                com.kgcheckin.app.data.VipEntry(type, endTime, daysUntil(end))
            } else null
        }
        return effective.sortedByDescending { it.productType == "svip" }
    }

    /** 从 /user/vip/detail 响应解析展示用 VIP 状态（优先 svip） */
    private fun parseVipInfo(body: JsonObject): VipInfo? {
        val busiVip = runCatching {
            body["data"]?.jsonObject?.get("busi_vip")?.jsonArray
        }.getOrNull() ?: return null
        val now = LocalDateTime.now()
        val effective = busiVip.mapNotNull { el ->
            val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val isVip = obj["is_vip"]?.jsonPrimitive?.contentOrNull == "1" ||
                obj["is_vip"]?.jsonPrimitive?.booleanOrNull == true
            val type = obj["product_type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val endTime = obj["vip_end_time"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val end = parseVipTime(endTime) ?: return@mapNotNull null
            if (isVip && end.isAfter(now)) Triple(type, endTime, end) else null
        }
        val preferred = effective.firstOrNull { it.first == "svip" } ?: effective.firstOrNull() ?: return null
        return VipInfo(
            endTime = preferred.second,
            productType = preferred.first,
            remainDays = daysUntil(preferred.third),
        )
    }

    // ══════════════════════════ 登录流程 ══════════════════════════

    /** 生成二维码内容并渲染 Bitmap（ZXing，黑底白码可反色由 UI 决定） */
    private suspend fun renderQr(content: String, size: Int = 720): Bitmap = withContext(Dispatchers.Default) {
        val hints = mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }

    /** 创建扫码登录会话：返回二维码内容与图片 */
    suspend fun createQrSession(): Pair<String, Bitmap> {
        val res = api.qrKey()
        if (res.status() != 1) {
            throw IllegalStateException("获取二维码失败：${res.bizErr()}")
        }
        val qrcode = res["data"]?.jsonObject?.get("qrcode")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("二维码 key 为空")
        val content = KgConfig.QR_LOGIN_URL_PREFIX + qrcode
        return content to renderQr(content)
    }

    /**
     * 轮询扫码状态一次（由 UI 层控制节奏：约 2 分钟内每 3~5 秒一次）
     * 返回：0=待扫码 2=已扫待确认 4=登录成功
     */
    suspend fun pollQrOnce(qrContent: String): Int {
        val qrcode = qrContent.removePrefix(KgConfig.QR_LOGIN_URL_PREFIX)
        val res = api.qrCheck(qrcode)
        val data = runCatching { res["data"]?.jsonObject }.getOrNull()
        return data?.get("status")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
    }

    /** 用扫码成功的 token/userid 完成账号初始化（dfid、昵称、VIP 状态） */
    suspend fun finishLogin(token: String, userid: String): Account {
        val account = Account(userid = userid, token = token)
        ensureDfid(account) { }
        refreshAccountInfo(account)
        store.upsert(account)
        return account
    }

    /** 获取/补齐设备指纹 dfid（风控接口需要；失败仅返回 false） */
    private suspend fun ensureDfid(account: Account, log: (LogEvent) -> Unit): Boolean {
        if (account.dfid.isNotEmpty()) return false
        val res = api.registerDev(account.cred)
        val dfid = res["data"]?.jsonObject?.get("dfid")?.jsonPrimitive?.contentOrNull
        return if (res.status() == 1 && !dfid.isNullOrEmpty()) {
            account.dfid = dfid
            true
        } else {
            false
        }
    }

    /** 头像URL规范化：http→https（Android 9+ 明文限制），避免Coil加载失败 */
    private fun normalizeAvatarUrl(url: String): String =
        if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url

    /** 仓库层调试日志存档注入（AppContainer 初始化时调用） */
    fun attachLogArchive(archive: com.kgcheckin.app.core.LogArchive?) {
        debugLogArchive = archive
    }

    /** 刷新账号展示信息（昵称、头像、全部会员权益），并持久化 */
    suspend fun refreshAccountInfo(account: Account) {
        runCatching {
            val info = api.getMyInfo(account.cred)
            val data = info["data"]?.jsonObject
            data?.get("nickname")?.jsonPrimitive?.contentOrNull?.let {
                account.nickname = it
            }
            // 头像：多候选字段兼容（pic/pic_url/headpic/avatar/icon/user_pic），
            // 并将 http:// 升级为 https://（Android 9+ 默认禁止明文图床请求）
            val picRaw = listOf("pic", "pic_url", "headpic", "avatar", "icon", "user_pic")
                .firstNotNullOfOrNull { key ->
                    data?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                }
            picRaw?.let { account.pic = normalizeAvatarUrl(it) }

            // 乐龄：注册时间 rtime（get_my_info 实测字段，epoch 秒）
            val regRaw = listOf("rtime", "reg_time", "regtime", "create_time", "register_time")
                .firstNotNullOfOrNull { key ->
                    data?.get(key)?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                }
            if (regRaw != null && regRaw in 1_000_000_000L..System.currentTimeMillis() / 1000) {
                account.regTime = regRaw
            }
            // 累计听歌时长：duration 字段（实测存在，单位按分钟）
            val listenRaw = listOf(
                "duration", "listen_duration", "listen_total_duration",
                "total_listen_time", "listen_time",
            ).firstNotNullOfOrNull { key ->
                data?.get(key)?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            }
            if (listenRaw != null && listenRaw >= 0) {
                account.listenMinutes = listenRaw
            }

            // 调试：乐龄或听歌时长未解析到时，把用户信息接口的完整字段清单写入日志，
            // 便于按真实响应结构精调解析（敏感字段脱敏）
            if (account.regTime == 0L || account.listenMinutes < 0) {
                val fields = data?.entries?.joinToString(", ") { (k, v) ->
                    val text = (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: "<obj>"
                    if (k.contains("token", ignoreCase = true) || text.length > 80) {
                        "$k=<${text.length}字符>"
                    } else {
                        "$k=$text"
                    }
                } ?: "无data"
                val event = LogEvent.warn("[调试]用户信息字段(用于精调乐龄/听歌解析): $fields")
                debugLogArchive?.append(event)
                com.kgcheckin.app.core.CheckinLogBus.post(event)
            }
        }
        runCatching {
            val vip = api.getUnionVip(account.cred)
            applyVipToAccount(account, vip)
        }
        store.upsert(account)
    }

    /** 将会员详情写入账号（主字段 + 全量列表） */
    private fun applyVipToAccount(account: Account, body: JsonObject) {
        val list = parseVipList(body)
        account.vipList = list
        val main = list.firstOrNull()
        if (main != null) {
            account.vipType = main.productType
            account.vipEndTime = main.endTime
            account.vipRemainDays = main.remainDays
        }
    }

    /** 发送短信验证码 */
    suspend fun sendSmsCode(mobile: String) {
        val res = api.sendMobileCode(mobile)
        if (res.status() != 1) {
            throw IllegalStateException("验证码发送失败：${res.bizErr()}")
        }
    }

    /** 验证码登录（34175=多账号绑定手机不支持） */
    suspend fun loginBySms(mobile: String, code: String): Account {
        val res = api.loginByVerifyCode(mobile, code)
        if (res.status() == 1) {
            val data = runCatching { res["data"]?.jsonObject }.getOrNull()
            val token = data?.get("token")?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("登录响应缺少 token")
            val userid = data["userid"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("登录响应缺少 userid")
            return finishLogin(token, userid)
        }
        if (res.errorCode() == 34175) {
            throw IllegalStateException("暂不支持多账号绑定手机登录")
        }
        throw IllegalStateException("登录失败：${res.bizErr()}")
    }

    /** 删除账号及其签到历史 */
    suspend fun deleteAccount(userid: String): Boolean {
        recordDao.deleteByUser(userid)
        return store.remove(userid)
    }

    /** 扫码确认成功后：再次调轮询接口解析 token/userid，完成账号初始化并落库 */
    suspend fun loginByQrToken(qrcode: String): Account {
        val res = api.qrCheck(qrcode)
        val data = runCatching { res["data"]?.jsonObject }.getOrNull()
            ?: throw IllegalStateException("登录响应解析失败")
        val status = data["status"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        if (status != 4) throw IllegalStateException("二维码状态异常（status=$status）")
        val token = data["token"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("未获取到 token")
        val userid = data["userid"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("未获取到 userid")
        return finishLogin(token, userid)
    }

    // ══════════════════════════════ 服务器签到记录同步 ══════════════════════════════

    /**
     * 从酷狗服务器拉取签到领取记录（/youth/v1/activity/get_month_vip_record），
     * 补齐本地缺失的日期。
     *
     * - targetMonth 为 null 同步当月；否则带 month 参数尝试历史月份（服务器是否支持以实测为准）
     * - 关键防御：解析出的日期必须落在目标月内才落库——即使服务器忽略参数返回当月数据，也不会写错月份
     * - 接口为"领取记录"性质：出现的日期视为已领取，除非带显式未领标记
     * - 只补缺，不覆盖本地已有记录
     * @return 本次新同步的记录条数
     */
    suspend fun syncServerRecords(account: Account, targetMonth: java.time.YearMonth? = null): Int = runCatching {
        val today = LocalDate.now()
        // 清理未来日期的脏记录（历史解析误判产生，如未来月份被误标打卡）
        recordDao.deleteFutureRecords(today.toEpochDay())
        val body = if (targetMonth == null) {
            api.getMonthVipRecord(account.cred)
        } else {
            api.getMonthVipRecord(account.cred, month = "%04d-%02d".format(targetMonth.year, targetMonth.monthValue))
        }
        if (body.status() != 1) return@runCatching 0
        // 严格过滤：不允许任何未来日期（含明天）落库
        val validDates = collectRecordDates(body).filter { date ->
            !date.isAfter(today) &&
                (targetMonth == null || java.time.YearMonth.from(date) == targetMonth)
        }
        var added = 0
        for (date in validDates) {
            val epochDay = date.toEpochDay()
            if (recordDao.findDay(account.userid, epochDay) == null) {
                recordDao.insert(
                    CheckinRecord(
                        userid = account.userid,
                        date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        epochDay = epochDay,
                        success = true,
                        summary = "服务器同步",
                    )
                )
                added++
            }
        }
        added
    }.getOrDefault(0)

    /**
     * 防御式解析：递归查找响应中所有"含日期的记录对象"，提取已领取日期。
     * 接口响应结构未公开，兼容常见字段命名（day/date/receive_time/is_receive 等）。
     */
    private fun collectRecordDates(
        element: kotlinx.serialization.json.JsonElement,
        depth: Int = 0,
    ): List<LocalDate> {
        if (depth > 6) return emptyList()
        return when (element) {
            is kotlinx.serialization.json.JsonArray -> element.flatMap { collectRecordDates(it, depth + 1) }
            is JsonObject -> {
                val date = extractRecordDate(element)
                when {
                    date != null && !isExplicitNotReceived(element) -> listOf(date)
                    date != null -> emptyList() // 显式未领取
                    else -> element.values.flatMap { collectRecordDates(it, depth + 1) }
                }
            }
            else -> emptyList()
        }
    }

    /** 从记录对象中提取日期（支持 yyyy-MM-dd[ HH:mm:ss] / MM-dd；排除生日、到期类字段） */
    private fun extractRecordDate(obj: JsonObject): LocalDate? {
        val today = LocalDate.now()
        for ((key, value) in obj) {
            val lowerKey = key.lowercase()
            val isDateField = when {
                lowerKey.contains("birth") -> false           // 生日
                lowerKey.contains("expire") -> false          // 过期时间
                lowerKey.contains("end_time") -> false       // 到期时间（vip_end_time 等）
                lowerKey.contains("date") -> true
                lowerKey.contains("day") -> true
                lowerKey.contains("time") -> true
                else -> false
            }
            if (!isDateField) continue
            when (value) {
                is kotlinx.serialization.json.JsonPrimitive -> {
                    val text = value.contentOrNull ?: continue
                    // yyyy-MM-dd 或含完整日期时间
                    Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(text)?.let { m ->
                        val d = runCatching {
                            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                        }.getOrNull()
                        if (d != null && !d.isBefore(LocalDate.of(2020, 1, 1))) return d
                    }
                    // MM-dd（无年份，补当前年）
                    Regex("^(\\d{1,2})-(\\d{1,2})$").find(text.trim())?.let { m ->
                        val d = runCatching {
                            LocalDate.of(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt())
                        }.getOrNull()
                        if (d != null && !d.isAfter(today)) return d
                    }
                }
                else -> continue
            }
        }
        return null
    }

    /** 判断记录对象是否显式标记为未领取（is_receive/received == 0/false） */
    private fun isExplicitNotReceived(obj: JsonObject): Boolean {
        for ((key, value) in obj) {
            val lowerKey = key.lowercase()
            if (lowerKey == "is_receive" || lowerKey == "received" || lowerKey == "is_get") {
                val text = (value as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                if (text == "0" || text == "false" || text.isNullOrBlank()) return true
            }
        }
        return false
    }

    // ══════════════════════════ 签到流程（移植 main.js）══════════════════════════

    /**
     * 单账号完整签到。逐条输出过程日志；返回结果汇总并写入签到日历。
     * 任意账号失败不影响外部继续处理其他账号。
     */
    suspend fun runCheckin(
        account: Account,
        log: (LogEvent) -> Unit,
    ): CheckinOutcome = try {
        doCheckin(account, log)
    } catch (e: CancellationException) {
        log(LogEvent.warn("签到已取消"))
        throw e
    } catch (e: Exception) {
        log(LogEvent.error("账号处理异常：${e.message}"))
        CheckinOutcome(
            userid = account.userid,
            nickname = account.displayNickname(),
            success = false,
            listenStatus = "异常",
            adClaimCount = 0,
            adClaimTotal = 0,
            dayVipStatus = "-",
            upgradeStatus = "-",
            vipEndTime = account.vipEndTime,
            vipType = account.vipType,
            vipRemainDays = account.vipRemainDays,
            error = e.message ?: "未知异常",
        )
    }

    private suspend fun doCheckin(account: Account, log: (LogEvent) -> Unit): CheckinOutcome {
        val cred = account.cred
        var hasError = false
        log(LogEvent.account("账号 ${account.displayNickname()} 开始领取VIP..."))

        // 0. 设备指纹 dfid
        if (account.dfid.isEmpty()) {
            log(LogEvent.step("正在获取设备指纹 dfid ..."))
            if (ensureDfid(account, log)) {
                log(LogEvent.success("dfid 获取成功"))
            } else {
                log(LogEvent.warn("dfid 获取失败（领取 VIP 类接口可能异常）"))
            }
        }

        // 1. 校验 token：取用户详情
        log(LogEvent.step("校验登录状态 ..."))
        val detail = api.getMyInfo(cred)
        val nickname = detail["data"]?.jsonObject?.get("nickname")?.jsonPrimitive?.contentOrNull
        if (nickname == null) {
            log(LogEvent.error("token 过期或账号不存在，请重新登录"))
            return finishOutcome(
                account, success = false, listen = "账号无效", adCount = 0, adTotal = 0,
                dayVip = "-", upgrade = "-", hasError = true,
                error = "token过期或账号不存在", log = log,
            )
        }
        account.nickname = nickname
        log(LogEvent.success("登录有效：$nickname"))

        // 2. 周日自动刷新 token（等价脚本逻辑）
        if (LocalDate.now().dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            runCatching {
                val refresh = api.loginByToken(cred)
                if (refresh.status() == 1) {
                    val newToken = refresh["data"]?.jsonObject?.get("token")?.jsonPrimitive?.contentOrNull
                    if (newToken != null && newToken != account.token) {
                        account.token = newToken
                        log(LogEvent.warn("token 已自动刷新"))
                    }
                }
            }
        }

        // 3. 听歌领取
        log(LogEvent.step("开始听歌领取VIP..."))
        var listenStatus = "未知"
        val listen = api.reportListenSong(cred)
        when {
            listen.status() == 1 -> {
                log(LogEvent.success("听歌领取成功"))
                listenStatus = "成功"
            }
            listen.errorCode() == 130012 -> {
                log(LogEvent.success("听歌领取：今日已领取"))
                listenStatus = "今日已领取"
            }
            else -> {
                log(LogEvent.error("听歌领取失败（${listen.bizErr()}）"))
                listenStatus = "失败"
                hasError = true
            }
        }

        // 4. 广告领取（最多 8 次，间隔 25~35 秒防风控，等价脚本）
        log(LogEvent.step("开始领取VIP..."))
        var claimCount = 0
        var claimTotal = 0
        for (i in 1..8) {
            claimTotal = i
            val ad = api.adPlayReport(cred)
            if (ad.status() == 1) {
                claimCount++
                log(LogEvent.success("第${i}次领取成功"))
                if (i != 8) {
                    val waitSec = 25 + (0..9).random()
                    log(LogEvent.info("等待 $waitSec 秒后继续 ..."))
                    delay(waitSec * 1000L)
                }
            } else if (ad.errorCode() == 30002) {
                log(LogEvent.success("广告领取：今日次数已用光"))
                break
            } else {
                log(LogEvent.error("第${i}次领取失败（${ad.bizErr()}）"))
                hasError = true
                break
            }
        }

        // 5. 查询 VIP 状态并决策（svip 有效→跳过；tvip 有效→只升级；否则领取+升级）
        log(LogEvent.step("查询 VIP 状态 ..."))
        val vipDetail = api.getUnionVip(cred)
        val vipInfo = parseVipInfo(vipDetail)
        val busiVip = runCatching {
            vipDetail["data"]?.jsonObject?.get("busi_vip")?.jsonArray
        }.getOrNull()

        fun activeOf(type: String): LocalDateTime? {
            val arr = busiVip ?: return null
            return arr.mapNotNull { el ->
                val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                val t = obj["product_type"]?.jsonPrimitive?.contentOrNull
                val isVip = obj["is_vip"]?.jsonPrimitive?.contentOrNull == "1" ||
                    obj["is_vip"]?.jsonPrimitive?.booleanOrNull == true
                val end = obj["vip_end_time"]?.jsonPrimitive?.contentOrNull?.let { parseVipTime(it) }
                if (t == type && isVip && end != null && end.isAfter(LocalDateTime.now())) end else null
            }.firstOrNull()
        }

        var dayVipStatus = "-"
        var upgradeStatus = "-"
        val activeSvip = activeOf("svip")
        val activeTvip = activeOf("tvip")

        if (activeSvip != null) {
            log(LogEvent.success("超级 VIP 有效期内（至 ${vipInfo?.endTime ?: "未知"}），无需领取与升级"))
            dayVipStatus = "超级VIP有效"
            upgradeStatus = "超级VIP有效"
        } else {
            var gotToday = false
            if (activeTvip != null) {
                log(LogEvent.warn("畅听 VIP(tvip) 有效，跳过领取直接升级"))
                dayVipStatus = "畅听VIP有效"
                gotToday = true
            } else {
                log(LogEvent.step("领取一天概念 VIP ..."))
                val receive = api.receiveVipListenSong(cred)
                when {
                    receive.status() == 1 -> {
                        log(LogEvent.success("一天概念 VIP 领取成功"))
                        dayVipStatus = "成功"
                        gotToday = true
                    }
                    receive.errorCode() == 131001 -> {
                        log(LogEvent.success("一天概念 VIP：今日已领取"))
                        dayVipStatus = "今日已领取"
                        gotToday = true
                    }
                    else -> {
                        log(LogEvent.error("一天概念 VIP 领取失败（${receive.bizErr()}）"))
                        dayVipStatus = "失败"
                        hasError = true
                    }
                }
            }
            if (gotToday) {
                log(LogEvent.step("升级超级 VIP ..."))
                val upgrade = api.upgradeVipReward(cred)
                val msg = upgrade.strMsg()
                when {
                    upgrade.status() == 1 -> {
                        log(LogEvent.success("升级超级 VIP 成功"))
                        upgradeStatus = "成功"
                    }
                    upgrade.errorCode() == 297000 || Regex("已(经)?领取|无需|不能升级").containsMatchIn(msg) -> {
                        log(LogEvent.success("升级超级 VIP：${if (msg.isEmpty()) "无需升级" else msg}"))
                        upgradeStatus = "无需升级"
                    }
                    else -> {
                        log(LogEvent.error("升级超级 VIP 失败（${upgrade.bizErr()}）"))
                        upgradeStatus = "失败"
                        hasError = true
                    }
                }
            } else {
                upgradeStatus = "跳过"
            }
        }

        // 6. 重新拉取 VIP 展示状态（主字段 + 全量列表）
        runCatching {
            val fresh = api.getUnionVip(cred)
            applyVipToAccount(account, fresh)
        }
        if (vipInfo != null) {
            account.vipEndTime = vipInfo.endTime
            account.vipType = vipInfo.productType
            account.vipRemainDays = vipInfo.remainDays
            log(
                LogEvent.datetime(
                    "今天是：${LocalDate.now()}\nVIP到期时间：${vipInfo.endTime}" +
                        "（还剩 ${vipInfo.remainDays} 天）"
                )
            )
        } else {
            log(LogEvent.error("VIP 到期时间获取失败"))
            hasError = true
        }

        return finishOutcome(
            account,
            success = !hasError,
            listen = listenStatus,
            adCount = claimCount,
            adTotal = claimTotal,
            dayVip = dayVipStatus,
            upgrade = upgradeStatus,
            hasError = hasError,
            error = "",
            log = log,
        )
    }

    /** 汇总结果 + 持久化账号 + 写签到日历 */
    private suspend fun finishOutcome(
        account: Account,
        success: Boolean,
        listen: String,
        adCount: Int,
        adTotal: Int,
        dayVip: String,
        upgrade: String,
        hasError: Boolean,
        error: String,
        log: (LogEvent) -> Unit,
    ): CheckinOutcome {
        store.upsert(account)
        val today = LocalDate.now()
        val summary = buildString {
            append("听歌 $listen；广告 $adCount/$adTotal；VIP $dayVip；升级 $upgrade")
        }
        // 日历记录：同日重复签到则更新当天记录
        val existing = recordDao.findDay(account.userid, today.toEpochDay())
        if (existing != null) {
            recordDao.updateDay(existing.id, success, summary, System.currentTimeMillis())
        } else {
            recordDao.insert(
                CheckinRecord(
                    userid = account.userid,
                    date = today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    epochDay = today.toEpochDay(),
                    success = success,
                    summary = summary,
                )
            )
        }
        val outcome = CheckinOutcome(
            userid = account.userid,
            nickname = account.displayNickname(),
            success = success,
            listenStatus = listen,
            adClaimCount = adCount,
            adClaimTotal = adTotal,
            dayVipStatus = dayVip,
            upgradeStatus = upgrade,
            vipEndTime = account.vipEndTime,
            vipType = account.vipType,
            vipRemainDays = account.vipRemainDays,
            error = error,
        )
        log(
            if (success) LogEvent.success("✅ 签到完成") else LogEvent.error("❌ 签到失败")
        )
        return outcome
    }

    companion object {
        /** 仓库层调试日志存档（由 AppContainer 初始化时注入） */
        @Volatile internal var debugLogArchive: com.kgcheckin.app.core.LogArchive? = null
        @Volatile private var instance: KgRepository? = null

        fun okHttp(): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
