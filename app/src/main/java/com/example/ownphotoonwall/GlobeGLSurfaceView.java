package com.example.ownphotoonwall;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.view.MotionEvent;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GlobeGLSurfaceView extends GLSurfaceView {
    private GlobeRenderer renderer;
    private float previousX;
    private float previousY;

    public GlobeGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // 1. Core OpenGL Setup
        setEGLContextClientVersion(2);

        // Ensure the OpenGL window is completely transparent to show app theme!
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);

        renderer = new GlobeRenderer(context);
        setRenderer(renderer);

        // Continuously render frames (like a game engine)
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    // 2. Handle 360-degree True 3D Touch Rotation
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();

        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = x - previousX;
            float dy = y - previousY;

            // Adjust sensitivity here (0.2f feels natural)
            renderer.rotationX += dy * 0.2f;
            renderer.rotationY += dx * 0.2f;

            // Clamp X rotation so you don't accidentally flip the world upside down and reverse controls
            if (renderer.rotationX > 80.0f) renderer.rotationX = 80.0f;
            if (renderer.rotationX < -80.0f) renderer.rotationX = -80.0f;
        }

        previousX = x;
        previousY = y;
        return true;
    }

    // =========================================================
    // THE INTERNAL RENDERER LOOP
    // =========================================================
    private class GlobeRenderer implements GLSurfaceView.Renderer {
        private Context context;
        private Sphere sphere;
        private int textureId;

        // Camera matrices
        private final float[] vPMatrix = new float[16];
        private final float[] projectionMatrix = new float[16];
        private final float[] viewMatrix = new float[16];
        private final float[] modelMatrix = new float[16];
        private final float[] scratch = new float[16];

        public float rotationX = 0f; // Pitch (Up/Down)
        public float rotationY = 0f; // Yaw (Left/Right)

        private float autoSpin = 0f;

        public GlobeRenderer(Context context) {
            this.context = context;
        }

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            // Set background color to TRANSPARENT
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

            // Enable 3D Depth testing so the back of the globe doesn't render over the front
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);

            // Generate Sphere with high resolution (40 lat / 40 lon bands)
            sphere = new Sphere(1.5f, 40, 40);
            textureId = loadTexture(context, R.drawable.earth_map);
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            float ratio = (float) width / height;
            Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 1f, 10f);
        }

        @Override
        public void onDrawFrame(GL10 unused) {
            // Clear screen and depth buffer
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            // Set camera position
            Matrix.setLookAtM(viewMatrix, 0, 0, 0, -4f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
            Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

            // Calculate Model Rotations
            Matrix.setIdentityM(modelMatrix, 0);

            // Add a slow auto-spin that combines with user touch
            autoSpin -= 0.5f;

            // Apply True 3D Rotations (X for Pitch, Y for Yaw)
            Matrix.rotateM(modelMatrix, 0, rotationX, 1.0f, 0.0f, 0.0f);
            Matrix.rotateM(modelMatrix, 0, rotationY + autoSpin, 0.0f, 1.0f, 0.0f);

            // Combine matrices and draw
            Matrix.multiplyMM(scratch, 0, vPMatrix, 0, modelMatrix, 0);
            sphere.draw(scratch, textureId);
        }

        // Helper to load bitmap from resources into GPU memory
        private int loadTexture(Context context, int resourceId) {
            final int[] textureHandle = new int[1];
            GLES20.glGenTextures(1, textureHandle, 0);

            if (textureHandle[0] != 0) {
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inScaled = false;
                final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
                bitmap.recycle(); // Free up RAM
            }
            return textureHandle[0];
        }
    }
}