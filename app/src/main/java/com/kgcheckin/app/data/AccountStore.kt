package com.kgcheckin.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 账号持久化存储：
 * - 账号列表 JSON 经 AndroidKeyStore AES-256-GCM 加密后写入应用私有目录
 * - 进程内以 StateFlow 暴露，全部账号读写走此单点
 * - 首次加载时自动迁移旧版（壳架构 v1.0.x）账号数据，升级不丢账号
 */
class AccountStore(context: Context) {

    private val appContext = context.applicationContext
    private val file: File get() = File(appContext.filesDir, "kg_accounts.bin")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Account.serializer())
    private val mutex = Mutex()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private var loaded = false

    /** 启动时加载（失败不崩溃，返回空列表） */
    suspend fun loadIfNeeded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            val list = withContext(Dispatchers.IO) {
                runCatching { readFromFile() }.getOrElse { emptyList() }
            }
            _accounts.value = list
            loaded = true
        }
    }

    /** 保存全部账号（加密落盘 + 更新内存态） */
    suspend fun save(accounts: List<Account>) {
        mutex.withLock {
            _accounts.value = accounts
            withContext(Dispatchers.IO) {
                writeToFile(accounts)
            }
        }
    }

    /** 新增或替换账号（按 userid 去重） */
    suspend fun upsert(account: Account) {
        val current = _accounts.value.toMutableList()
        val idx = current.indexOfFirst { it.userid == account.userid }
        if (idx >= 0) current[idx] = account else current.add(account)
        save(current)
    }

    /** 移除账号；返回是否删除成功 */
    suspend fun remove(userid: String): Boolean {
        val current = _accounts.value
        if (current.none { it.userid == userid }) return false
        save(current.filterNot { it.userid == userid })
        return true
    }

    suspend fun find(userid: String): Account? = _accounts.value.firstOrNull { it.userid == userid }

    // ═══════════════════════ 加密读写 + 旧版迁移 ═══════════════════════

    private fun readFromFile(): List<Account> {
        if (!file.exists()) return emptyList()
        val blob = file.readBytes()
        val iv = blob.copyOfRange(0, IV_LEN)
        val cipherBytes = blob.copyOfRange(IV_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(TAG_BITS, iv))
        val plain = cipher.doFinal(cipherBytes)
        val text = String(plain, Charsets.UTF_8)
        // 先按当前格式解析；失败则尝试从旧版（壳架构）格式迁移
        return runCatching { json.decodeFromString(serializer, text) }.getOrElse {
            migrateFromLegacy(text).also { migrated ->
                if (migrated.isNotEmpty()) writeToFile(migrated)
            }
        }
    }

    /**
     * 旧版（壳架构 v1.0.x）账号结构迁移：
     * [{id, credentialsJson, displayJson, scriptName}] → Account(userid/token/dfid/nickname/pic)
     * - credentialsJson：含 id/token/dfid
     * - displayJson：含 nickname/avatarUrl
     * 展示类字段（VIP 明细、乐龄等）在下次刷新账号时自动补齐。
     */
    private fun migrateFromLegacy(text: String): List<Account> {
        return runCatching {
            val arr = json.parseToJsonElement(text).jsonArray
            arr.mapNotNull { el ->
                val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                val credJson = obj["credentialsJson"]?.jsonPrimitive?.contentOrNull
                    ?: return@mapNotNull null
                val cred = runCatching { json.parseToJsonElement(credJson).jsonObject }.getOrNull()
                    ?: return@mapNotNull null
                val userid = cred["id"]?.jsonPrimitive?.contentOrNull
                    ?: cred["userid"]?.jsonPrimitive?.contentOrNull
                    ?: obj["id"]?.jsonPrimitive?.contentOrNull
                    ?: return@mapNotNull null
                val disp = obj["displayJson"]?.jsonPrimitive?.contentOrNull?.let {
                    runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull()
                }
                val avatar = disp?.get("avatarUrl")?.jsonPrimitive?.contentOrNull ?: ""
                Account(
                    userid = userid,
                    token = cred["token"]?.jsonPrimitive?.contentOrNull ?: "",
                    dfid = cred["dfid"]?.jsonPrimitive?.contentOrNull ?: "",
                    nickname = disp?.get("nickname")?.jsonPrimitive?.contentOrNull ?: "",
                    pic = if (avatar.startsWith("http://")) {
                        "https://" + avatar.removePrefix("http://")
                    } else {
                        avatar
                    },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeToFile(accounts: List<Account>) {
        if (!file.exists()) file.parentFile?.mkdirs()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val plain = json.encodeToString(serializer, accounts).toByteArray(Charsets.UTF_8)
        file.writeBytes(cipher.iv + cipher.doFinal(plain))
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "kg_checkin_master_key"
        private const val KEY_ALGORITHM = "AES"
        private const val IV_LEN = 12
        private const val TAG_BITS = 128
    }
}