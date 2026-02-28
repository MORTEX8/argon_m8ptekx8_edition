package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.event.events.PlayerTickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Automatically mines the block you are looking at.
 */
public final class AutoMine extends Module implements PlayerTickListener {

	public AutoMine() {
		super(EncryptedString.of("Auto Mine"),
				EncryptedString.of("Mines the block you look at automatically"),
				-1,
				Category.MISC);
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
		if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
		if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;

		BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
		if (mc.world.getBlockState(hit.getBlockPos()).isAir()) return;

		mc.interactionManager.attackBlock(hit.getBlockPos(), hit.getSide());
	}
}
