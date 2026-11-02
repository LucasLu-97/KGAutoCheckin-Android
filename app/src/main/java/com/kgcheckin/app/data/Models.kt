package com.kgcheckin.app.data

import kotlinx.serialization.Serializable

/** 单项会员权益（busi_vip 中一个有效条目） */
@Serializable
data class VipEntry(
    /** svip / tvip / dvip / qvip ... */
    val productType: String,
    val endTime: String,
    val remainDays: Int,
) {
    /** 截图2风格名称映射：svip=概念会员、tvip=畅听会员 */
    val displayName: String
        get() = when (productType) {
            "svip" -> "概念会员"
            "tvip" -> "畅听会员"
            else -> "${productType.uppercase()} 会员"
        }
}

/**
 * 已登录的酷狗账号（等价脚本 USERINFO 数组中的一项）
 */
@Serializable
data class Account(
    val userid: String,
    var token: String,
    var dfid: String = "",
    var nickname: String = "",
    /** 酷狗头像 URL（get_my_info 的 pic 字段） */
    var pic: String = "",
    /** 注册时间（epoch 秒），0=未知；用于计算乐龄 */
    var regTime: Long = 0,
    /** 累计听歌分钟数，-1=未知 */
    var listenMinutes: Long = -1,
    /** 主 VIP 展示字段（优先 svip；兼容旧数据） */
    var vipEndTime: String = "",
    var vipType: String = "",
    var vipRemainDays: Int = -1,
    /** 全部有效会员权益（签到日历页会员信息模块数据源） */
    var vipList: List<VipEntry> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val cred
        get() = com.kgcheckin.app.core.Cred(token = token, userid = userid, dfid = dfid)

    /** 脱敏昵称展示 */
    fun displayNickname(): String = when {
        nickname.isNotEmpty() -> nickname
        userid.length > 4 -> "用户 ${userid.take(3)}***${userid.takeLast(2)}"
        else -> "用户 $userid"
    }

    /** 账号附加信息：乐龄 / 累计听歌时长（拉取到才展示） */
    fun profileExtra(): String? {
        val parts = mutableListOf<String>()
        if (regTime > 0) {
            val years = ((System.currentTimeMillis() / 1000 - regTime) / (365.25 * 24 * 3600)).toInt()
            parts.add(if (years > 0) "乐龄 $years 年" else "乐龄不足 1 年")
        }
        if (listenMinutes >= 0) {
            val hours = listenMinutes / 60
            parts.add(if (hours > 0) "已听歌 $hours 小时" else "已听歌 $listenMinutes 分钟")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

/** 单账号签到结果汇总 */
data class CheckinOutcome(
    val userid: String,
    val nickname: String,
    val success: Boolean,
    val listenStatus: String,
    val adClaimCount: Int,
    val adClaimTotal: Int,
    val dayVipStatus: String,
    val upgradeStatus: String,
    val vipEndTime: String,
    val vipType: String,
    val vipRemainDays: Int,
    val error: String = "",
)
