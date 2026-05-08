package io.lpin.android.sdk.face.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.lpin.android.sdk.face.BuildConfig

@Database(entities = [UserEntity::class], version = 3, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {
    abstract val userDao: UserDao

    companion object {
        private var instance: UserDatabase? = null
        fun getUserDatabase(context: Context): UserDatabase {
            if (instance == null) {
                instance = Room
                        .databaseBuilder(context.applicationContext, UserDatabase::class.java, BuildConfig.LIBRARY_PACKAGE_NAME + ".database")
                        .fallbackToDestructiveMigration()
                        .allowMainThreadQueries()
                        .build()
            }
            return instance!!
        }
    }
}