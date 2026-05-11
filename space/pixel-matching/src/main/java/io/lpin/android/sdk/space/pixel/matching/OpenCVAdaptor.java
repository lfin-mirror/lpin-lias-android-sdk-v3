package io.lpin.android.sdk.space.pixel.matching;

import android.graphics.Bitmap;

import io.lpin.android.sdk.licensing.LiasApplicationContext;
import io.lpin.android.sdk.licensing.LiasLicensedFeature;
import io.lpin.android.sdk.licensing.LiasLicenseGate;
import java.nio.ByteBuffer;

public class OpenCVAdaptor {
    public OpenCVAdaptor() {
        LiasLicenseGate.requireFeature(LiasApplicationContext.requireApplicationContext(), LiasLicensedFeature.PIXEL_MATCHING);
        System.loadLibrary("opencv-adapter-lib");
    }

    public int process(Bitmap bitmap) {
        Bitmap bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        byte[] bitmapBytes = bitmapToByteArray(bmp32);
        return process(bitmapBytes, bmp32.getWidth(), bmp32.getHeight());
    }

    byte[] bitmapToByteArray(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocate(bitmap.getByteCount()); //바이트 버퍼를 이미지 사이즈 만큼 선언
        bitmap.copyPixelsToBuffer(buffer); //비트맵의 픽셀을 버퍼에 저장
        return buffer.array();
    }

    public native int initialize(double wrapper_version);

    public native int setMarker(byte[] bitmap, int width, int height);

    public native int startRetriveTargetData();

    public native int endRetriveTargetData();

    public native int startProcessQuery();

    public native int endProcessQuery();

    public native double getConfidenceRate();

    public native int process(byte[] bitmap, int width, int height);
}
