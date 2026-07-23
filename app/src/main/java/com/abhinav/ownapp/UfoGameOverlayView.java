package com.abhinav.ownapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class UfoGameOverlayView extends View {
    private final Paint hudPaint;
    private final Paint ufoBasePaint;
    private final Paint ufoHighlightPaint;
    private final Paint ufoDomePaint;
    private final Paint particlePaint;

    private final Paint charredPaint;
    private final Paint damagePaint;
    private final Paint emberGlowPaint;
    private final Paint emberPaint;

    private final Paint shieldDomePaint;
    private final Paint shieldGlowPaint;
    private final Paint shieldCorePaint;
    private final Paint shieldAuraPaint;
    private final Paint shieldDivinePulsePaint;

    private int score = 0;
    private int lives = 3;

    public static final int STATE_WAITING = 0;
    public static final int STATE_PLAYING = 1;
    public static final int STATE_GAME_OVER = 2;
    private int gameState = STATE_WAITING;

    private final ArrayList<Ufo> ufoList = new ArrayList<>();
    private final ArrayList<Explosion> explosions = new ArrayList<>();
    private final ArrayList<DamageSpot> damages = new ArrayList<>();

    private final ArrayList<Ufo> ufoPool = new ArrayList<>();
    private final ArrayList<Explosion> explosionPool = new ArrayList<>();
    private final ArrayList<DamageSpot> damagePool = new ArrayList<>();

    private static final Random random = new Random();
    private float planetRadius;

    // Decreased speed for a creeping swarm effect
    private float baseSpeed = 3.5f;

    private long shieldEndTime = 0;
    private float waveAnimRadius = 0;

    private final Vibrator vibrator;

    public interface OnGameOverListener {
        void onGameOver(int finalScore);
    }
    private OnGameOverListener gameOverListener;

    public void setOnGameOverListener(OnGameOverListener listener) {
        this.gameOverListener = listener;
    }

    private static class Ufo {
        float x, y, speed, dx, dy;
        int type;
    }

    private static class Particle {
        float x, y, dx, dy, radius;
        int alpha;
        int color;
    }

    private static class DamageSpot {
        float x, y, radius;
        public void init(float x, float y, float radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    private static class Explosion {
        final ArrayList<Particle> particles = new ArrayList<>();

        public Explosion() {
            for (int i = 0; i < 25; i++) {
                particles.add(new Particle());
            }
        }

        public void init(float ex, float ey, int ufoType) {
            int[] colors;
            switch (ufoType) {
                case 1: colors = new int[]{Color.parseColor("#FFEA00"), Color.parseColor("#FFAB00"), Color.WHITE}; break;
                case 2: colors = new int[]{Color.parseColor("#FF4081"), Color.parseColor("#E040FB"), Color.WHITE}; break;
                case 3: colors = new int[]{Color.parseColor("#FF1744"), Color.parseColor("#D50000"), Color.WHITE}; break;
                default: colors = new int[]{Color.parseColor("#00E5FF"), Color.parseColor("#69F0AE"), Color.WHITE}; break;
            }

            for (Particle p : particles) {
                p.x = ex;
                p.y = ey;
                double angle = Math.random() * 2 * Math.PI;
                float speed = (float) (Math.random() * 12 + 4);
                p.dx = (float) Math.cos(angle) * speed;
                p.dy = (float) Math.sin(angle) * speed;
                p.radius = (float) (Math.random() * 8 + 4);
                p.alpha = 255;
                p.color = colors[random.nextInt(colors.length)];
            }
        }
    }

    public UfoGameOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudPaint.setColor(Color.parseColor("#4A90E2"));
        hudPaint.setTextSize(65f);
        hudPaint.setTypeface(Typeface.DEFAULT_BOLD);
        hudPaint.setTextAlign(Paint.Align.CENTER);

        ufoBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ufoBasePaint.setStyle(Paint.Style.FILL);
        ufoHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ufoHighlightPaint.setStyle(Paint.Style.FILL);
        ufoDomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ufoDomePaint.setStyle(Paint.Style.FILL);
        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStyle(Paint.Style.FILL);

        charredPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        charredPaint.setColor(Color.parseColor("#99000000"));
        charredPaint.setStyle(Paint.Style.FILL);

        damagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        damagePaint.setColor(Color.BLACK);
        damagePaint.setStyle(Paint.Style.FILL);

        emberGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emberGlowPaint.setColor(Color.parseColor("#D84315"));
        emberGlowPaint.setAlpha(150);
        emberGlowPaint.setStyle(Paint.Style.FILL);

        emberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emberPaint.setColor(Color.parseColor("#FFD54F"));
        emberPaint.setStyle(Paint.Style.FILL);

        shieldDomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shieldDomePaint.setColor(Color.parseColor("#00B0FF"));
        shieldDomePaint.setAlpha(40);
        shieldDomePaint.setStyle(Paint.Style.FILL);

        shieldGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shieldGlowPaint.setColor(Color.parseColor("#00E5FF"));
        shieldGlowPaint.setStyle(Paint.Style.STROKE);
        shieldGlowPaint.setStrokeWidth(50f);

        shieldCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shieldCorePaint.setColor(Color.WHITE);
        shieldCorePaint.setStyle(Paint.Style.STROKE);
        shieldCorePaint.setStrokeWidth(15f);

        shieldAuraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shieldAuraPaint.setColor(Color.parseColor("#FFD700"));
        shieldAuraPaint.setStyle(Paint.Style.STROKE);
        shieldAuraPaint.setStrokeWidth(8f);

        shieldDivinePulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shieldDivinePulsePaint.setColor(Color.parseColor("#E0F7FA"));
        shieldDivinePulsePaint.setStyle(Paint.Style.STROKE);
        shieldDivinePulsePaint.setStrokeWidth(25f);

        for (int i = 0; i < 40; i++) ufoPool.add(new Ufo());
        for (int i = 0; i < 30; i++) explosionPool.add(new Explosion());
        for (int i = 0; i < 60; i++) damagePool.add(new DamageSpot());
    }

    private void triggerVibration() {
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(300);
            }
        }
    }

    public void startGame() {
        if (gameState == STATE_PLAYING) return;
        score = 0;
        lives = 3;

        ufoPool.addAll(ufoList);
        ufoList.clear();
        explosionPool.addAll(explosions);
        explosions.clear();
        damagePool.addAll(damages);
        damages.clear();

        // Speed is initialized to the new slower constant
        baseSpeed = 3.5f;

        shieldEndTime = 0;
        waveAnimRadius = 0;
        gameState = STATE_PLAYING;
        invalidate();
    }

    public boolean handleTouch(float tx, float ty) {
        if (gameState != STATE_PLAYING) return false;

        Iterator<Ufo> iterator = ufoList.iterator();
        while (iterator.hasNext()) {
            Ufo ufo = iterator.next();
            float dist = (float) Math.hypot(tx - ufo.x, ty - ufo.y);

            if (dist < 110f) {
                if (!explosionPool.isEmpty()) {
                    Explosion exp = explosionPool.remove(explosionPool.size() - 1);
                    exp.init(ufo.x, ufo.y, ufo.type);
                    explosions.add(exp);
                }

                iterator.remove();
                ufoPool.add(ufo);

                score++;

                // Reward the player by restoring their lives to full upon completing a wave!
                if (score > 0 && score % 25 == 0) {
                    shieldEndTime = System.currentTimeMillis() + 3000;
                    waveAnimRadius = planetRadius;
                    lives = 3;
                }

                invalidate();
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;

        planetRadius = getResources().getDisplayMetrics().density * 135f;

        if (gameState == STATE_WAITING) return;

        for (DamageSpot spot : damages) {
            canvas.drawCircle(spot.x, spot.y, spot.radius * 1.4f, charredPaint);
            canvas.drawCircle(spot.x, spot.y, spot.radius, damagePaint);
            canvas.drawCircle(spot.x, spot.y, spot.radius * 0.5f, emberGlowPaint);
            canvas.drawCircle(spot.x, spot.y, spot.radius * 0.25f, emberPaint);
        }

        boolean isShieldActive = System.currentTimeMillis() < shieldEndTime;

        if (isShieldActive) {
            canvas.drawCircle(cx, cy, planetRadius + 30, shieldDomePaint);
        }

        if (waveAnimRadius > 0) {
            if (gameState == STATE_PLAYING) {
                waveAnimRadius += 18f;
                float maxWaveRadius = Math.max(getWidth(), getHeight()) + 100f;
                if (waveAnimRadius > maxWaveRadius) {
                    if (isShieldActive) {
                        waveAnimRadius = planetRadius;
                    } else {
                        waveAnimRadius = 0;
                    }
                }
            }

            if (waveAnimRadius > 0) {
                float maxWaveRadius = Math.max(getWidth(), getHeight()) + 100f;
                float waveProgress = (waveAnimRadius - planetRadius) / (maxWaveRadius - planetRadius);
                int waveAlpha = Math.max(0, (int) (255 * (1f - waveProgress)));

                shieldGlowPaint.setAlpha(Math.max(0, (int)(90 * (1f - waveProgress))));
                shieldCorePaint.setAlpha(waveAlpha);
                shieldAuraPaint.setAlpha(waveAlpha);
                shieldDivinePulsePaint.setAlpha(Math.max(0, (int)(waveAlpha * 0.7f)));

                canvas.drawCircle(cx, cy, waveAnimRadius, shieldGlowPaint);
                canvas.drawCircle(cx, cy, waveAnimRadius, shieldCorePaint);
                canvas.drawCircle(cx, cy, waveAnimRadius - 15f, shieldDivinePulsePaint);
                canvas.drawCircle(cx, cy, waveAnimRadius + 45f, shieldAuraPaint);
                canvas.drawCircle(cx, cy, waveAnimRadius - 35f, shieldAuraPaint);
            }
        }

        if (gameState == STATE_PLAYING) {

            // --- DIFFICULTY PHASES ---
            int maxUfos;
            float spawnChance;

            if (score >= 50) {
                // DIFFICULT PHASE
                maxUfos = 7;
                spawnChance = 0.06f;
            } else if (score >= 25) {
                // MEDIUM PHASE
                maxUfos = 5;
                spawnChance = 0.04f;
            } else {
                // EASY PHASE
                maxUfos = 3;
                spawnChance = 0.02f;
            }

            // Strictly enforce the maximum UFO cap for the current phase!
            if (ufoList.size() < maxUfos) {
                if (random.nextFloat() < spawnChance) {
                    spawnUfo(cx, cy);
                }
            }

            Iterator<Ufo> iterator = ufoList.iterator();
            while (iterator.hasNext()) {
                Ufo ufo = iterator.next();
                ufo.x += ufo.dx * ufo.speed;
                ufo.y += ufo.dy * ufo.speed;

                float dist = (float) Math.hypot(cx - ufo.x, cy - ufo.y);

                if (waveAnimRadius > 0 && dist < waveAnimRadius + 60) {
                    if (!explosionPool.isEmpty()) {
                        Explosion exp = explosionPool.remove(explosionPool.size() - 1);
                        exp.init(ufo.x, ufo.y, ufo.type);
                        explosions.add(exp);
                    }
                    iterator.remove();
                    ufoPool.add(ufo);
                    continue;
                }

                if (dist < planetRadius) {
                    if (!explosionPool.isEmpty()) {
                        Explosion exp = explosionPool.remove(explosionPool.size() - 1);
                        exp.init(ufo.x, ufo.y, ufo.type);
                        explosions.add(exp);
                    }

                    float angle = (float) Math.atan2(ufo.y - cy, ufo.x - cx);
                    float impactX = cx + (float)Math.cos(angle) * (planetRadius - 35);
                    float impactY = cy + (float)Math.sin(angle) * (planetRadius - 35);

                    if (!damagePool.isEmpty()) {
                        DamageSpot ds = damagePool.remove(damagePool.size() - 1);
                        ds.init(impactX, impactY, random.nextFloat() * 14 + 18);
                        damages.add(ds);
                    }

                    triggerVibration();

                    iterator.remove();
                    ufoPool.add(ufo);
                    lives--;

                    if (lives <= 0 && gameState != STATE_GAME_OVER) {
                        gameState = STATE_GAME_OVER;
                        if (gameOverListener != null) {
                            gameOverListener.onGameOver(score);
                        }
                    }
                }
            }
        }

        for (Ufo ufo : ufoList) {
            drawCustomUfo(canvas, ufo);
        }

        Iterator<Explosion> expIt = explosions.iterator();
        while (expIt.hasNext()) {
            Explosion exp = expIt.next();
            boolean stillActive = false;

            for (Particle p : exp.particles) {
                if (p.alpha > 0) {
                    stillActive = true;
                    particlePaint.setColor(p.color);
                    particlePaint.setAlpha(p.alpha);
                    canvas.drawCircle(p.x, p.y, p.radius, particlePaint);

                    p.x += p.dx;
                    p.y += p.dy;
                    p.alpha -= 8;
                }
            }
            if (!stillActive) {
                expIt.remove();
                explosionPool.add(exp);
            }
        }

        canvas.drawText("Score: " + score + "   |   Lives: " + Math.max(0, lives), cx, 150, hudPaint);

        if (gameState == STATE_PLAYING || !explosions.isEmpty() || waveAnimRadius > 0) {
            postInvalidateOnAnimation();
        }
    }

    private void drawCustomUfo(Canvas canvas, Ufo ufo) {
        float x = ufo.x;
        float y = ufo.y;

        switch (ufo.type) {
            case 0:
                ufoBasePaint.setColor(Color.parseColor("#263238"));
                ufoHighlightPaint.setColor(Color.parseColor("#78909C"));
                ufoDomePaint.setColor(Color.parseColor("#69F0AE"));
                ufoDomePaint.setAlpha(180);

                canvas.drawOval(x - 35, y - 40, x + 35, y + 5, ufoDomePaint);
                canvas.drawOval(x - 70, y - 15, x + 70, y + 15, ufoBasePaint);
                canvas.drawOval(x - 50, y - 10, x + 50, y + 5, ufoHighlightPaint);

                particlePaint.setColor(Color.parseColor("#00E5FF"));
                particlePaint.setAlpha(255);
                canvas.drawCircle(x - 45, y + 5, 5, particlePaint);
                canvas.drawCircle(x, y + 10, 6, particlePaint);
                canvas.drawCircle(x + 45, y + 5, 5, particlePaint);
                break;

            case 1:
                ufoBasePaint.setColor(Color.parseColor("#3E2723"));
                ufoHighlightPaint.setColor(Color.parseColor("#D84315"));
                ufoDomePaint.setColor(Color.parseColor("#FFAB00"));
                ufoDomePaint.setAlpha(180);

                canvas.drawOval(x - 25, y - 45, x + 25, y, ufoDomePaint);
                canvas.drawOval(x - 60, y - 10, x + 60, y + 20, ufoBasePaint);
                canvas.drawOval(x - 40, y - 5, x + 40, y + 10, ufoHighlightPaint);

                particlePaint.setColor(Color.parseColor("#FFEA00"));
                particlePaint.setAlpha(255);
                canvas.drawCircle(x - 35, y + 10, 5, particlePaint);
                canvas.drawCircle(x, y + 15, 6, particlePaint);
                canvas.drawCircle(x + 35, y + 10, 5, particlePaint);
                break;

            case 2:
                ufoBasePaint.setColor(Color.parseColor("#F57F17"));
                ufoHighlightPaint.setColor(Color.parseColor("#FBC02D"));
                ufoDomePaint.setColor(Color.parseColor("#E040FB"));
                ufoDomePaint.setAlpha(180);

                canvas.drawOval(x - 35, y - 40, x + 35, y + 5, ufoDomePaint);
                canvas.drawOval(x - 70, y - 15, x + 70, y + 15, ufoBasePaint);
                canvas.drawOval(x - 50, y - 10, x + 50, y + 5, ufoHighlightPaint);

                particlePaint.setColor(Color.parseColor("#FF4081"));
                particlePaint.setAlpha(255);
                canvas.drawCircle(x - 45, y + 5, 5, particlePaint);
                canvas.drawCircle(x, y + 10, 6, particlePaint);
                canvas.drawCircle(x + 45, y + 5, 5, particlePaint);
                break;

            case 3:
                ufoBasePaint.setColor(Color.parseColor("#000000"));
                ufoHighlightPaint.setColor(Color.parseColor("#424242"));
                ufoDomePaint.setColor(Color.parseColor("#D50000"));
                ufoDomePaint.setAlpha(180);

                canvas.drawOval(x - 40, y - 25, x + 40, y + 5, ufoDomePaint);
                canvas.drawOval(x - 85, y - 5, x + 85, y + 15, ufoBasePaint);
                canvas.drawOval(x - 60, y - 2, x + 60, y + 8, ufoHighlightPaint);

                particlePaint.setColor(Color.parseColor("#FF1744"));
                particlePaint.setAlpha(255);
                canvas.drawCircle(x - 55, y + 5, 4, particlePaint);
                canvas.drawCircle(x - 25, y + 8, 4, particlePaint);
                canvas.drawCircle(x, y + 10, 5, particlePaint);
                canvas.drawCircle(x + 25, y + 8, 4, particlePaint);
                canvas.drawCircle(x + 55, y + 5, 4, particlePaint);
                break;
        }
    }

    private void spawnUfo(int cx, int cy) {
        if (ufoPool.isEmpty()) return;

        Ufo ufo = ufoPool.remove(ufoPool.size() - 1);
        int edge = random.nextInt(4);
        int w = getWidth(); int h = getHeight();

        switch (edge) {
            case 0: ufo.x = random.nextInt(w); ufo.y = -100; break;
            case 1: ufo.x = w + 100; ufo.y = random.nextInt(h); break;
            case 2: ufo.x = random.nextInt(w); ufo.y = h + 100; break;
            case 3: ufo.x = -100; ufo.y = random.nextInt(h); break;
        }

        float dx = cx - ufo.x;
        float dy = cy - ufo.y;
        float length = (float) Math.hypot(dx, dy);
        ufo.dx = dx / length;
        ufo.dy = dy / length;

        ufo.speed = baseSpeed + (random.nextFloat() * 1.5f);

        ufo.type = random.nextInt(4);

        ufoList.add(ufo);
    }
}