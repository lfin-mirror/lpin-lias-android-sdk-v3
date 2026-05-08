package io.lpin.android.sdk.space

object Constants {
    const val BACKGROUND_RECEIVER_ACTION = "io.lpin.android.sdk.plac.background.receiver"
    const val TEXT_WARNING = "경고"

    const val SUCCESS_CODE = "00"
    const val SUCCESS_MESSAGE = "SUCCESS"
    const val CLIENT_ERROR_NO_PERMISSION_CODE = "13"
    const val CLIENT_ERROR_NO_PERMISSION_MESSAGE = "사용자 권한을 확인해 주세요"
    const val CLIENT_ERROR_NO_GPS_CODE = "15"
    const val CLIENT_ERROR_NO_GPS_MESSAGE = "위치가 켜져있는지 확인해 주세요"
    const val CLIENT_ERROR_NO_SIM_CODE = "16"
    const val CLIENT_ERROR_NO_SIM_MESSAGE = "유심 상태를 확인 해 주세요"
    const val CLIENT_ERROR_NO_BLE_CODE = "17"
    const val CLIENT_ERROR_NO_BLE_MESSAGE = "블루투스가 켜져있는지 확인해 주세요"
    const val CLIENT_NOT_SUPPORT_DEVICE_CODE = "19"
    const val CLIENT_NOT_SUPPORT_DEVICE_MESSAGE = "지원하지않는 단말입니다"

    // 권한
    const val CLIENT_ERROR_NO_CELL_PERMISSION_CODE = "20"
    const val CLIENT_ERROR_NO_CELL_PERMISSION_MESSAGE = "전화 권한을 확인 해 주세요"
    const val CLIENT_ERROR_TIMEOUT_CODE = "21"
    const val CLIENT_ERROR_TIMEOUT_MESSAGE = "시간이 초과되었습니다."
    const val CLIENT_ERROR_NO_GPS_PERMISSION_CODE = "22"
    const val CLIENT_ERROR_NO_GPS_PERMISSION_MESSAGE = "위치 권한을 확인 해 주세요"

    // 위치
    const val CLIENT_ERROR_NO_LOCATION_CODE = "31"
    const val CLIENT_ERROR_NO_LOCATION_MESSAGE = "등록되지 않은 위치입니다"
    const val CLIENT_ERROR_INVALID_LOCATION_CODE = "32"
    const val CLIENT_ERROR_INVALID_LOCATION_MESSAGE = "위치인증에 실패하였습니다"
    const val CLIENT_ERROR_ALREADY_REGISTERED_CODE = "33"
    const val CLIENT_ERROR_ALREADY_REGISTERED_MESSAGE = "위치데이터가 이미 등록되었습니다"
    const val CLIENT_ERROR_LOCATION_AUTH_WIFI_CODE = "34"
    const val CLIENT_ERROR_LOCATION_AUTH_WIFI_MESSAGE = "위치인증 와이파이 인증 실패"
    const val CLIENT_ERROR_LOCATION_AUTH_GPS_CODE = "35"
    const val CLIENT_ERROR_LOCATION_AUTH_GPS_MESSAGE = "위치인증 GPS 인증 실패"
    const val CLIENT_ERROR_INVALID_LOCATION_DATA_CODE = "36"
    const val CLIENT_ERROR_INVALID_LOCATION_DATA_MESSAGE = "위치정보가 유효하지 않습니다"
    const val CLIENT_ERROR_WIFI_THROTTLING_CODE = "37"
    const val CLIENT_ERROR_WIFI_THROTTLING_MESSAGE = "안드로이드 정책에 따른 인증 횟수 제한으로 2분 후에 재시도 해 주세요"

    // 공간
    const val CLIENT_ERROR_NO_SPACE_CODE = "38"
    const val CLIENT_ERROR_NO_SPACE_MESSAGE = "공간이미지가 등록되지 않았습니다"
    const val CLIENT_ERROR_INVALID_SPACE_CODE = "39"
    const val CLIENT_ERROR_INVALID_SPACE_MESSAGE = "공간이미지가 일치하지 않습니다"
    const val CLIENT_ERROR_ALREADY_REGISTERED_SPACE_CODE = "40"
    const val CLIENT_ERROR_ALREADY_REGISTERED_SPACE_MESSAGE = "공간이미지가 이미 등록되었습니다"
    const val CLIENT_ERROR_INVALID_DATA_CODE = "41"
    const val CLIENT_ERROR_INVALID_DATA_MESSAGE = "공간이미지 데이터가 올바르지 않습니다."
    const val CLIENT_ERROR_CANCEL_SPACE_CODE = "42"
    const val CLIENT_ERROR_CANCEL_SPACE_MESSAGE = "공간이미지 취소"
    const val CLIENT_ERROR_NO_OBJECT_CODE = "43"
    const val CLIENT_ERROR_NO_OBJECT_MESSAGE = "등록된 사물이 인식되지 않았습니다"
    const val CLIENT_ERROR_MORE_OBJECT_CODE = "44"
    const val CLIENT_ERROR_MORE_OBJECT_MESSAGE = "화면에 더 많은 사물이 보이도록 찍어주세요"
    const val CLIENT_ERROR_LOCATION_SPACE_FAILURE_CODE = "45"
    const val CLIENT_ERROR_LOCATION_SPACE_FAILURE_MESSAGE = "공간인증, 위치인증 모두 실패"

    // 기타
    const val CLIENT_UNKNOWN_ERROR_CODE = "99"
    const val CLIENT_UNKNOWN_ERROR_MESSAGE = "알수없는 에러가 발생했습니다"
}