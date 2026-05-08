package io.lpin.android.sdk.face.core.camera;

import android.hardware.Camera;


public interface Camera1Listener {
    void onCameraOpened(Camera camera, int cameraId, int displayOrientation, boolean isMirror);
    void onPreview(byte[] data, Camera camera);
    void onCameraClosed();
    void onCameraError(Exception e);
    void onCameraConfigurationChanged(int cameraID, int displayOrientation);
}
