package com.example.ownphotoonwall;

import android.content.Context; import android.graphics.Bitmap; import android.graphics.BitmapFactory; import android.graphics.PixelFormat; import android.opengl.GLES20; import android.opengl.GLSurfaceView; import android.opengl.GLUtils; import android.opengl.Matrix; import android.util.AttributeSet; import android.view.GestureDetector; import android.view.MotionEvent; import javax.microedition.khronos.egl.EGLConfig; import javax.microedition.khronos.opengles.GL10;

public class GlobeGLSurfaceView extends GLSurfaceView {
    private GlobeRenderer renderer; private float previousX; private float previousY; private GestureDetector gestureDetector; private int currentPlanetIndex = 0; private final int[] maps = {R.drawable.earth_map, R.drawable.moon_map, R.drawable.mars_map, R.drawable.venus_map};

    public GlobeGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs); setEGLContextClientVersion(2); setEGLConfigChooser(8, 8, 8, 8, 16, 0); getHolder().setFormat(PixelFormat.TRANSLUCENT); setZOrderOnTop(true);
        renderer = new GlobeRenderer(context); setRenderer(renderer); setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) { currentPlanetIndex = (currentPlanetIndex + 1) % 4; queueEvent(() -> renderer.changeTexture(maps[currentPlanetIndex])); return true; }
        });
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        gestureDetector.onTouchEvent(e); float x = e.getX(); float y = e.getY();
        if (e.getAction() == MotionEvent.ACTION_MOVE) { float dx = x - previousX; float dy = y - previousY; renderer.rotationX -= dy * 0.2f; renderer.rotationY += dx * 0.2f; if (renderer.rotationX > 80.0f) renderer.rotationX = 80.0f; if (renderer.rotationX < -80.0f) renderer.rotationX = -80.0f; }
        previousX = x; previousY = y; return true;
    }

    private class GlobeRenderer implements GLSurfaceView.Renderer {
        private Context context; private Sphere sphere; private int textureId; private final float[] vPMatrix = new float[16]; private final float[] projectionMatrix = new float[16]; private final float[] viewMatrix = new float[16]; private final float[] modelMatrix = new float[16]; private final float[] scratch = new float[16]; public float rotationX = 0f; public float rotationY = 0f; private float autoSpin = 0f;
        public GlobeRenderer(Context context) { this.context = context; }
        @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) { GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f); GLES20.glEnable(GLES20.GL_DEPTH_TEST); sphere = new Sphere(1.5f, 40, 40); textureId = loadTexture(context, R.drawable.earth_map); }
        @Override public void onSurfaceChanged(GL10 unused, int width, int height) { GLES20.glViewport(0, 0, width, height); float ratio = (float) width / height; Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 1f, 10f); }
        @Override public void onDrawFrame(GL10 unused) { GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT); Matrix.setLookAtM(viewMatrix, 0, 0, 0, -4f, 0f, 0f, 0f, 0f, 1.0f, 0.0f); Matrix.multiplyMM(vPMatrix, 0, projectionMatrix, 0, viewMatrix, 0); Matrix.setIdentityM(modelMatrix, 0); autoSpin -= 0.5f; Matrix.rotateM(modelMatrix, 0, rotationX, 1.0f, 0.0f, 0.0f); Matrix.rotateM(modelMatrix, 0, rotationY + autoSpin, 0.0f, 1.0f, 0.0f); Matrix.multiplyMM(scratch, 0, vPMatrix, 0, modelMatrix, 0); sphere.draw(scratch, textureId); }
        public void changeTexture(int resourceId) { int oldTex = textureId; textureId = loadTexture(context, resourceId); if (oldTex != 0) { GLES20.glDeleteTextures(1, new int[]{oldTex}, 0); } }
        private int loadTexture(Context context, int resourceId) { final int[] textureHandle = new int[1]; GLES20.glGenTextures(1, textureHandle, 0); if (textureHandle[0] != 0) { final BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false; final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR); GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0); bitmap.recycle(); } return textureHandle[0]; }
    }
}