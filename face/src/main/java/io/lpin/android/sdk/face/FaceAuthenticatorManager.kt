package io.lpin.android.sdk.face

import android.content.Context
import io.lpin.android.sdk.face.FaceAuthenticatorManager.Type.*
import io.lpin.android.sdk.face.database.UserDatabase
import io.lpin.android.sdk.face.database.UserEntity

class FaceAuthenticatorManager(builder: Builder) {
    enum class Type {
        REGISTER_FACE, MODIFY_FACE
    }

    private val database: UserDatabase = UserDatabase.getUserDatabase(builder.context)
    private val sharedPreferences: FaceSharedPreferences = FaceSharedPreferences(builder.context)

    /**
     * 현재 단말에 등록된 유저의 UUID 값을 가져온다.
     *
     * @param type
     * @see Type.REGISTER_FACE 현재 등록된 얼굴
     * @see Type.MODIFY_FACE  현재 변경 대기중인 얼굴
     */
    fun getUserUuid(type: Type): List<String> {
        return when (type) {
            REGISTER_FACE -> if (getUserUuid() == null) emptyList() else listOf(getUserUuid()!!)
            MODIFY_FACE -> getModifyUsers()
        }
    }

    /**
     * 얼굴 정보 등록 여부 체크
     */
    fun hasUser(): Boolean = database.userDao.findUser(sharedPreferences.user) != null

    private fun getUserUuid(): String? = sharedPreferences.user
    private fun getModifyUsers(): List<String> = sharedPreferences.modifyUsers ?: emptyList()

    /**
     * 현재 변경 대기중인 얼굴이 있는지 여부 확인
     */
    @Deprecated("hasModifyUser 로 메소드 이름 변경", ReplaceWith("hasModifyUser(uuid)"))
    fun hasWaitingForChangeFace(uuid: String): Boolean = hasModifyUser(uuid)

    /**
     * 현재 변경 대기중인 얼굴이 있는지 여부 확인
     */
    fun hasModifyUser(uuid: String): Boolean {
        return try {
            // 데이터 있는지 확인
            val hasDatabase = database.userDao.findUser(uuid) != null
            // 저장소 있는지 확인
            val hasSharedPreference = sharedPreferences.modifyUsers?.any { it == uuid } ?: false
            return hasDatabase && hasSharedPreference
        } catch (ignore: Exception) {
            false
        }
    }

    fun addFaceData(type: Type, uuid: String, entity: UserEntity) {
        when (type) {
            REGISTER_FACE -> {
                // 이전 데이터 제거
                deleteFaceData(type, uuid)

                sharedPreferences.user = uuid
                database.userDao.insert(entity)
            }
            MODIFY_FACE -> TODO()
        }
    }

    fun deleteFaceData(type: Type, uuid: String): Boolean {
        return when (type) {
            REGISTER_FACE -> try {
                // 데이터 제거
                database.userDao.deleteByName(uuid)
                // 저장소 제거
                sharedPreferences.user = null
                true
            } catch (ignore: Exception) {
                false
            }
            MODIFY_FACE -> try {
                if (hasModifyUser(uuid)) {
                    // 데이터 제거
                    database.userDao.deleteByName(uuid)
                    // 저장소 제거
                    sharedPreferences.removeModifyUser(uuid)
                    true
                } else {
                    false
                }
            } catch (ignore: Exception) {
                false
            }
        }
    }

    fun deleteFaceData(type: Type): Boolean {
        return when (type) {
            REGISTER_FACE -> try {
                sharedPreferences.user?.apply { database.userDao.deleteByName(this) }
                sharedPreferences.user = null
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            MODIFY_FACE -> try {
                sharedPreferences.modifyUsers?.forEach { database.userDao.deleteByName(it) }
                sharedPreferences.modifyUsers = null
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 현재 등록된 유저 제거
     */
    fun deleteFaceData(): Boolean {
        return deleteFaceData(REGISTER_FACE)
    }

    /**
     * 유저 FaceData 값
     */
    fun getFaceData(uuid: String?): FaceData? {
        if (uuid == null)
            return null
        val userUuid = uuid.replace("-", "").replace(" ", "")
        val userByUuid = database.userDao.findUser(userUuid)
        return if (userByUuid != null) {
            FaceData(uuid, FaceRecognizer.hashFromFeature(userByUuid.features), 0.0F)
        } else {
            null
        }
    }

    /**
     * 얼굴 정보 변경 완료
     */
    fun allowModifyFace(uuid: String) {
        // 현재 변경 신청 된 유저를 메인 유저로 등록한다.
        val entity = database.userDao.findUser(uuid)
        try {
            if (entity != null) {
                // 현재 유저 목록에서 제거한다.
                sharedPreferences.user?.apply { deleteFaceData(REGISTER_FACE, this) }
                deleteFaceData(MODIFY_FACE, uuid)
                // 이름을 변경한다.
                entity.name = uuid
                addFaceData(REGISTER_FACE, uuid, entity)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // fun newUser(name: String, hashFaceFeature: String) {
    // TODO 얼굴 이름 해시값 설정
    //}

    //fun changeOrNewUser(hashFaceFeature: String) {
    // TODO 얼굴 등록 유저 설정
    //}

    private companion object {
        private var TAG: String = FaceAuthenticatorManager::class.java.simpleName
        private const val USER_DATABASE = "USER_DATABASE"
    }

    class Builder(val context: Context) {
        fun build() = FaceAuthenticatorManager(this)
    }
}