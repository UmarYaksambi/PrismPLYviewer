package com.prism.plyviewer360;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * Custom GLSurfaceView with touch controls for 360° rotation and zoom
 */
public class ModelView extends GLSurfaceView {
    private PLYRenderer renderer;
    private ScaleGestureDetector scaleDetector;

    private float previousX;
    private float previousY;

    private static final float TOUCH_SCALE_FACTOR = 0.3f;

    public ModelView(Context context) {
        super(context);
        init(context);
    }

    public ModelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // Set OpenGL ES 2.0
        setEGLContextClientVersion(2);

        // Create renderer
        renderer = new PLYRenderer();
        setRenderer(renderer);

        // Render on demand (or RENDERMODE_CONTINUOUSLY for auto-rotation)
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // Initialize scale detector for pinch-to-zoom
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Handle pinch-to-zoom
        scaleDetector.onTouchEvent(event);

        // Handle rotation
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Stop auto-rotation when user touches
                renderer.setAutoRotate(false);
                previousX = x;
                previousY = y;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()) {
                    float dx = x - previousX;
                    float dy = y - previousY;

                    // Convert touch movement to rotation
                    float rotationX = dy * TOUCH_SCALE_FACTOR;
                    float rotationY = dx * TOUCH_SCALE_FACTOR;

                    renderer.addRotation(rotationX, rotationY);

                    previousX = x;
                    previousY = y;
                }
                break;
        }

        return true;
    }

    /**
     * Load PLY model into the renderer
     */
    public void loadModel(PLYParser.ParseResult parseResult) {
        queueEvent(() -> renderer.loadModel(parseResult));
    }

    /**
     * Reset view to initial state
     */
    public void resetView() {
        renderer.resetView();
    }

    /**
     * Toggle auto-rotation
     */
    public void toggleAutoRotate() {
        renderer.setAutoRotate(!renderer.isAutoRotate());
    }

    /**
     * Get the renderer (for direct access if needed)
     */
    public PLYRenderer getRenderer() {
        return renderer;
    }

    /**
     * Scale gesture listener for pinch-to-zoom
     */
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();

            // Convert scale to zoom (inverse relationship)
            float zoomDelta = (1.0f - scaleFactor) * 2.0f;
            renderer.addZoom(zoomDelta);

            return true;
        }
    }
}