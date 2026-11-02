package com.kgcheckin.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 签到历史记录（签到日历页数据源）：
 * 从第一次使用本 APP 签到起逐日累积，按账号 + 日期存取。
 */
@Entity(tableName = "checkin_records")
data class CheckinRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userid: String,
    /** yyyy-MM-dd */
    val date: String,
    /** LocalDate.toEpochDay()，便于区间查询 */
    val epochDay: Long,
    val success: Boolean,
    /** 一句话结果，如“听歌✓ 广告 8/8 畅听VIP✓ 升级✓” */
    val summary: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Dao
interface CheckinRecordDao {

    @Insert
    suspend fun insert(record: CheckinRecord)

    /** 查询某账号指定月份区间的记录（含起止日） */
    @Query("SELECT * FROM checkin_records WHERE userid = :userid AND epochDay >= :fromDay AND epochDay <= :toDay ORDER BY epochDay ASC")
    fun queryMonth(userid: String, fromDay: Long, toDay: Long): Flow<List<CheckinRecord>>

    /** 该账号是否已有当天记录（用于重复签到时更新而非追加） */
    /** 该账号是否已有当天记录（用于重复签到时更新而非追加） */
    @Query("SELECT * FROM checkin_records WHERE userid = :userid AND epochDay = :epochDay LIMIT 1")
    suspend fun findDay(userid: String, epochDay: Long): CheckinRecord?

    @Query("DELETE FROM checkin_records WHERE userid = :userid AND epochDay = :epochDay")
    suspend fun deleteDay(userid: String, epochDay: Long)

    /** 清理未来日期的脏数据（历史bug产生的误判记录） */
    @Query("DELETE FROM checkin_records WHERE epochDay > :todayEpochDay")
    suspend fun deleteFutureRecords(todayEpochDay: Long)

    @Query("UPDATE checkin_records SET success = :success, summary = :summary, timestamp = :timestamp WHERE id = :id")
    suspend fun updateDay(id: Long, success: Boolean, summary: String, timestamp: Long)

    @Query("DELETE FROM checkin_records WHERE userid = :userid")
    suspend fun deleteByUser(userid: String)

    /** 该账号第一条记录（日历页“开始使用日期”） */
    @Query("SELECT * FROM checkin_records WHERE userid = :userid ORDER BY epochDay ASC LIMIT 1")
    suspend fun firstRecord(userid: String): CheckinRecord?
}

@Database(entities = [CheckinRecord::class], version = 1, exportSchema = false)
abstract class CheckinDb : RoomDatabase() {
    abstract fun recordDao(): CheckinRecordDao
}
