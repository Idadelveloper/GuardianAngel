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
        TripEntity::class,
    ],
    version = 5,
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
    abstract fun tripDao(): TripDao
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
         * Adds the login session, the local credential, and the codeword "the user
         * actually chose this" flag.
         *
         * `sessionActive` defaults to 1 so anyone already using the app stays signed in
         * rather than being bounced to a login screen by an update.
         * `isCustomised` defaults to 0, which is the honest answer for existing rows:
         * they may be seeded suggestions, and treating a suggestion as configured is the
         * bug this column exists to fix.
         */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE users ADD COLUMN sessionActive INTEGER NOT NULL DEFAULT 1"
                )
                connection.execSQL(
                    "ALTER TABLE users ADD COLUMN encryptedPasswordHash BLOB DEFAULT NULL"
                )
                connection.execSQL(
                    "ALTER TABLE codewords ADD COLUMN isCustomised INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Adds the "ring a guardian on an emergency" setting.
         *
         * Defaults to 1, the same default a fresh install gets, so an existing user is
         * not quietly opted out of a protection a new user would have.
         */
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE users ADD COLUMN callGuardianOnEmergency INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * Adds the `trips` table.
         *
         * Hand-written to match Room's generated schema exactly — a column order or a
         * missing index makes `validateMigration` fail at runtime on an upgraded
         * install while a fresh install works perfectly, which is the worst way to
         * find out.
         */
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trips` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `destinationName` TEXT NOT NULL,
                        `destinationAddress` TEXT NOT NULL,
                        `destinationLat` REAL NOT NULL,
                        `destinationLng` REAL NOT NULL,
                        `originLat` REAL,
                        `originLng` REAL,
                        `routeId` TEXT,
                        `routeLabel` TEXT,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER,
                        `outcome` TEXT NOT NULL,
                        `distanceMeters` INTEGER,
                        `notifyContactId` TEXT,
                        `notifyContactName` TEXT,
                        `notifyMessage` TEXT,
                        `arrivalNotifiedAt` INTEGER,
                        `arrivalNotifyError` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        `syncedAt` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`userId`) REFERENCES `users`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_userId` ON `trips` (`userId`)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_outcome` ON `trips` (`outcome`)")
            }
        }

        /**
         * All migrations, in order. Each schema change adds one here rather than
         * bumping the version and hoping.
         */
        val MIGRATIONS: Array<androidx.room.migration.Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

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
