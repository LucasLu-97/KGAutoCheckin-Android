package com.kgcheckin.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.kgcheckin.app.core.KgApi
import com.kgcheckin.app.core.LogArchive
import com.kgcheckin.app.data.AccountStore
import com.kgcheckin.app.data.CheckinDb
import com.kgcheckin.app.data.KgRepository
import com.kgcheckin.app.data.SettingsStore
import com.kgcheckin.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 轻量手动依赖注入容器：
 * OkHttp → KgApi → (AccountStore + Room) → KgRepository
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** 进程级协程作用域：Service 销毁后仍需完成的任务（如闹钟续排）在这里执行 */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val okHttp = KgRepository.okHttp()
    private val api = KgApi(okHttp)
    private val db = Room.databaseBuilder(appContext, CheckinDb::class.java, "kg_checkin.db")
        // 弃用 API 迁移：无参版在 Room 内部即本重载传 true，行为逐字节等价（每日仅
        // 一条记录/账号，历史数据可弃保重建：缺少迁移路径时清库重连，避免崩溃）
        .fallbackToDestructiveMigration(true)
        .build()

    val recordDao = db.recordDao()
    val accountStore = AccountStore(appContext)
    val settingsStore = SettingsStore(appContext)
    val logArchive = LogArchive(appContext)
    val repository = KgRepository(api, accountStore, recordDao, logArchive)
    val notifier = Notifier(appContext)

    init {
        // 启动即异步加载已存账号（失败不崩溃，返回空列表）
        applicationScope.launch { accountStore.loadIfNeeded() }
    }

    /** 清空签到历史（保留账号凭据，重置应用数据用） */
    suspend fun clearCheckinHistory() {
        withContext(Dispatchers.IO) {
            runCatching { db.clearAllTables() }
        }
    }
}

class KgApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as KgApplication).container