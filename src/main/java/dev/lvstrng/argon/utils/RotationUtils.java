package dev.lvstrng.argon.utils;

import dev.lvstrng.argon.utils.rotation.Rotation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import static dev.lvstrng.argon.Argon.mc;

public final class RotationUtils {

	public static Vec3d getEyesPos(PlayerEntity player) {
		return RenderUtils.getCameraPos();
	}

	public static BlockPos getCameraBlockPos() {
		return mc.getBlockEntityRenderDispatcher().camera.getBlockPos();
	}

	public static BlockPos getEyesBlockPos() {
		return new BlockPos((int) getEyesPos(mc.player).x, (int) getEyesPos(mc.player).y, (int) getEyesPos(mc.player).z);
	}

	public static Vec3d getPlayerLookVec(float yaw, float pitch) {
		float f = pitch * 0.017453292F;
		float g = -yaw * 0.017453292F;

		float h = MathHelper.cos(g);
		float i = MathHelper.sin(g);
		float j = MathHelper.cos(f);
		float k = MathHelper.sin(f);

		return new Vec3d((i * j), (-k), (h * j));
	}

	public static Vec3d getPlayerLookVec(PlayerEntity player) {
		return getPlayerLookVec(player.getYaw(), player.getPitch());
	}

	public static Rotation getDiff(Rotation rotation1, Rotation rotation2) {
		double yaw = Math.abs(Math.max(rotation1.yaw(), rotation2.yaw()) - Math.min(rotation1.yaw(), rotation2.yaw()));
		double pitch = Math.abs(Math.max(rotation1.pitch(), rotation2.pitch()) - Math.min(rotation1.pitch(), rotation2.pitch()));

		return new Rotation(yaw, pitch);
	}

	public static Rotation getSmoothRotation(Rotation from, Rotation to, double speed) {
		return new Rotation(
				MathHelper.lerpAngleDegrees((float) speed, (float) from.yaw(), (float) to.yaw()),
				MathHelper.lerpAngleDegrees((float) speed, (float) from.pitch(), (float) to.pitch())
		);
	}

	public static double getTotalDiff(Rotation rotation1, Rotation rotation2) {
		Rotation diff = getDiff(rotation1, rotation2);

		return diff.yaw() + diff.pitch();
	}

	public static Vec3d getClientLookVec() {
		return getPlayerLookVec(mc.player);
	}

	public static Rotation getDirection(Entity entity, Vec3d vec) {
		double dx = vec.x - entity.getX(),
				dy = vec.y - entity.getY(),
				dz = vec.z - entity.getZ(),
				dist = MathHelper.sqrt((float) (dx * dx + dz * dz));

		return new Rotation(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0), -MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dy, dist))));
	}

	/** Yaw and pitch to look from src to dest. Returns float[0]=yaw, float[1]=pitch. */
	public static float[] getRotationsTo(Vec3d src, Vec3d dest) {
		Vec3d diff = dest.subtract(src);
		float yaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90);
		float pitch = (float) Math.toDegrees(-Math.atan2(diff.y, Math.hypot(diff.x, diff.z)));
		return new float[] { MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch) };
	}

	/** Smooth step from previous toward target. rotationSpeed 0=slow, 100=fast. */
	public static float[] smooth(float[] target, float[] previous, float rotationSpeed) {
		float speed = (1.0f - MathHelper.clamp(rotationSpeed / 100.0f, 0.1f, 0.9f)) * 10.0f;
		float[] out = new float[2];
		out[0] = previous[0] + (float) (-getAngleDifference(previous[0], target[0]) / speed);
		out[1] = previous[1] + (-(previous[1] - target[1]) / speed);
		out[1] = MathHelper.clamp(out[1], -90.0f, 90.0f);
		return out;
	}

	public static double getAngleDifference(float client, float yaw) {
		return ((client - yaw) % 360.0 + 540.0) % 360.0 - 180.0;
	}

	public static double getAnglePitchDifference(float client, float pitch) {
		return ((client - pitch) % 180.0 + 270.0) % 180.0 - 90.0;
	}

	/** Forward vector from yaw/pitch in degrees. */
	public static Vec3d getRotationVector(float pitch, float yaw) {
		float f = pitch * ((float) Math.PI / 180.0f);
		float g = -yaw * ((float) Math.PI / 180.0f);
		float h = MathHelper.cos(g);
		float i = MathHelper.sin(g);
		float j = MathHelper.cos(f);
		float k = MathHelper.sin(f);
		return new Vec3d(i * j, -k, h * j);
	}

	public static double getAngleToRotation(Rotation rotation) {
		double currentYaw = MathHelper.wrapDegrees(mc.player.getYaw());
		double currentPitch = MathHelper.wrapDegrees(mc.player.getPitch());

		double diffYaw = MathHelper.wrapDegrees(currentYaw - rotation.yaw());
		double diffPitch = MathHelper.wrapDegrees(currentPitch - rotation.pitch());

		return Math.sqrt(diffYaw * diffYaw + diffPitch * diffPitch);
	}
}