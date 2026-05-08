package io.lpin.android.sdk.face

object Constants {
    const val CROP_SIZE = 224
    const val CLIENT_ERROR_SDK_INIT = "11"
    const val CLIENT_ERROR_SDK_MESSAGE = "SDK 초기화가 필요합니다"

    // 안면인증
    const val CLIENT_ERROR_NO_FACE_CODE = "61"
    const val CLIENT_ERROR_NO_FACE_MESSAGE = "얼굴이 등록되지 않았습니다"
    const val CLIENT_ERROR_INVALID_HASH_CODE = "62"
    const val CLIENT_ERROR_INVALID_HASH_MESSAGE = "얼굴 Hash 값이 일치하지 않습니다"
    const val CLIENT_ERROR_ALREADY_REGISTERED_FACE_CODE = "63"
    const val CLIENT_ERROR_ALREADY_REGISTERED_FACE_MESSAGE = "얼굴이 이미 등록된 상태입니다"
    const val CLIENT_ERROR_CANCEL_FACE_CODE = "64"
    const val CLIENT_ERROR_CANCEL_FACE_MESSAGE = "안면인증 취소"
    const val CLIENT_ERROR_INVALID_FACE_CODE = "65"
    const val CLIENT_ERROR_INVALID_FACE_MESSAGE = "얼굴이 일치하지 않습니다"
}