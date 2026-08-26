package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/** Image preview with pinch zoom, buttons and basic panning. */
public final class ZoomableImageView extends AppCompatImageView {
    private final Matrix imageMatrixState = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private float baseScale = 1f;
    private float currentScale = 1f;
    private float maximumScale = 4f;
    private float lastX;
    private float lastY;

    public ZoomableImageView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float target = currentScale * detector.getScaleFactor();
                        float clamped = Math.max(baseScale, Math.min(maximumScale, target));
                        float factor = clamped / currentScale;
                        imageMatrixState.postScale(
                                factor, factor, detector.getFocusX(), detector.getFocusY());
                        currentScale = clamped;
                        setImageMatrix(imageMatrixState);
                        return true;
                    }
                });
        setFocusable(true);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        resetZoom();
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        post(this::resetZoom);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && event.getPointerCount() == 1
                        && currentScale > baseScale) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    imageMatrixState.postTranslate(dx, dy);
                    setImageMatrix(imageMatrixState);
                    lastX = event.getX();
                    lastY = event.getY();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (event.getActionMasked() == MotionEvent.ACTION_UP
                        && !scaleDetector.isInProgress()) {
                    performClick();
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public void zoomBy(float factor) {
        if (factor <= 0f || getDrawable() == null) {
            return;
        }
        float target = Math.max(baseScale, Math.min(maximumScale, currentScale * factor));
        float applied = target / currentScale;
        imageMatrixState.postScale(applied, applied, getWidth() / 2f, getHeight() / 2f);
        currentScale = target;
        setImageMatrix(imageMatrixState);
    }

    public void resetZoom() {
        if (getDrawable() == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        float imageWidth = getDrawable().getIntrinsicWidth();
        float imageHeight = getDrawable().getIntrinsicHeight();
        if (imageWidth <= 0f || imageHeight <= 0f) {
            return;
        }
        baseScale = Math.min(getWidth() / imageWidth, getHeight() / imageHeight);
        if (baseScale <= 0f) {
            baseScale = 1f;
        }
        maximumScale = baseScale * 4f;
        currentScale = baseScale;
        imageMatrixState.reset();
        imageMatrixState.postScale(baseScale, baseScale);
        float left = (getWidth() - imageWidth * baseScale) / 2f;
        float top = (getHeight() - imageHeight * baseScale) / 2f;
        imageMatrixState.postTranslate(left, top);
        setImageMatrix(imageMatrixState);
    }
}
