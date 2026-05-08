-keep class io.lpin.android.sdk.face.Face {
    public static int faceSize;
    public <init>(float, float, float, float);
    public final android.graphics.RectF getBoundingBox();
    public final byte[] getByteArray();
    public final float[] getFloatArray();
    public void setByteArray(byte[]);
    public void setFloatArray(float[]);
}

-keepclasseswithmembernames class io.lpin.android.sdk.face.FaceDetector {
    native <methods>;
}
