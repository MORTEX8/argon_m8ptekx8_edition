package dev.lvstrng.argon.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lvstrng.argon.Argon;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Manages save/load of named configs to m8ptekx8 folder.
 * Saves all module settings, enabled states, binds, and friends.
 */
public final class ConfigManager {
	private static final String CONFIG_FOLDER = "m8ptekx8";
	private final Gson gson = new Gson();

	public Path getConfigFolder() {
		return Argon.mc.runDirectory.toPath().resolve(CONFIG_FOLDER);
	}

	public Path getConfigPath(String name) {
		String safeName = sanitizeConfigName(name);
		if (safeName.isEmpty()) return null;
		return getConfigFolder().resolve(safeName + ".json");
	}

	/** Only allow alphanumeric, underscore, hyphen - prevents path traversal */
	private static String sanitizeConfigName(String name) {
		if (name == null || name.isBlank()) return "";
		return name.trim().replaceAll("[^a-zA-Z0-9_-]", "");
	}

	public boolean saveConfig(String name) {
		Path path = getConfigPath(name);
		if (path == null) return false;
		try {
			Files.createDirectories(getConfigFolder());
			JsonObject root = new JsonObject();

			// Save all modules
			JsonObject modulesJson = new JsonObject();
			for (Module module : Argon.INSTANCE.getModuleManager().getModules()) {
				JsonObject moduleConfig = new JsonObject();
				moduleConfig.addProperty("enabled", module.isEnabled());
				moduleConfig.addProperty("key", module.getKey());

				for (Setting<?> setting : module.getSettings()) {
					int idx = module.getSettings().indexOf(setting);
					if (setting instanceof BooleanSetting s) {
						moduleConfig.addProperty(String.valueOf(idx), s.getValue());
					} else if (setting instanceof ModeSetting<?> s) {
						moduleConfig.addProperty(String.valueOf(idx), s.getModeIndex());
					} else if (setting instanceof NumberSetting s) {
						moduleConfig.addProperty(String.valueOf(idx), s.getValue());
					} else if (setting instanceof KeybindSetting s) {
						moduleConfig.addProperty(String.valueOf(idx), s.getKey());
					} else if (setting instanceof StringSetting s) {
						moduleConfig.addProperty(String.valueOf(idx), s.getValue());
					} else if (setting instanceof MinMaxSetting s) {
						JsonObject minMax = new JsonObject();
						minMax.addProperty("1", s.getMinValue());
						minMax.addProperty("2", s.getMaxValue());
						moduleConfig.add(String.valueOf(idx), minMax);
					}
				}
				modulesJson.add(String.valueOf(Argon.INSTANCE.getModuleManager().getModules().indexOf(module)), moduleConfig);
			}
			root.add("modules", modulesJson);

			// Save friends
			JsonArray friendsArray = new JsonArray();
			for (String friend : Argon.INSTANCE.getFriendManager().getFriends()) {
				friendsArray.add(friend);
			}
			root.add("friends", friendsArray);

			Files.writeString(path, gson.toJson(root));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public boolean loadConfig(String name) {
		Path path = getConfigPath(name);
		if (path == null || !Files.isRegularFile(path)) return false;
		try {
			JsonObject root = gson.fromJson(Files.readString(path), JsonObject.class);
			if (root == null) return false;

			JsonObject modulesJson = root.has("modules") && root.get("modules").isJsonObject()
					? root.getAsJsonObject("modules") : null;

			if (modulesJson != null) {
				for (Module module : Argon.INSTANCE.getModuleManager().getModules()) {
					int idx = Argon.INSTANCE.getModuleManager().getModules().indexOf(module);
					JsonElement moduleEl = modulesJson.get(String.valueOf(idx));
					if (moduleEl == null || !moduleEl.isJsonObject()) continue;
					JsonObject moduleConfig = moduleEl.getAsJsonObject();

					JsonElement enabledEl = moduleConfig.get("enabled");
					if (enabledEl != null && enabledEl.isJsonPrimitive()) {
						module.setEnabled(enabledEl.getAsBoolean());
					}

					JsonElement keyEl = moduleConfig.get("key");
					if (keyEl != null && keyEl.isJsonPrimitive()) {
						module.setKey(keyEl.getAsInt());
					}

					for (Setting<?> setting : module.getSettings()) {
						int sIdx = module.getSettings().indexOf(setting);
						JsonElement settingEl = moduleConfig.get(String.valueOf(sIdx));
						if (settingEl == null) continue;

						if (setting instanceof BooleanSetting s) {
							s.setValue(settingEl.getAsBoolean());
						} else if (setting instanceof ModeSetting<?> s) {
							s.setModeIndex(settingEl.getAsInt());
						} else if (setting instanceof NumberSetting s) {
							s.setValue(settingEl.getAsDouble());
						} else if (setting instanceof KeybindSetting s) {
							s.setKey(settingEl.getAsInt());
							if (s.isModuleKey()) module.setKey(settingEl.getAsInt());
						} else if (setting instanceof StringSetting s) {
							s.setValue(settingEl.getAsString());
						} else if (setting instanceof MinMaxSetting s && settingEl.isJsonObject()) {
							JsonObject minMax = settingEl.getAsJsonObject();
							if (minMax.has("1")) s.setMinValue(minMax.get("1").getAsDouble());
							if (minMax.has("2")) s.setMaxValue(minMax.get("2").getAsDouble());
						}
					}
				}
			}

			// Load friends
			JsonElement friendsEl = root.get("friends");
			if (friendsEl != null && friendsEl.isJsonArray()) {
				Set<String> friends = new java.util.HashSet<>();
				for (JsonElement el : friendsEl.getAsJsonArray()) {
					if (el.isJsonPrimitive()) friends.add(el.getAsString());
				}
				Argon.INSTANCE.getFriendManager().setFriends(friends);
			}

			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public List<String> listConfigs() {
		try {
			Path folder = getConfigFolder();
			if (!Files.isDirectory(folder)) return List.of();
			List<String> names = new ArrayList<>();
			try (var stream = Files.list(folder)) {
				stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json"))
						.map(p -> {
							String fn = p.getFileName().toString();
							return fn.substring(0, fn.length() - 5);
						})
						.forEach(names::add);
			}
			return names;
		} catch (Exception e) {
			return List.of();
		}
	}

	public boolean deleteConfig(String name) {
		Path path = getConfigPath(name);
		if (path == null || !Files.isRegularFile(path)) return false;
		try {
			Files.delete(path);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
