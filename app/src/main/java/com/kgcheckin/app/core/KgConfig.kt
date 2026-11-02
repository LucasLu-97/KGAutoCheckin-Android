package com.kgcheckin.app.core

/** 酷狗客户端常量（移植自 api/util/config.json 与 request.js） */
object KgConfig {
    const val APPID = "1005"
    const val SRCAPPID = "2919"
    const val CLIENTVER = "20489"

    const val GATEWAY = "https://gateway.kugou.com"
    const val LOGIN_USER_HTTPS = "https://login-user.kugou.com"
    const val LOGIN_USER_HTTP = "http://login.user.kugou.com"
    const val USERSERVICE = "https://userservice.kugou.com"
    const val KUGOU_VIP = "https://kugouvip.kugou.com"

    const val UA_DEFAULT = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"
    const val UA_LISTEN_SONG = "Android13-1070-10566-201-0-ReportPlaySongToServerProtocol-wifi"

    /** 听歌上报固定曲目（原脚本默认值） */
    const val MIX_SONG_ID = 666075191L
    /** 广告领取固定 ad_id（原脚本默认值） */
    const val AD_ID = 12307537187L
    /** 领取一天概念 VIP 的固定 source_id */
    const val RECEIVE_VIP_SOURCE_ID = "90137"

    /** login_token 的 p3 固定密钥（移植自 login_token.js） */
    const val TOKEN_REFRESH_KEY = "90b8382a1bb4ccdcf063102053fd75b8"
    const val TOKEN_REFRESH_IV = "f063102053fd75b8"

    /** 二维码登录页 URL 模板（qrcode 为 key） */
    const val QR_LOGIN_URL_PREFIX = "https://h5.kugou.com/apps/loginQRCode/html/index.html?qrcode="
}
