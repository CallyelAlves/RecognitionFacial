package com.cordova.neurotechnology.utils;

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.TextureView;

public class AutoFitTextureView extends TextureView {

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    private final Matrix transformMatrix = new Matrix();

    public AutoFitTextureView(Context context) {
        this(context, null);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(width, height);
        applyTransform(width, height);
    }

    private void applyTransform(int viewWidth, int viewHeight) {
        if (viewWidth == 0 || viewHeight == 0 || mRatioWidth == 0 || mRatioHeight == 0) {
            resetTransform();
            return;
        }

        float bufferWidth = mRatioWidth;
        float bufferHeight = mRatioHeight;

        float scaleX = viewWidth / bufferWidth;
        float scaleY = viewHeight / bufferHeight;
        float maxScale = Math.max(scaleX, scaleY);

        if (Math.abs(scaleX - scaleY) < 0.001f) {
            resetTransform();
            return;
        }

        float finalScaleX = maxScale / scaleX;
        float finalScaleY = maxScale / scaleY;

        transformMatrix.reset();
        float pivotX = viewWidth / 2f;
        float pivotY = viewHeight / 2f;
        transformMatrix.postScale(finalScaleX, finalScaleY, pivotX, pivotY);
        setTransform(transformMatrix);
    }

    private void resetTransform() {
        transformMatrix.reset();
        setTransform(null);
    }
}
