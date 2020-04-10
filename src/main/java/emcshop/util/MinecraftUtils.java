package emcshop.util;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;

/**
 * Misc Minecraft-related utilities.
 * @author Michael Angstadt
 */
public final class MinecraftUtils {
	/**
	 * Gets the location of the default Minecraft installation.
	 * @return the Minecraft directory or null if unknown
	 */
	public static Path getDefaultMinecraftFolder() {
		if (OS.isWindows()) {
			return Paths.get(System.getenv("appdata"), ".minecraft");
		}

		Path userDir = FileUtils.getUserDirectory().toPath();

		if (OS.isMac()) {
			return userDir.resolve(Paths.get("Library", "Application Support", "minecraft"));
		}

		if (OS.isLinux()) {
			return userDir.resolve(".minecraft");
		}

		return null;
	}

	private MinecraftUtils() {
		//hide
	}
}
