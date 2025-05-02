package com.cordova.neurotechnology.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.graphics.Path;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff;

public class FaceOverlayView extends View {
    private Paint maskPaint;
    private Paint borderPaint;
    private RectF ovalRect;
    private Path path;

    public FaceOverlayView(Context context) {
        super(context);
        init();
    }

    public FaceOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public RectF getOvalRect() {
        return ovalRect;
    }

    private void init() {
        maskPaint = new Paint();
        maskPaint.setColor(Color.parseColor("#88000000"));
        maskPaint.setStyle(Paint.Style.FILL);
        maskPaint.setAntiAlias(true);

        borderPaint = new Paint();
        borderPaint.setColor(Color.RED);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(10);
        borderPaint.setAntiAlias(true);

        path = new Path();
    }

    public void setOvalRect(RectF rect) {
        this.ovalRect = rect;
        invalidate();
    }

    public void setBorderColor(int color) {
        borderPaint.setColor(color);
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (ovalRect != null) {
            path.reset();
            path.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
            path.addOval(ovalRect, Path.Direction.CCW);
            canvas.drawPath(path, maskPaint);
            canvas.drawOval(ovalRect, borderPaint);
        }
    }
}
