package com.raghava.unlockaudit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AuditSession::class, AuditAppEvent::class], version = 1, exportSchema = false)
abstract class AuditDatabase : RoomDatabase() {
    abstract fun dao(): AuditDao

    companion object {
        @Volatile private var instance: AuditDatabase? = null
        fun get(context: Context): AuditDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AuditDatabase::class.java, "unlock_audit_v2.db"
            ).build().also { instance = it }
        }
    }
}
