package dev.lvstrng.argon.managers;

import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.event.events.PacketSendListener;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.text.Text;

/**
 * Handles .config save/load commands via chat with a dot prefix.
 */
public final class ConfigCommandHandler implements PacketSendListener {

	public ConfigCommandHandler() {}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (!(event.packet instanceof ChatMessageC2SPacket packet))
			return;

		String message = packet.chatMessage().trim();
		if (!message.startsWith("."))
			return;

		event.cancel();

		String[] args = message.substring(1).trim().split("\\s+");
		if (args.length < 1) return;

		if ("config".equalsIgnoreCase(args[0])) {
			if (args.length < 2) {
				sendFeedback("§7Usage: .config save <name> | .config load <name> | .config list");
				return;
			}
			String sub = args[1].toLowerCase();
			if ("save".equals(sub)) {
				if (args.length < 3) {
					sendFeedback("§cUsage: .config save <name>");
					return;
				}
				String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
				if (name.isBlank()) {
					sendFeedback("§cConfig name cannot be empty");
					return;
				}
				if (Argon.INSTANCE.getConfigManager().saveConfig(name)) {
					sendFeedback("§aConfig saved: §f" + name);
				} else {
					sendFeedback("§cFailed to save config");
				}
			} else if ("load".equals(sub)) {
				if (args.length < 3) {
					sendFeedback("§cUsage: .config load <name>");
					return;
				}
				String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
				if (name.isBlank()) {
					sendFeedback("§cConfig name cannot be empty");
					return;
				}
				if (Argon.INSTANCE.getConfigManager().loadConfig(name)) {
					sendFeedback("§aConfig loaded: §f" + name);
				} else {
					sendFeedback("§cConfig not found: §f" + name);
				}
			} else if ("list".equals(sub)) {
				var configs = Argon.INSTANCE.getConfigManager().listConfigs();
				if (configs.isEmpty()) {
					sendFeedback("§7No configs saved");
				} else {
					sendFeedback("§aSaved configs: §f" + String.join(", ", configs));
				}
			} else if ("delete".equals(sub) && args.length >= 3) {
				String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
				if (Argon.INSTANCE.getConfigManager().deleteConfig(name)) {
					sendFeedback("§aConfig deleted: §f" + name);
				} else {
					sendFeedback("§cConfig not found: §f" + name);
				}
			} else {
				sendFeedback("§7Usage: .config save <name> | .config load <name> | .config list | .config delete <name>");
			}
		}
	}

	private void sendFeedback(String message) {
		Argon.mc.execute(() -> {
			if (Argon.mc.player != null && Argon.mc.inGameHud != null) {
				Argon.mc.inGameHud.getChatHud().addMessage(Text.literal(message));
			}
		});
	}
}
