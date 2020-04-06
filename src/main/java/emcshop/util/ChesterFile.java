package emcshop.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * Parses files generated by the Chester mod.
 */
public class ChesterFile {
	private final String version;
	private final double playerX, playerY, playerZ;
	private final Map<String, Integer> items;

	/**
	 * Parses a Chester file
	 * @param file the file to parse
	 * @return the parsed data
	 * @throws IOException if the file couldn't be read
	 * @throws IllegalArgumentException if the file couldn't be parsed
	 */
	public static ChesterFile parse(File file) throws IOException {
		try (Reader reader = new FileReader(file)) {
			return parse(reader);
		}
	}

	/**
	 * Parses a Chester file.
	 * @param reader the input stream to the file
	 * @return the parsed data
	 * @throws IOException if the file couldn't be read
	 * @throws IllegalArgumentException if the file couldn't be parsed
	 */
	public static ChesterFile parse(Reader reader) throws IOException {
		BufferedReader bufReader = new BufferedReader(reader);
		try {
			String version = bufReader.readLine();

			String line = bufReader.readLine();
			if (line == null) {
				throw new IllegalArgumentException("Player coordinates expected.");
			}

			String split[] = line.split(" ", 3);
			double playerX = Double.parseDouble(split[0]);
			double playerY = Double.parseDouble(split[1]);
			double playerZ = Double.parseDouble(split[2]);

			Map<String, Integer> items = new HashMap<String, Integer>();
			while ((line = bufReader.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}

				split = line.split(" ", 2);
				String id = split[0];
				if (id.contains("-")) {
					//it's an item name
					id = id.replace('-', ' ');
				}
				int quantity = Integer.parseInt(split[1]);
				items.put(id, quantity);
			}

			return new ChesterFile(version, playerX, playerY, playerZ, items);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(e);
		} catch (IndexOutOfBoundsException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private ChesterFile(String version, double playerX, double playerY, double playerZ, Map<String, Integer> items) {
		this.version = version;
		this.playerX = playerX;
		this.playerY = playerY;
		this.playerZ = playerZ;
		this.items = ImmutableMap.copyOf(items);
	}

	public String getVersion() {
		return version;
	}

	public double getPlayerX() {
		return playerX;
	}

	public double getPlayerY() {
		return playerY;
	}

	public double getPlayerZ() {
		return playerZ;
	}

	public Map<String, Integer> getItems() {
		return items;
	}
}
