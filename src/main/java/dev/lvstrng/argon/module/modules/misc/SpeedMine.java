package dev.lvstrng.argon.module.modules.misc;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.utils.EncryptedString;

/**
 * Removes block break cooldown so blocks can be broken faster.
 */
public final class SpeedMine extends Module {
	public SpeedMine() {
		super(EncryptedString.of("Speed Mine"),
				EncryptedString.of("Removes block break delay for faster mining"),
				-1,
				Category.MISC);
	}
}
