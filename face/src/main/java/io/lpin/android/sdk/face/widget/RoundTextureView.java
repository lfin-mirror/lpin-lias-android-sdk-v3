package io.lpin.android.sdk.face.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

public class RoundTextureView extends TextureView {
    private static final String TAG = "CustomTextureView";
    private int radius = 0;
    private int mRatioWidth;
    private int mRatioHeight;
    private boolean mWithMargin = true;

    public RoundTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                Rect rect = new Rect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                outline.setRoundRect(rect, radius);
            }
        });
        setClipToOutline(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int margin = (height - width) / 2;

        if (!mWithMargin) {
            mWithMargin = true;
            ViewGroup.MarginLayoutParams margins = ViewGroup.MarginLayoutParams.class.cast(getLayoutParams());
            margins.topMargin = -margin;
            margins.bottomMargin = -margin;
            margins.leftMargin = 0;
            margins.rightMargin = 0;
            setLayoutParams(margins);
        }

        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }

//    public void setMeasure(int ratioWidth, int ratioHeight) {
//        this.mRatioWidth = ratioWidth;
//        this.mRatioHeight = ratioHeight;
//
//        requestLayout();
//    }
//
//    @Override
//    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//        int width = MeasureSpec.getSize(widthMeasureSpec);
//        int height = MeasureSpec.getSize(heightMeasureSpec);
//        if (0 == mRatioWidth || 0 == mRatioHeight) {
//            setMeasuredDimension(width, height);
//        } else {
//            if (width < height * mRatioWidth / mRatioHeight) {
//                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
//            } else {
//                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
//            }
//        }
//    }

    public void turnRound() {
        invalidateOutline();
    }

    /**
     * min 0
     * max 90
     *
     * @param radius
     */
    public void setRadius(int radius) {
        this.radius = radius * Math.min(getLayoutParams().width, getLayoutParams().height) / 2 / 90;
        // this.radius = radius * 4;
        Log.d(TAG, "Frame radius: " + this.radius);
    }

    public int getRadius() {
        return radius;
    }
}