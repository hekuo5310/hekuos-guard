package dev.hekuo.guard.core;

import dev.hekuo.guard.HekuosGuard;
import dev.hekuo.guard.config.GuardConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.registry.tag.FluidTags;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Coordinates all checks; it deliberately has no block-breaking or placement checks. */
public final class ViolationManager {
    private final GuardConfig config;
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final Set<UUID> alertSubscribers = new HashSet<>();
    private final AuditLogger audit = new AuditLogger();
    private final BanManager bans = new BanManager();
    private MinecraftServer server;
    private long tick;

    public ViolationManager(GuardConfig config) { this.config = config; }
    public void start(MinecraftServer value) { server = value; bans.load(); }
    public void stop() { states.clear(); alertSubscribers.clear(); server = null; }

    public void join(ServerPlayerEntity player) {
        BanManager.BanRecord record = bans.active(player.getUuid());
        if (record != null) {
            player.networkHandler.disconnect(bans.message(record));
            return;
        }
        PlayerState state = state(player);
        state.exemptUntilTick = Math.max(state.exemptUntilTick, tick + config.get().movement.joinGraceSeconds * 20L);
    }
    public void leave(ServerPlayerEntity player) { states.remove(player.getUuid()); alertSubscribers.remove(player.getUuid()); }
    public long tickCount() { return tick; }

    public void tick(MinecraftServer ignored) {
        tick++;
        for (ServerPlayerEntity player : ignored.getPlayerManager().getPlayerList()) {
            PlayerState state = state(player);
            Vec3d position = player.getPos();
            checkWaterWalk(player, state);
            if (isMovementExempt(player, state)) {
                state.stableTicks = 0;
                state.airborneTicks = 0;
            } else if (player.getWorld().isSpaceEmpty(player) && player.isOnGround()) {
                if (++state.stableTicks >= config.get().movement.safeTicks) state.safePosition = position;
                state.airborneTicks = 0;
            } else {
                state.stableTicks = 0;
                checkFlight(player, state);
            }
            checkInvulnerability(player, state);
            state.remember(position);
            decay(state);
        }
    }

    /** Called by the pre-processing move-packet mixin. False means cancel this packet. */
    public boolean checkMovePacket(ServerPlayerEntity player, double x, double y, double z) {
        GuardConfig.Values cfg = config.get();
        if (!cfg.enabled || !cfg.movement.enabled) return true;
        PlayerState state = state(player);
        if (!finite(x, y, z) || Math.abs(x) > cfg.movement.maxCoordinate || Math.abs(y) > cfg.movement.maxCoordinate || Math.abs(z) > cfg.movement.maxCoordinate) {
            violation(player, state, CheckType.INVALID_PACKET, "non-finite or out-of-world position", 20, true);
            return false;
        }
        if (isMovementExempt(player, state)) return true;
        long now = System.nanoTime();
        if (cfg.packets.enabled && !state.moveBucket.tryTake(now)) {
            violation(player, state, CheckType.INVALID_PACKET, "movement packet flood", 5, false);
            return false;
        }
        Vec3d target = new Vec3d(x, y, z);
        int elapsed = (int) Math.max(1, tick - state.lastMoveTick);
        boolean tooFast = MotionEnvelope.exceeds(player.getPos(), target, player.getVelocity(), elapsed,
                cfg.movement.maxHorizontalPerTick, cfg.movement.maxVerticalPerTick);
        // Vanilla clients can legitimately report a box touching a collision edge while the
        // server resolves the move. Only treat a large attempted displacement into a block as phase.
        double displacement = target.distanceTo(player.getPos());
        boolean clips = displacement > 1.5 && !player.getWorld().isSpaceEmpty(player, player.getBoundingBox().offset(target.subtract(player.getPos())));
        if (tooFast || clips) {
            violation(player, state, clips ? CheckType.PHASE : CheckType.MOVE_SPEED,
                    clips ? "target collides with solid shape" : "movement exceeds conservative envelope", 3, true);
            return false;
        }
        state.lastMoveTick = tick;
        return true;
    }

