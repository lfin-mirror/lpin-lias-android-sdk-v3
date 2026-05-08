package io.lpin.android.sdk.face

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FaceSharedPreferences(context: Context) {
    private val preferences: SharedPreferences =
            context.applicationContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)

    var user: String?
        set(value) {
            if (value != null)
                preferences.edit().putString(KEY_USER, value).apply()
            else
                preferences.edit().remove(KEY_USER).apply()
        }
        get() = preferences.getString(KEY_USER, null)

    var modifyUsers: ArrayList<String>?
        set(data) {
            if (data == null) {
                preferences.edit().remove(KEY_MODIFY_USERS).apply()
            } else {
                preferences.edit()
                        .putString(
                                KEY_MODIFY_USERS,
                                Gson().toJson(data, object : TypeToken<ArrayList<String>>() {}.type)
                        )
                        .apply()
            }
        }
        get() = preferences.getString(KEY_MODIFY_USERS, null)?.let { data ->
            try {
                Gson().fromJson(data, object : TypeToken<ArrayList<String>>() {}.type)
            } catch (ignore: Exception) {
                null
            }
        }

    /**
     * 등록 대기 유저 추가
     *
     * @param uuid
     */
    fun addModifyUser(uuid: String): Boolean {
        return try {
            // 데이터가 없으면 초기화
            if (this.modifyUsers == null) {
                this.modifyUsers = arrayListOf()
            }
            // 데이터가 있으면 리턴
            if (this.modifyUsers?.any { it == uuid } == true) {
                return false
            }
            // 데이터 추가후 저장
            this.modifyUsers = this.modifyUsers?.apply { add(uuid) }
            true
        } catch (ignore: Exception) {
            false
        }
    }

    /**
     * 등록 대기 유저 제거
     *
     * @param uuid
     */
    fun removeModifyUser(uuid: String): Boolean {
        return try {
            // 데이터가 있는지 확인 후 제거
            if (this.modifyUsers == null) {
                false
            } else {
                if (this.modifyUsers?.any { it == uuid } == true) {
                    this.modifyUsers = this.modifyUsers?.apply { remove(uuid) }
                    true
                } else {
                    false
                }
            }
        } catch (ignore: Exception) {
            false
        }
    }

    /*
    var userChange: String?
        set(value: String?) {
            if (value != null)
                preferences.edit().putString("USER_CHANGE", value).apply()
            else
                preferences.edit().remove("USER_CHANGE").apply()
        }
        get() = preferences.getString("USER_CHANGE", null)
     */

    companion object {
        var TAG: String = FaceSharedPreferences::class.java.simpleName
        const val KEY_USER = "KEY_USER"
        const val KEY_MODIFY_USERS = "KEY_MODIFY_USERS"
    }
}