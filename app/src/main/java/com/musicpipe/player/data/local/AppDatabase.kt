package com.musicpipe.player.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HistoryTrack::class, FavoriteTrack::class, LyricsCache::class, LibrarySnapshot::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
    abstract fun librarySnapshotDao(): LibrarySnapshotDao

    companion object {
        /**
         * v1 -> v2: the legacy remote-id column became "streamPath"
         * (gateway-relative stream path). Add the new column and force old rows to
         * rebuild their URLs from the song id at read time.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history_tracks ADD COLUMN streamPath TEXT")
                db.execSQL("ALTER TABLE favorite_tracks ADD COLUMN streamPath TEXT")
                db.execSQL("UPDATE history_tracks SET coverUrl = NULL")
                db.execSQL("UPDATE favorite_tracks SET coverUrl = NULL")
            }
        }

        /**
         * v2 -> v3 (v1.7.0)：新增两张纯缓存表，与既有数据无关，直接建表即可。
         * 明确写成 migration 而不是 fallbackToDestructiveMigration —— 后者会把用户的
         * 播放历史与「我喜欢」一起清掉，代价远大于省下的几行代码。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lyrics_cache` (" +
                        "`songId` TEXT NOT NULL, `linesJson` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`songId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `library_snapshot` (" +
                        "`songId` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, " +
                        "`album` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`coverPath` TEXT, `streamPath` TEXT, `platform` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, PRIMARY KEY(`songId`))"
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "musicpipe.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
