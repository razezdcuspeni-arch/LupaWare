package ru.levin.modules.combat;

import java.util.Random;

/**
 * The SmoothAim rotation copied from the supplied pasted_content.txt.
 * Only the Minecraft adapter remains in AttackAura; the aim algorithm itself
 * is kept unchanged.
 */
public final class TestSpookyRotation {
    private TestSpookyRotation() {
    }

    public static final class Vector2D {
        public double x, y;

        public Vector2D(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public Vector2D add(Vector2D other) {
            return new Vector2D(this.x + other.x, this.y + other.y);
        }

        public Vector2D sub(Vector2D other) {
            return new Vector2D(this.x - other.x, this.y - other.y);
        }

        public Vector2D scale(double factor) {
            return new Vector2D(this.x * factor, this.y * factor);
        }

        public double length() {
            return Math.sqrt(x * x + y * y);
        }

        public Vector2D normalize() {
            double len = length();
            if (len == 0) return new Vector2D(0, 0);
            return new Vector2D(x / len, y / len);
        }

        public double distanceTo(Vector2D other) {
            return Math.sqrt(Math.pow(this.x - other.x, 2) + Math.pow(this.y - other.y, 2));
        }

        @Override
        public String toString() {
            return String.format("Vector2D(%.2f, %.2f)", x, y);
        }
    }

    public static final class NoiseGenerator {
        private static final Random rnd = new Random();

        private NoiseGenerator() {
        }

        public static double jitter(double amplitude) {
            return (rnd.nextDouble() - 0.5) * 2 * amplitude;
        }

        public static boolean shouldMiss(double missChance) {
            return rnd.nextDouble() < missChance;
        }

        public static double missOffset(double maxMissDegrees) {
            return (rnd.nextDouble() - 0.5) * 2 * maxMissDegrees;
        }

        public static int randomDelay(int minMs, int maxMs) {
            return minMs + rnd.nextInt(maxMs - minMs + 1);
        }

        public static double randomDouble(double min, double max) {
            return min + (max - min) * rnd.nextDouble();
        }
    }

    public static final class AimProfile {
        public double smoothness;
        public double jitterAmplitude;
        public double missChance;
        public double maxMissDegrees;
        public double reactionDelayMs;
        public double maxAimAngle;
        public double maxDistance;

        public AimProfile(double smoothness, double jitterAmplitude,
                          double missChance, double maxMissDegrees,
                          double reactionDelayMs, double maxAimAngle,
                          double maxDistance) {
            this.smoothness = smoothness;
            this.jitterAmplitude = jitterAmplitude;
            this.missChance = missChance;
            this.maxMissDegrees = maxMissDegrees;
            this.reactionDelayMs = reactionDelayMs;
            this.maxAimAngle = maxAimAngle;
            this.maxDistance = maxDistance;
        }

        public static AimProfile human() {
            return new AimProfile(0.12, 0.25, 0.12, 2.5, 80, 3.5, 6.0);
        }

        public static AimProfile aggressive() {
            return new AimProfile(0.18, 0.35, 0.08, 1.8, 50, 5.0, 8.0);
        }

        public static AimProfile sniper() {
            return new AimProfile(0.06, 0.15, 0.18, 3.0, 150, 2.0, 15.0);
        }
    }

    public static final class SmoothAim {
        private final AimProfile profile;
        private long lastUpdateTime = 0;
        private Vector2D currentAngles;
        private boolean isFirstFrame = true;
        private boolean isAiming = false;
        private long aimStartTime = 0;

        public SmoothAim(AimProfile profile) {
            this.profile = profile;
            this.currentAngles = new Vector2D(0, 0);
        }

        public Vector2D update(Vector2D current, Vector2D target) {
            if (isFirstFrame) {
                isFirstFrame = false;
                lastUpdateTime = System.currentTimeMillis();
                return current;
            }

            long now = System.currentTimeMillis();
            long deltaMs = now - lastUpdateTime;
            lastUpdateTime = now;

            if (deltaMs < profile.reactionDelayMs) {
                return current;
            }

            if (!isAiming) {
                isAiming = true;
                aimStartTime = now;
            }

            Vector2D delta = target.sub(current);
            delta.x = normalizeAngle(delta.x);
            delta.y = normalizeAngle(delta.y);

            double distance = delta.length();

            if (distance < 0.1) {
                return current.add(new Vector2D(
                    NoiseGenerator.jitter(profile.jitterAmplitude * 0.3),
                    NoiseGenerator.jitter(profile.jitterAmplitude * 0.3)
                ));
            }

            if (Math.abs(delta.x) > profile.maxAimAngle) {
                delta.x = Math.signum(delta.x) * profile.maxAimAngle;
            }
            if (Math.abs(delta.y) > profile.maxAimAngle) {
                delta.y = Math.signum(delta.y) * profile.maxAimAngle;
            }

            if (NoiseGenerator.shouldMiss(profile.missChance)) {
                double missX = NoiseGenerator.missOffset(profile.maxMissDegrees);
                double missY = NoiseGenerator.missOffset(profile.maxMissDegrees);
                target = target.add(new Vector2D(missX, missY));
                delta = target.sub(current);
                delta.x = normalizeAngle(delta.x);
                delta.y = normalizeAngle(delta.y);
                distance = delta.length();
            }

            double speed = Math.min(distance, distance * profile.smoothness + 0.02);

            if (speed < 0.05) {
                speed = 0.05 + NoiseGenerator.jitter(0.02);
            }

            Vector2D step = delta.normalize().scale(speed);

            step = step.add(new Vector2D(
                NoiseGenerator.jitter(profile.jitterAmplitude),
                NoiseGenerator.jitter(profile.jitterAmplitude)
            ));

            Vector2D newAngles = current.add(step);

            if (distance > 1.0 && Math.random() < 0.1) {
                newAngles = newAngles.add(new Vector2D(
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.3
                ));
            }

            newAngles.x = clampAngle(newAngles.x);
            newAngles.y = clampAngle(newAngles.y);

            this.currentAngles = newAngles;
            return newAngles;
        }

        private double normalizeAngle(double angle) {
            while (angle > 180) angle -= 360;
            while (angle < -180) angle += 360;
            return angle;
        }

        private double clampAngle(double angle) {
            return Math.max(-180, Math.min(180, angle));
        }

        public void reset() {
            isFirstFrame = true;
            isAiming = false;
            currentAngles = new Vector2D(0, 0);
        }

        public boolean isAiming() {
            return isAiming;
        }

        public long getAimTime() {
            return System.currentTimeMillis() - aimStartTime;
        }
    }
}

