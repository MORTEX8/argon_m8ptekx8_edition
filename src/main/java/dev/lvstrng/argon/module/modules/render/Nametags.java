package dev.lvstrng.argon.module.modules.render;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.HudListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.modules.client.Friends;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import dev.lvstrng.argon.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

import java.awt.*;
import java.util.Comparator;
import java.util.List;

/**
 * Renders custom nameplates: name, health, and distance for visible players.
 */
public final class Nametags extends Module implements HudListener {

	private final NumberSetting scale = new NumberSetting(EncryptedString.of("Scale"), 0.5, 3, 1, 0.1);
	private final BooleanSetting health = new BooleanSetting(EncryptedString.of("Health"), true);
	private final BooleanSetting distance = new BooleanSetting(EncryptedString.of("Distance"), true);
	private final BooleanSetting background = new BooleanSetting(EncryptedString.of("Background"), true);
	private final NumberSetting maxRange = new NumberSetting(EncryptedString.of("Max Range"), 10, 128, 64, 1);

	public Nametags() {
		super(EncryptedString.of("Nametags"),
				EncryptedString.of("Custom nameplates for players"),
				-1,
				Category.RENDER);
		addSettings(scale, health, distance, background, maxRange);
	}

	@Override
	public void onEnable() {
		eventManager.add(HudListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		super.onDisable();
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (mc.world == null || mc.player == null) return;

		double rangeSq = maxRange.getValue() * maxRange.getValue();
		List<? extends PlayerEntity> players = mc.world.getPlayers().stream()
				.filter(p -> p != mc.player && p.isAlive())
				.filter(p -> p.squaredDistanceTo(mc.player) <= rangeSq)
				.sorted(Comparator.comparingDouble(p -> -p.squaredDistanceTo(mc.player)))
				.toList();

		DrawContext context = event.context;
		int x = 4;
		int y = 4;

		context.getMatrices().push();
		float s = scale.getValueFloat();
		context.getMatrices().scale(s, s, 1f);

		for (PlayerEntity player : players) {
			String name = getDisplayName(player);
			String info = buildInfo(player);
			int w1 = mc.textRenderer.getWidth(name) * 2;
			int w2 = info.isEmpty() ? 0 : mc.textRenderer.getWidth(info) * 2;
			int width = Math.max(w1, w2);
			int lineHeight = mc.textRenderer.fontHeight * 2;
			int height = info.isEmpty() ? lineHeight : lineHeight * 2;

			if (background.getValue()) {
				context.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0x80000000);
			}

			TextRenderer.drawString(name, context, x, y, getColor(player));
			if (!info.isEmpty()) {
				TextRenderer.drawString(info, context, x, y + lineHeight, 0xAAAAAA);
			}

			y += height + 6;
		}

		context.getMatrices().pop();
	}

	private String getDisplayName(PlayerEntity player) {
		if (Argon.INSTANCE.getModuleManager().getModule(Friends.class).isEnabled()
				&& Argon.INSTANCE.getFriendManager().isFriend(player)) {
			return "§a" + player.getGameProfile().getName();
		}
		return player.getGameProfile().getName();
	}

	private String buildInfo(PlayerEntity player) {
		StringBuilder sb = new StringBuilder();
		if (health.getValue()) {
			float hp = player.getHealth() + player.getAbsorptionAmount();
			sb.append(String.format("%.1f HP", hp));
		}
		if (distance.getValue()) {
			if (sb.length() > 0) sb.append(" | ");
			sb.append(String.format("%.1fm", player.distanceTo(mc.player)));
		}
		return sb.toString();
	}

	private int getColor(PlayerEntity player) {
		if (Argon.INSTANCE.getFriendManager().isFriend(player))
			return 0xFF00FF00;
		float hp = player.getHealth() / player.getMaxHealth();
		if (hp > 0.6f) return Color.WHITE.getRGB();
		if (hp > 0.3f) return Color.YELLOW.getRGB();
		return new Color(255, 85, 85).getRGB();
	}
}
