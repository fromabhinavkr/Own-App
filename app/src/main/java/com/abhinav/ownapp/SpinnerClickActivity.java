package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.ImageView;
import android.graphics.PixelFormat;
public class SpinnerClickActivity extends Activity {

    private ImageView imgSpinner;
    private float currentRotation = 0f;
    private float velocity = 0f;
    private boolean isSpinning = false;

    // Physics tracking
    private float lastX = 0f;
    private float lastY = 0f;
    private long lastTime = 0;

    // Smoothed velocity tracking (fixes direction-flip bug)
    private VelocityTracker velocityTracker = null;

    // Haptic engine variables
    private Vibrator vibrator;
    private float hapticAccumulator = 0f;
    private long lastHapticTime = 0;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!isSpinning) return;

            currentRotation += velocity;
            imgSpinner.setRotation(currentRotation);

            processHaptics(Math.abs(velocity));

            velocity *= 0.9995f;

            if (Math.abs(velocity) < 0.05f) {
                isSpinning = false;
                velocity = 0f;
            } else {
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // FIX: Semi-transparent window so the home screen shows through faintly
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#66000000")));
        setContentView(R.layout.activity_spinner_click);

        imgSpinner = findViewById(R.id.imgLargeSpinner);
        View root = findViewById(R.id.spinnerRoot);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            @SuppressWarnings("deprecation")
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vibrator = v;
        }

        root.setOnClickListener(v -> finish());
        imgSpinner.setOnClickListener(null);

        imgSpinner.setOnTouchListener((v, event) -> {
            float cx = v.getWidth() / 2f;
            float cy = v.getHeight() / 2f;

            float x = event.getX();
            float y = event.getY();
            long currentTime = System.currentTimeMillis();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isSpinning = false;
                    velocity = 0f;
                    lastX = x;
                    lastY = y;
                    lastTime = currentTime;

                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    } else {
                        velocityTracker.clear();
                    }
                    velocityTracker.addMovement(event);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (velocityTracker != null) {
                        velocityTracker.addMovement(event);
                    }

                    float dx = x - lastX;
                    float dy = y - lastY;

                    float rx = x - cx;
                    float ry = y - cy;

                    float crossProduct = (rx * dy) - (ry * dx);
                    float distanceSq = (rx * rx) + (ry * ry);
                    float deltaAngle = 0f;

                    if (distanceSq > 0) {
                        deltaAngle = (float) ((crossProduct / distanceSq) * (180f / Math.PI));
                    }

                    currentRotation += deltaAngle;
                    imgSpinner.setRotation(currentRotation);

                    processHaptics(Math.abs(deltaAngle));

                    lastX = x;
                    lastY = y;
                    lastTime = currentTime;
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.performClick();

                    velocity = computeReleaseAngularVelocity(event, cx, cy);

                    if (velocity > 350f) velocity = 350f;
                    if (velocity < -350f) velocity = -350f;

                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }

                    if (Math.abs(velocity) > 1f) {
                        isSpinning = true;
                        Choreographer.getInstance().postFrameCallback(frameCallback);
                    }
                    return true;
            }
            return false;
        });
    }

    // Converts the smoothed linear finger velocity (from VelocityTracker) into
    // angular velocity around the spinner's pivot, using the SAME sign convention
    // as the live drag rotation above, so the direction always matches the gesture.
    private float computeReleaseAngularVelocity(MotionEvent event, float cx, float cy) {
        if (velocityTracker == null) return 0f;

        velocityTracker.addMovement(event);
        // 1000 = compute in pixels/second
        velocityTracker.computeCurrentVelocity(1000);

        float vx = velocityTracker.getXVelocity();
        float vy = velocityTracker.getYVelocity();

        float rx = lastX - cx;
        float ry = lastY - cy;
        float distanceSq = (rx * rx) + (ry * ry);

        if (distanceSq <= 0) return 0f;

        float crossProduct = (rx * vy) - (ry * vx);
        float angularVelocityDegPerSec = (float) ((crossProduct / distanceSq) * (180f / Math.PI));

        // Convert deg/sec -> deg/frame (~16ms) to match the units used elsewhere
        return angularVelocityDegPerSec * (16f / 1000f);
    }

    private void processHaptics(float degreesMoved) {
        hapticAccumulator += degreesMoved;

        if (hapticAccumulator >= 60f) {
            hapticAccumulator %= 60f;
            triggerHapticTick(Math.abs(velocity));
        }
    }

    private void triggerHapticTick(float currentSpeed) {
        if (vibrator == null || !vibrator.hasVibrator()) return;

        long now = System.currentTimeMillis();
        if (now - lastHapticTime < 20) return;
        lastHapticTime = now;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int strength = (currentSpeed > 15f) ? 120 : 60;
            long duration = 15;
            vibrator.vibrate(VibrationEffect.createOneShot(duration, strength));
        } else {
            imgSpinner.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isSpinning = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }
}