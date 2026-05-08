package io.lpin.android.sdk.face

import android.graphics.Bitmap
import io.lpin.android.sdk.face.model.CameraFrameData

interface LFaceCameraFragmentListener {
    fun processImage(frameData: CameraFrameData)
}