package io.lpin.android.sdk.space

import android.graphics.Bitmap

interface SpaceAuthenticatorListener {

    fun getStatusNone() = "none"
    fun getStatusSuccess() = "success"
    fun getStatusFailure() = "failure"
    fun getStatusWaiting() = "waiting"

    /**
     * 위치인증 상태를 받아와서 공간인증 처리를 진행한다.
     */
    fun getLocationAuthStatus(): String {
        return getStatusNone()
    }

    /**
     * 공간 이미지 수집 시작 시
     */
    fun takePictureStarted()

    fun takePictureCompareResult(spaceDistance: Double, pixelScore: Float, emailMessage: String = ""): Boolean {
        return false
    }

    /**
     * 공간이미지 데이터
     *
     * @param bitmap screenCapture
     * @param spaceImage   공간이미지 Base64 Encoding 이미지
     * @param spaceFeature 공간이미지 Feature Hash 값
     */
    fun takePictureSuccess(bitmap: Bitmap, spaceImage: String, spaceFeature: String, spaceFeatureValue: String)

    /**
     * 공간이미지 생성/인증 실패
     */
    fun takePictureFailure(code: String, message: String)
}