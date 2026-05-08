package io.lpin.android.sdk.face.core.camera;


import android.hardware.camera2.CameraDevice;
import android.media.Image;
import android.util.Size;

public abstract class Camera2Listener {
    public abstract void onCameraOpened(CameraDevice cameraDevice, String cameraId, Size previewSize, int displayOrientation, boolean isMirror);

    public void onPreview(Image image, byte[] y, byte[] u, byte[] v, Size previewSize, int yRowStride, int uvRowStride, int uvPixelStride) {

    }

    public abstract void onPreview(Image image, Size size);

    public abstract void onCameraClosed();

    public abstract void onCameraError(Exception e);

    private boolean isProcessing = false;

    public void startProcessingFrame() {
        isProcessing = true;
    }

    public Boolean isProcessingFrame() {
        return isProcessing;
    }

    public void stopProcessingFrame() {
        isProcessing = false;
    }
}