package emcshop.util;

import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;

/**
 * Caches images.
 * @author Michael Angstadt
 */
public class ImageCache {
	protected final Map<String, ImageIcon> cache = new HashMap<>();

	/**
	 * Gets an image from the cache.
	 * @param name the image name
	 * @param maxSize the size of the image
	 * @return the image or null if it doesn't exist
	 */
	public ImageIcon get(String name, int maxSize) {
		String key = key(name, maxSize);
		synchronized (cache) {
			return cache.get(key);
		}
	}

	/**
	 * Adds an image to the cache.
	 * @param name the image name
	 * @param maxSize the size of the image
	 * @param image the image
	 */
	public void put(String name, int maxSize, ImageIcon image) {
		String key = key(name, maxSize);
		synchronized (cache) {
			cache.put(key, image);
		}
	}

	/**
	 * Empties the cache of all sizes of an image.
	 * @param name the image name
	 */
	public void clear(String name) {
		String prefix = prefix(name);
		synchronized (cache) {
			cache.keySet().removeIf(key -> key.startsWith(prefix));
		}
	}

	protected String key(String name, int maxSize) {
		return prefix(name) + maxSize;
	}

	protected String prefix(String name) {
		return name.toLowerCase() + '|';
	}
}
