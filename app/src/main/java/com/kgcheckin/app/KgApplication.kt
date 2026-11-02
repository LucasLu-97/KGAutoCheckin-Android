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

/**
 * 轻量手动依赖注入容器：
 * OkHttp -> KgApi -> (AccountStore + Room) -> KgRepository
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val okHttp = KgRepository.okHttp()
    private val api = KgApi(okHttp)
    private val db = Room.databaseBuilder(appContext, CheckinDb::class.java, "kg_checkin.db")
        .fallbackToDestructiveMigration()
        .build()
    val recordDao = db.recordDao()
    val accountStore = AccountStore(appContext)
    val settingsStore = SettingsStore(appContext)
    val logArchive = LogArchive(appContext)
    val repository = KgRepository(api, accountStore, db.recordDao())
    val notifier = Notifier(appContext)

    init {
        // 仓库层调试日志通道注入
        repository.attachLogArchive(logArchive)
    }

    /** 清空签到历史（保留账号凭据，重置应用数据用） */
    suspend fun clearCheckinHistory() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { db.clearAllTables() }
        }
    }

    init {
        applicationScope.launch { accountStore.loadIfNeeded() }
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
