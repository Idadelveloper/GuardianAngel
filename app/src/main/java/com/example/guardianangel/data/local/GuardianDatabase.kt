package com.example.guardianangel.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.execSQL

/**
 * The on-device database.
 *
 * ## Versioning
 *
 * Schemas are exported to `app/schemas` and checked in, so every change arrives as a
 * reviewable diff and `MigrationTestHelper` can replay old databases against new code.
 *
 * `fallbackToDestructiveMigration` is deliberately **not** set. For most apps wiping on
 * a bad migration is an acceptable last resort; here it would silently destroy the
 * guardians and codewords someone is relying on, and they would not find out until the
 * night they needed them. A missing migration should fail loudly in development
 * instead.
 */
@Database(
    entities = [
        UserEntity::class,
        VoiceProfileEntity::class,
        WakeWordEntity::class,
        CodewordEntity::class,
        CodewordContactEntity::class,
        GuardianEntity::class,
        SessionEntity::class,
        TranscriptEntryEntity::class,
        AudioEventEntity::class,
        LocationPointEntity::class,
        SafePlaceEntity::class,
        DisarmPinEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class GuardianDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun wakeWordDao(): WakeWordDao
    abstract fun codewordDao(): CodewordDao
    abstract fun guardianDao(): GuardianDao
    abstract fun sessionDao(): SessionDao
    abstract fun safePlaceDao(): SafePlaceDao
    abstract fun disarmPinDao(): DisarmPinDao
    abstract fun syncDao(): SyncDao

    companion object {
        private const val NAME = "guardian-angel.db"

        /**
         * Adds `tokenizerVersion` to `wake_words`.
         *
         * Defaulting to 0 marks every existing row as produced by the original, broken
         * tokeniser, so the first read regenerates its tokens. Anyone who set a wake
         * word before this fix had one that could never fire; they get a working one
         * back without being asked to do anything.
         */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE wake_words ADD COLUMN tokenizerVersion INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * All migrations, in order. Each schema change adds one here rather than
         * bumping the version and hoping.
         */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf(MIGRATION_1_2)

        @Volatile
        private var instance: GuardianDatabase? = null

        fun get(context: Context): GuardianDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): GuardianDatabase =
            Room.databaseBuilder(context, GuardianDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                // WAL lets the listening service write a session while the UI reads
                // the activity list, instead of the two blocking each other.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
