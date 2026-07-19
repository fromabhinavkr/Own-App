package com.example.ownphotoonwall;

import android.content.Context; import android.graphics.Bitmap; import android.graphics.BitmapFactory; import android.graphics.BitmapShader; import android.graphics.Canvas; import android.graphics.Matrix; import android.graphics.Paint; import android.graphics.Shader; import android.util.AttributeSet; import android.view.GestureDetector; import android.view.MotionEvent; import android.view.View; import android.view.animation.DecelerateInterpolator; import android.widget.Scroller;

public class GlobeView extends View {
    private Bitmap originalMap; private BitmapShader shader; private Paint paint; private Matrix matrix; private float mapXOffset = 0; private float baseSpinSpeed = 1.5f; private float currentSpinSpeed = baseSpinSpeed; private GestureDetector gestureDetector; private Scroller scroller; private int currentPlanetIndex = 0; private final int[] maps = {R.drawable.earth_map, R.drawable.moon_map, R.drawable.mars_map, R.drawable.venus_map};

    public GlobeView(Context context, AttributeSet attrs) { super(context, attrs); init(context); }

    private void init(Context context) { setBackgroundColor(0x00000000); originalMap = BitmapFactory.decodeResource(getResources(), maps[currentPlanetIndex]); paint = new Paint(Paint.ANTI_ALIAS_FLAG); matrix = new Matrix(); scroller = new Scroller(context, new DecelerateInterpolator()); gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
        @Override public boolean onDown(MotionEvent e) { scroller.forceFinished(true); currentSpinSpeed = 0; return true; }
        @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) { mapXOffset += distanceX * 0.8f; invalidate(); return true; }
        @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) { currentSpinSpeed = velocityX * 0.05f; return true; }
        @Override public boolean onSingleTapUp(MotionEvent e) { currentSpinSpeed = 40f; return true; }
        @Override public boolean onDoubleTap(MotionEvent e) { currentPlanetIndex = (currentPlanetIndex + 1) % 4; updatePlanetMap(); return true; }
    }); }

    private void updatePlanetMap() { if (originalMap != null && !originalMap.isRecycled()) originalMap.recycle(); originalMap = BitmapFactory.decodeResource(getResources(), maps[currentPlanetIndex]); setupShader(getWidth(), getHeight()); invalidate(); }

    private void setupShader(int w, int h) { float radius = Math.min(w, h) / 2.1f; int diameter = (int) (radius * 2); if (originalMap != null && diameter > 0) { float scale = (float) diameter / originalMap.getHeight(); int newWidth = Math.round(originalMap.getWidth() * scale); Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalMap, newWidth, diameter, true); shader = new BitmapShader(scaledBitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP); paint.setShader(shader); } }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); setupShader(w, h); }

    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); if (shader == null) return; float width = getWidth(); float height = getHeight(); float radius = Math.min(width, height) / 2.1f; float cx = width / 2; float cy = height / 2; if (!scroller.isFinished()) { invalidate(); } else { if (Math.abs(currentSpinSpeed) > baseSpinSpeed) { currentSpinSpeed *= 0.96f; } else { currentSpinSpeed = baseSpinSpeed; } mapXOffset += currentSpinSpeed; } matrix.setTranslate(-mapXOffset, cy - radius); shader.setLocalMatrix(matrix); canvas.drawCircle(cx, cy, radius, paint); postInvalidateOnAnimation(); }

    @Override public boolean onTouchEvent(MotionEvent event) { boolean handled = gestureDetector.onTouchEvent(event); if (event.getAction() == MotionEvent.ACTION_UP && currentSpinSpeed == 0) { currentSpinSpeed = baseSpinSpeed; } return handled || super.onTouchEvent(event); }
}