package io.lpin.android.sdk.face.database

import androidx.room.*


@Dao
interface UserDao {
    companion object {
        const val TABLE_NAME = "users"
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(value: UserEntity)

    @Update
    fun update(value: UserEntity)

    @Delete
    fun delete(value: UserEntity)

    @Query("DELETE FROM $TABLE_NAME WHERE name=:name")
    fun deleteByName(name: String?)

    @Query("DELETE FROM $TABLE_NAME")
    fun clear()

    @Query("SELECT name FROM $TABLE_NAME WHERE name=:name LIMIT 1")
    fun isValidUser(name: String): String?

    @Query("SELECT * FROM $TABLE_NAME")
    fun users(): List<UserEntity>

    @Query("SELECT * FROM $TABLE_NAME WHERE name=:name LIMIT 1")
    fun findUser(name: String?): UserEntity?
}