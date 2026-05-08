package io.lpin.android.sdk.face;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.Keep;

@Keep
public class Face {
    public static int faceSize = 112;

    private RectF boundingBox;
    private int _id;
    private byte[] byteArray;
    private float[] floatArray;

    public Face(float left, float top, float right, float bottom) {
        boundingBox = new RectF(left, top, right, bottom);
    }

    public int getId() {
        return _id;
    }

    protected void setId(int value) {
        _id = value;
    }

    public final RectF getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(RectF boundingBox) {
        this.boundingBox = boundingBox;
    }

    public final PointF getPosition() {
        return new PointF(boundingBox.left, boundingBox.top);
    }

    public float getWidth() {
        return boundingBox.width();
    }

    public float getHeight() {
        return boundingBox.height();
    }

    public final byte[] getByteArray() {
        if (byteArray == null)
            byteArray = new byte[faceSize * faceSize * 3];
        return byteArray;
    }

    public void setByteArray(byte[] byteArray) {
        this.byteArray = byteArray;
    }

    public final float[] getFloatArray() {
        if (floatArray == null)
            floatArray = new float[faceSize * faceSize * 3];
        return floatArray;
    }

    public void setFloatArray(float[] floatArray) {
        this.floatArray = floatArray;
    }
}
