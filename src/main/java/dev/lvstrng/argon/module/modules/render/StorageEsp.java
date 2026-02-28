package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.event.events.GameRenderListener;
import dev.lvstrng.argon.event.events.PacketReceiveListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.RenderUtils;
import dev.lvstrng.argon.utils.WorldUtils;
import net.minecraft.block.entity.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.*;

public final class StorageEsp extends Module implements GameRenderListener, PacketReceiveListener {
	private final NumberSetting range = new NumberSetting(EncryptedString.of("Range"), 10, 200, 80, 5);
	private final BooleanSetting fill = new BooleanSetting(EncryptedString.of("Fill"), true);
	private final NumberSetting alpha = new NumberSetting(EncryptedString.of("Alpha"), 1, 255, 125, 1);
	private final BooleanSetting chests = new BooleanSetting(EncryptedString.of("Chests"), true);
	private final BooleanSetting echests = new BooleanSetting(EncryptedString.of("Ender Chests"), true);
	private final BooleanSetting shulkers = new BooleanSetting(EncryptedString.of("Shulkers"), true);
	private final BooleanSetting hoppers = new BooleanSetting(EncryptedString.of("Hoppers"), false);
	private final BooleanSetting furnaces = new BooleanSetting(EncryptedString.of("Furnaces"), false);
	private final BooleanSetting donutBypass = new BooleanSetting(EncryptedString.of("Donut Bypass"), false);
	private final BooleanSetting tracers = new BooleanSetting(EncryptedString.of("Tracers"), false)
			.setDescription(EncryptedString.of("Draws a line from your player to the storage block"));

	public StorageEsp() {
		super(EncryptedString.of("Storage ESP"),
				EncryptedString.of("Highlights containers in the world"),
				-1,
				Category.RENDER);
		addSettings(range, fill, alpha, chests, echests, shulkers, hoppers, furnaces, donutBypass, tracers);
	}

	@Override
	public void onEnable() {
		eventManager.add(PacketReceiveListener.class, this);
		eventManager.add(GameRenderListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PacketReceiveListener.class, this);
		eventManager.remove(GameRenderListener.class, this);
		super.onDisable();
	}

	@Override
	public void onGameRender(GameRenderEvent event) {
		Camera cam = mc.gameRenderer.getCamera();
		if (cam == null || mc.player == null || mc.world == null) return;
		MatrixStack matrices = event.matrices;
		Vec3d camPos = cam.getPos();
		matrices.push();
		matrices.translate(-camPos.x, -camPos.y, -camPos.z);

		double rangeSq = range.getValue() * range.getValue();
		for (WorldChunk chunk : WorldUtils.getLoadedChunks().toList()) {
			for (BlockPos blockPos : chunk.getBlockEntityPositions()) {
				if (mc.player.squaredDistanceTo(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) > rangeSq)
					continue;
				BlockEntity blockEntity = mc.world.getBlockEntity(blockPos);
				Color color = getColor(blockEntity);
				if (color == null) continue;

				double dist = mc.player.squaredDistanceTo(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
				double alphaFactor = 1.0 - MathHelper.clamp((100.0 - Math.sqrt(dist)) / 100.0, 0.0, 1.0);
				int a = (int) (alpha.getValueInt() * (1.0 - alphaFactor * 0.5));
				Color drawColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(255, a));

				if (fill.getValue()) {
					RenderUtils.renderFilledBox(matrices,
							blockPos.getX() + 0.06f, blockPos.getY(), blockPos.getZ() + 0.06f,
							blockPos.getX() + 0.94f, blockPos.getY() + 0.875f, blockPos.getZ() + 0.94f,
							drawColor);
				}
				if (tracers.getValue()) {
					Vec3d start = mc.player.getEyePos();
					Vec3d end = new Vec3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
					RenderUtils.renderLine(matrices, new Color(color.getRed(), color.getGreen(), color.getBlue(), 255), start, end);
				}
			}
		}

		matrices.pop();
	}

	private Color getColor(BlockEntity blockEntity) {
		if (blockEntity == null) return null;
		if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof TrappedChestBlockEntity) {
			return chests.getValue() ? new Color(200, 200, 101) : null;
		}
		if (blockEntity instanceof EnderChestBlockEntity) {
			return echests.getValue() ? new Color(155, 0, 200) : null;
		}
		if (blockEntity instanceof ShulkerBoxBlockEntity) {
			return shulkers.getValue() ? new Color(200, 0, 106) : null;
		}
		if (blockEntity instanceof HopperBlockEntity) {
			return hoppers.getValue() ? new Color(100, 100, 100) : null;
		}
		if (blockEntity instanceof FurnaceBlockEntity) {
			return furnaces.getValue() ? new Color(100, 100, 100) : null;
		}
		if (blockEntity instanceof BarrelBlockEntity) {
			return new Color(255, 140, 140);
		}
		if (blockEntity instanceof MobSpawnerBlockEntity) {
			return new Color(138, 126, 166);
		}
		if (blockEntity instanceof EnchantingTableBlockEntity) {
			return new Color(80, 80, 255);
		}
		return null;
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (donutBypass.getValue() && event.packet instanceof ChunkDeltaUpdateS2CPacket) {
			event.cancel();
		}
	}
}
