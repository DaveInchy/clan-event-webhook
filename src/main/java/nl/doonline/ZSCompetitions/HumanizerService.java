package nl.doonline.ZSCompetitions;

import com.google.inject.Singleton;
import java.util.Random;

@Singleton
public class HumanizerService {

    private final Random random = new Random();
    private double timingSkill = 1.0; // Starts at 1.0, decreases as skill increases
    private long lastActionTime = System.currentTimeMillis();

    // Constants adapted from Python script
    private static final double MAX_MISCLICK_CHANCE = 0.08;
    private static final double ACCURACY_DEGRADATION_FACTOR = 2.0;
    private static final double INITIAL_STD_DEV_FACTOR = 0.25;

    public void recordAction() {
        lastActionTime = System.currentTimeMillis();
        // Improve timing skill with activity
        timingSkill = Math.max(0.5, timingSkill * 0.975);
    }

    public void updateFocus() {
        if (System.currentTimeMillis() - lastActionTime > 120000) { // 2 minutes
            // Degrade skill back towards 1.0 (default)
            timingSkill = Math.min(1.0, timingSkill + 0.0005);
        }
    }

    public Point getHumanizedClick(int centerX, int centerY, int cellWidth, int cellHeight) {
        updateFocus();

        double focusMetric = (1.0 - timingSkill) / 0.5; // Maps [1.0, 0.5] to [0, 1]
        double currentMissclickChance = (1.0 - focusMetric) * MAX_MISCLICK_CHANCE;

        double randX, randY;

        if (random.nextDouble() < currentMissclickChance) {
            // Mis-click
            double stdDevFactor = INITIAL_STD_DEV_FACTOR * (1 + (1.0 - focusMetric) * ACCURACY_DEGRADATION_FACTOR);
            double stdDevX = cellWidth * stdDevFactor;
            double stdDevY = cellHeight * stdDevFactor;
            randX = random.nextGaussian() * stdDevX + centerX;
            randY = random.nextGaussian() * stdDevY + centerY;
        } else {
            // Accurate click
            double stdDevFactor = INITIAL_STD_DEV_FACTOR * (1.0 - (focusMetric * 0.5));
            double stdDevX = (cellWidth * stdDevFactor);
            double stdDevY = (cellHeight * stdDevFactor);
            randX = random.nextGaussian() * stdDevX + centerX;
            randY = random.nextGaussian() * stdDevY + centerY;
        }

        // Clamp to reasonable bounds around the center
        double minX = centerX - cellWidth;
        double maxX = centerX + cellWidth;
        double minY = centerY - cellHeight;
        double maxY = centerY + cellHeight;

        int clampedX = (int) Math.max(minX, Math.min(randX, maxX));
        int clampedY = (int) Math.max(minY, Math.min(randY, maxY));

        return new Point(clampedX, clampedY);
    }

    // A simple data class to hold a point
    public static class Point {
        public final int x;
        public final int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