    /** Fabric's server-side attack event lets valid automation remain untouched while impossible hits are cancelled. */
    public ActionResult checkAttack(ServerPlayerEntity player, Entity target) {
        GuardConfig.Values cfg = config.get();
        if (!cfg.enabled || !cfg.combat.enabled) return ActionResult.PASS;
        PlayerState state = state(player);
        if (cfg.packets.enabled && !state.attackBucket.tryTake(System.nanoTime())) {
            violation(player, state, CheckType.ATTACK_FLOOD, "attack packet flood", 5, false);
            return ActionResult.FAIL;
        }
        Vec3d eye = player.getEyePos();
        Vec3d closest = closest(target.getBoundingBox(), eye);
        double allowed = player.getEntityInteractionRange() + cfg.combat.reachTolerance;
        if (eye.squaredDistanceTo(closest) > allowed * allowed) {
            violation(player, state, CheckType.REACH, "attack beyond interaction range", 4, false);
            return ActionResult.FAIL;
        }
        HitResult hit = player.getWorld().raycast(new RaycastContext(eye, closest, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK && eye.squaredDistanceTo(((BlockHitResult) hit).getPos()) + 0.01 < eye.squaredDistanceTo(closest)) {
            violation(player, state, CheckType.THROUGH_WALL, "solid block blocks attack ray", 4, false);
            return ActionResult.FAIL;
        }
        if (tick - state.targetWindowTick > 20) { state.targetWindowTick = tick; state.targetsThisSecond = 0; }
        if (!target.getUuid().equals(state.lastAttackTarget)) state.targetsThisSecond++;
        state.lastAttackTarget = target.getUuid();
        if (state.targetsThisSecond > cfg.combat.maxTargetsPerSecond) violation(player, state, CheckType.MULTI_TARGET, "unusually many attack targets", 1, false);
        return ActionResult.PASS;
    }

    public void exempt(ServerPlayerEntity player, int seconds) {
        PlayerState state = state(player);
        state.exemptUntilTick = tick + Math.min(seconds, config.get().enforcement.temporaryExemptionMaxSeconds) * 20L;
    }
    public void reset(ServerPlayerEntity player) { state(player).scores.clear(); }
    public int score(ServerPlayerEntity player) { return state(player).totalScore(); }
    public boolean toggleAlerts(ServerPlayerEntity player) { return alertSubscribers.remove(player.getUuid()) ? false : alertSubscribers.add(player.getUuid()); }
    public boolean unbanByPlayerName(String playerName) { return bans.unbanByPlayerName(playerName); }
    public boolean forceUnbanByPlayerName(String playerName) { return bans.forceUnbanByPlayerName(playerName); }

    private PlayerState state(ServerPlayerEntity player) {
        return states.computeIfAbsent(player.getUuid(), ignored -> new PlayerState(player.getUuid(), player.getPos(), tick, System.nanoTime(),
                config.get().packets.movePacketsPerSecond, config.get().packets.attackPacketsPerSecond, config.get().packets.burstMultiplier));
    }
    private boolean isMovementExempt(ServerPlayerEntity player, PlayerState state) {
        // Fuji's /fly and other server-side flight systems grant vanilla player abilities.
        // Trust the granted state, never the presence of a particular mod id.
        if (tick < state.exemptUntilTick || player.isSpectator() || player.isCreative() || player.getAbilities().allowFlying
                || player.getAbilities().flying || player.hasVehicle() || player.isFallFlying()) return true;
        if (player.isTouchingWater() || player.isClimbing() || player.hasStatusEffect(StatusEffects.LEVITATION) || player.hasStatusEffect(StatusEffects.SLOW_FALLING)) return true;
        if (player.getVelocity().lengthSquared() > 1.0) return true; // explosions, pistons, slime launchers and strong knockback.
        return false;
    }
    private void checkFlight(ServerPlayerEntity player, PlayerState state) {
        // This path is only reached for survival players without Fuji/vanilla flight, vehicle,
        // elytra, water, climbing, relevant effects, or high external velocity (see exemptions).
        if (++state.airborneTicks < config.get().movement.flightAirTicks) return;
        if ((state.airborneTicks - config.get().movement.flightAirTicks) % 20 == 0) {
            violation(player, state, CheckType.FLIGHT, "unsupported sustained airtime", 3, true);
        }
    }
    private void checkWaterWalk(ServerPlayerEntity player, PlayerState state) {
        if (isMovementExempt(player, state) || player.isTouchingWater()) {
            state.waterWalkTicks = 0;
            return;
        }
        BlockPos belowFeet = BlockPos.ofFloored(player.getX(), player.getY() - 0.05, player.getZ());
        boolean standingOnWater = player.getWorld().getFluidState(belowFeet).isIn(FluidTags.WATER)
                && player.getWorld().getBlockState(belowFeet).getCollisionShape(player.getWorld(), belowFeet).isEmpty()
                && Math.abs(player.getVelocity().y) < 0.08;
        if (!standingOnWater) {
            state.waterWalkTicks = 0;
            return;
        }
        if (++state.waterWalkTicks >= config.get().movement.waterWalkTicks
                && (state.waterWalkTicks - config.get().movement.waterWalkTicks) % 20 == 0) {
            violation(player, state, CheckType.WATER_WALK, "unsupported standing on water surface", 3, true);
        }
    }
    private void checkInvulnerability(ServerPlayerEntity player, PlayerState state) {
        // Creative and spectator are intentionally excluded. A client cannot set this bit; it
        // identifies a server-visible survival-mode god/invulnerability state.
        if (!player.isCreative() && !player.isSpectator() && player.getAbilities().invulnerable) {
            if (++state.invulnerableTicks >= config.get().combat.invulnerabilityTicks
                    && (state.invulnerableTicks - config.get().combat.invulnerabilityTicks) % 20 == 0) {
                violation(player, state, CheckType.INVULNERABLE, "survival player has server-visible invulnerability", 5, false);
            }
        } else {
            state.invulnerableTicks = 0;
        }
    }
    private void violation(ServerPlayerEntity player, PlayerState state, CheckType type, String detail, int points, boolean setback) {
        int score = state.scores.merge(type, points, Integer::sum);
        state.scoreEvents.addLast(new PlayerState.ScoreEvent(tick, points));
        int total = state.totalScore();
        audit.write(player, type, detail, total, tick);
        HekuosGuard.LOGGER.warn("[{}] {}: {} ({} total)", type, player.getGameProfile().getName(), detail, total);
        if (config.get().logging.staffAlerts && total >= config.get().enforcement.alertAt) {
            for (UUID uuid : alertSubscribers) {
                ServerPlayerEntity staff = server == null ? null : server.getPlayerManager().getPlayer(uuid);
                if (staff != null) staff.sendMessage(Text.literal("[HG] " + player.getName().getString() + " " + type + ": " + detail + " (" + total + ")"), false);
            }
        }
        if (setback) setback(player, state);
        if (total >= config.get().enforcement.kickAt) {
            GuardConfig.Enforcement settings = config.get().enforcement;
            int windowTicks = settings.fastScoreWindowSeconds * 20;
            while (!state.scoreEvents.isEmpty() && state.scoreEvents.peekFirst().tick() < tick - windowTicks) state.scoreEvents.removeFirst();
            int recentPoints = state.scoreEvents.stream().mapToInt(PlayerState.ScoreEvent::points).sum();
            int speedLevel = Math.max(1, (int) Math.ceil((double) recentPoints / settings.fastScoreThreshold));
            BanManager.BanRecord record = bans.ban(player, speedLevel, settings);
            HekuosGuard.LOGGER.warn("Banned {} at level {} after {} points in {} seconds", player.getGameProfile().getName(), record.level, recentPoints, settings.fastScoreWindowSeconds);
            player.networkHandler.disconnect(bans.message(record));
        }
    }
    private void setback(ServerPlayerEntity player, PlayerState state) {
        Vec3d safe = state.safePosition;
        player.setVelocity(Vec3d.ZERO);
        player.networkHandler.requestTeleport(safe.x, safe.y, safe.z, player.getYaw(), player.getPitch());
    }
    private void decay(PlayerState state) {
        long period = config.get().enforcement.decaySeconds * 20L;
        if (tick - state.lastDecayTick < period) return;
        state.lastDecayTick = tick;
        state.scores.replaceAll((check, score) -> Math.max(0, score - 1));
        state.scores.values().removeIf(score -> score == 0);
    }
    private static boolean finite(double x, double y, double z) { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    private static Vec3d closest(Box box, Vec3d point) {
        return new Vec3d(Math.clamp(point.x, box.minX, box.maxX), Math.clamp(point.y, box.minY, box.maxY), Math.clamp(point.z, box.minZ, box.maxZ));
    }
}
