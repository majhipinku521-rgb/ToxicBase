package com.example.toxicbase.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ApiKeyEntity::class,
        UserEntity::class,
        OtpChallengeEntity::class,
        SessionEntity::class,
        DocumentEntity::class,
        CollectionIndexEntity::class,
        RequestLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ToxicBaseDatabase : RoomDatabase() {
    abstract fun dao(): ToxicBaseDao

    companion object {
        @Volatile
        private var INSTANCE: ToxicBaseDatabase? = null

        fun getInstance(context: Context): ToxicBaseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ToxicBaseDatabase::class.java,
                    "toxicbase_baas_engine.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
