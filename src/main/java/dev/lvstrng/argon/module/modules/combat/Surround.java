package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.PlayerTickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.InventoryUtils;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Places obsidian around your feet. Optionally attacks crystals blocking placement.
 */
public final class Surround extends Module implements PlayerTickListener {

	private final NumberSetting placeRange = new NumberSetting(EncryptedString.of("Place Range"), 0, 6, 4, 0.1);
	private final BooleanSetting attack = new BooleanSetting(EncryptedString.of("Attack Crystals"), true);
	private final BooleanSetting rotate = new BooleanSetting(EncryptedString.of("Rotate"), false);

	public Surround() {
		super(EncryptedString.of("Surround"),
				EncryptedString.of("Surrounds your feet with obsidian"),
				-1,
				Category.COMBAT);
		addSettings(placeRange, attack, rotate);
	}

	@Override
	public void onEnable() {
		eventManager.add(PlayerTickListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(PlayerTickListener.class, this);
		super.onDisable();
	}

	@Override
	public void onPlayerTick() {
		if (mc.player == null || mc.world == null || mc.player.isSpectator()) return;

		int slot = getObsidianSlot();
		if (slot == -1) return;

		List<BlockPos> surroundPositions = getSurroundPositions();
		if (surroundPositions.isEmpty()) return;

		if (attack.getValue()) {
			for (BlockPos pos : surroundPositions) {
				EndCrystalEntity crystal = getCrystalAt(pos);
				if (crystal != null) {
					mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
					mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
					return;
				}
			}
		}

		double rangeSq = placeRange.getValue() * placeRange.getValue();
		for (BlockPos pos : surroundPositions) {
			if (!mc.world.getBlockState(pos).isReplaceable()) continue;
			if (mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSq) continue;
			if (!mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) continue;

			Direction side = getPlaceDirection(pos);
			if (side == null) continue;

			BlockPos support = pos.offset(side);
			Direction face = side.getOpposite();
			Vec3d hitVec = Vec3d.ofCenter(support).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
			BlockHitResult hit = new BlockHitResult(hitVec, face, support, false);

			int prevSlot = mc.player.getInventory().selectedSlot;
			InventoryUtils.setInvSlot(slot);
			mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 0));
			mc.player.swingHand(Hand.MAIN_HAND);
			InventoryUtils.setInvSlot(prevSlot);
			return;
		}
	}

	private List<BlockPos> getSurroundPositions() {
		List<BlockPos> list = new ArrayList<>();
		BlockPos playerPos = mc.player.getBlockPos();
		for (Direction dir : Direction.Type.HORIZONTAL) {
			list.add(playerPos.offset(dir));
		}
		return list;
	}

	private Direction getPlaceDirection(BlockPos target) {
		BlockPos playerBlock = mc.player.getBlockPos();
		int dx = target.getX() - playerBlock.getX();
		int dz = target.getZ() - playerBlock.getZ();
		if (dx == 1) return Direction.WEST;
		if (dx == -1) return Direction.EAST;
		if (dz == 1) return Direction.NORTH;
		if (dz == -1) return Direction.SOUTH;
		return null;
	}

	private EndCrystalEntity getCrystalAt(BlockPos pos) {
		Box box = new Box(pos);
		for (var e : mc.world.getOtherEntities(null, box)) {
			if (e instanceof EndCrystalEntity ce) return ce;
		}
		return null;
	}

	private int getObsidianSlot() {
		for (int i = 0; i < 9; i++) {
			if (mc.player.getInventory().getStack(i).getItem() == Items.OBSIDIAN)
				return i;
		}
		return -1;
	}
}
