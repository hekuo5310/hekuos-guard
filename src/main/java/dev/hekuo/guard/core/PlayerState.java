package dev.hekuo.guard.core;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.UUID;

final class PlayerState {
    final UUID uuid;
    final EnumMap<CheckType, Integer> scores = new EnumMap<>(CheckType.class);
    final ArrayDeque<Vec3d> positions = new ArrayDeque<>();
    final ArrayDeque<ScoreEvent> scoreEvents = new ArrayDeque<>();
    Vec3d safePosition;
    Vec3d lastPosition;
    long lastMoveTick;
    long exemptUntilTick;
    long lastDecayTick;
    long lastAttackTick;
    UUID lastAttackTarget;
    int targetsThisSecond;
    long targetWindowTick;
    int stableTicks;
    int airborneTicks;
    int waterWalkTicks;
    int invulnerableTicks;
    long movementBudgetTick = Long.MIN_VALUE;
    double horizontalDistanceThisTick;
    long lastMovePacketNanos;
    int timerOverBudgetTicks;
    int moveViolationStreak;
    int moveFloodStreak;
    long externalMovementGraceUntilTick;
    TokenBucket moveBucket;
    TokenBucket attackBucket;
    TokenBucket interactBucket;

    PlayerState(UUID uuid, Vec3d initial, long tick, long nowNanos, int moveRate, int attackRate, int interactRate, int burst) {
        this.uuid = uuid;
        this.safePosition = initial;
        this.lastPosition = initial;
        this.lastMoveTick = tick;
        this.lastMovePacketNanos = nowNanos;
        this.lastDecayTick = tick;
        this.moveBucket = new TokenBucket(moveRate, (double) moveRate * burst, nowNanos);
        this.attackBucket = new TokenBucket(attackRate, (double) attackRate * burst, nowNanos);
        this.interactBucket = new TokenBucket(interactRate, (double) interactRate * burst, nowNanos);
    }

    void remember(Vec3d position) {
        positions.addLast(position);
        while (positions.size() > 20) positions.removeFirst();
        lastPosition = position;
    }

    int totalScore() { return scores.values().stream().mapToInt(Integer::intValue).sum(); }
    record ScoreEvent(long tick, int points) {}
}
