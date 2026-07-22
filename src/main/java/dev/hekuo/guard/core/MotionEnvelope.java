package dev.hekuo.guard.core;

import net.minecraft.util.math.Vec3d;

/** Pure movement math, kept independent of a running server for testing. */
public final class MotionEnvelope {
    private MotionEnvelope() {}

    public static boolean exceeds(Vec3d from, Vec3d to, Vec3d velocity, int elapsedTicks, double horizontalPerTick, double verticalPerTick) {
        int ticks = Math.clamp(elapsedTicks, 1, 5);
        Vec3d delta = to.subtract(from);
        double horizontal = Math.hypot(delta.x, delta.z);
        double horizontalBudget = horizontalPerTick * ticks + Math.hypot(velocity.x, velocity.z) * ticks + 0.5;
        double verticalBudget = verticalPerTick * ticks + Math.abs(velocity.y) * ticks + 0.5;
        return horizontal > horizontalBudget || Math.abs(delta.y) > verticalBudget;
    }
}
