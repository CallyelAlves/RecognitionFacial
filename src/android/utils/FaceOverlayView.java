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
    private Paint paint;
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
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        path = new Path();
    }

    public void setOvalRect(RectF rect) {
        this.ovalRect = rect;
        invalidate(); // Redesenha a view
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (ovalRect != null) {
            path.reset();
            path.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
            path.addOval(ovalRect, Path.Direction.CCW);

            canvas.drawPath(path, paint);
        }
    }
}
