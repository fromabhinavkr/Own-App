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
import android.view.View;
import android.widget.ImageView;
import android.graphics.PixelFormat;

public class SpinnerClickActivity extends Activity {

    private ImageView imgSpinner;
    private float currentRotation = 0f;
    private float velocity = 0f;
    private boolean isSpinning = false;

    // Physics Constants
    // 60f = 10 revolutions per second (Fastest visual speed without causing the wagon-wheel illusion)
    private static final float MAX_VELOCITY = 60f;
    // Tuned so a good swipe maxes it out
    private static final float VELOCITY_MULTIPLIER = 25f;
    // Ultra-low friction perfectly adjusted to keep the 6 to 7 minute spin duration
    private static final float FRICTION = 0.99970f;

    // Touch & Momentum tracking
    private float lastTouchAngle = 0f;
    private float lastVelocityAngle = 0f;
    private float spinVelocity = 0f;
    private long lastVelocityTime = 0;

    // Tap-to-stop tracking
    private long touchDownTime = 0;
    private float totalSwipeAngle = 0f;

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

            // Haptics now perfectly follow the visual math
            processHaptics(Math.abs(velocity), Math.abs(velocity));

            // Apply friction
            velocity *= FRICTION;

            // Stop threshold
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

            // Calculate angle once per event to satisfy IDE warnings
            float currentTouchAngle = (float) Math.toDegrees(Math.atan2(y - cy, x - cx));

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Pause free-spin loop while finger is on the spinner
                    isSpinning = false;
                    Choreographer.getInstance().removeFrameCallback(frameCallback);

                    spinVelocity = 0f;
                    totalSwipeAngle = 0f;
                    touchDownTime = currentTime;

                    lastTouchAngle = currentTouchAngle;
                    lastVelocityAngle = currentTouchAngle;
                    lastVelocityTime = currentTime;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    // 1. Live visual drag
                    float deltaAngle = currentTouchAngle - lastTouchAngle;
                    if (deltaAngle > 180f) deltaAngle -= 360f;
                    else if (deltaAngle < -180f) deltaAngle += 360f;

                    totalSwipeAngle += Math.abs(deltaAngle);
                    currentRotation += deltaAngle;
                    imgSpinner.setRotation(currentRotation);

                    // Passing deltaAngle ensures perfect drag vibrations
                    processHaptics(Math.abs(deltaAngle), Math.abs(deltaAngle));

                    lastTouchAngle = currentTouchAngle;

                    // 2. Velocity sampling (avoiding end-of-swipe jitter)
                    long timeDelta = currentTime - lastVelocityTime;
                    if (timeDelta > 15) {
                        float velDeltaAngle = currentTouchAngle - lastVelocityAngle;
                        if (velDeltaAngle > 180f) velDeltaAngle -= 360f;
                        else if (velDeltaAngle < -180f) velDeltaAngle += 360f;

                        float instantVelocity = velDeltaAngle / timeDelta; // deg/ms
                        spinVelocity = (spinVelocity * 0.6f) + (instantVelocity * 0.4f);

                        lastVelocityAngle = currentTouchAngle;
                        lastVelocityTime = currentTime;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.performClick();

                    long touchDuration = currentTime - touchDownTime;
                    long timeSinceLastMove = currentTime - lastVelocityTime;

                    // TAP / CLICK DETECTION:
                    boolean isTap = (totalSwipeAngle < 8f && touchDuration < 250);
                    boolean isHoldStill = (timeSinceLastMove > 80 && Math.abs(spinVelocity) < 0.05f);

                    if (isTap || isHoldStill) {
                        // Stop the spinner completely on click
                        velocity = 0f;
                        isSpinning = false;
                        spinVelocity = 0f;
                        triggerHapticTick(30f); // Light tactile click confirming stop
                        return true;
                    }

                    // SWIPE LOGIC
                    velocity = spinVelocity * VELOCITY_MULTIPLIER;

                    // Cap maximum release velocity based on the new visual limit
                    if (velocity > MAX_VELOCITY) velocity = MAX_VELOCITY;
                    if (velocity < -MAX_VELOCITY) velocity = -MAX_VELOCITY;

                    // Start spinning if speed is above minimum threshold
                    if (Math.abs(velocity) > 0.5f) {
                        isSpinning = true;
                        Choreographer.getInstance().postFrameCallback(frameCallback);
                    } else {
                        velocity = 0f;
                        isSpinning = false;
                    }
                    return true;
            }
            return false;
        });
    }

    private void processHaptics(float degreesMoved, float currentSpeed) {
        hapticAccumulator += degreesMoved;

        if (hapticAccumulator >= 45f) {
            hapticAccumulator %= 45f;
            triggerHapticTick(currentSpeed);
        }
    }

    private void triggerHapticTick(float currentSpeed) {
        if (vibrator == null || !vibrator.hasVibrator()) return;

        long now = System.currentTimeMillis();
        if (now - lastHapticTime < 15) return;
        lastHapticTime = now;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Scales smoothly from 10 to 140 strength based on the new visual max speed
            int strength = (int) (10 + (currentSpeed / MAX_VELOCITY) * 130);

            // Hard safety cap at 140
            if (strength > 140) strength = 140;
            if (strength < 10) strength = 10;

            long duration = 10;
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
    }
}