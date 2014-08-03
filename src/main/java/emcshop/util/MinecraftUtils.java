package emcshop.util;

import java.io.File;

import org.apache.commons.io.FileUtils;

/**
 * Misc Minecraft-related utilities.
 */
public class MinecraftUtils {
	/**
	 * Gets the location of the default Minecraft installation.
	 * @return the Minecraft directory or null if unknown
	 */
	public static File getDefaultMinecraftFolder() {
		if (OS.isWindows()) {
			String appData = System.getenv("appdata");
			return new File(appData, ".minecraft");
		}

		if (OS.isMac()) {
			File library = new File(FileUtils.getUserDirectory(), "Library");
			File applicationSupport = new File(library, "Application Support");
			return new File(applicationSupport, "minecraft");
		}

		if (OS.isLinux()) {
			return new File(FileUtils.getUserDirectory(), ".minecraft");
		}

		return null;
	}
}
