package emcshop.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;

/**
 * Caches images.
 */
public class ImageCache {
	protected final Map<String, ImageIcon> cache = Collections.synchronizedMap(new HashMap<String, ImageIcon>());

	public ImageIcon get(String name, int maxSize) {
		return cache.get(key(name, maxSize));
	}

	public void put(String name, int maxSize, ImageIcon image) {
		cache.put(key(name, maxSize), image);
	}

	protected String key(String name, int maxSize) {
		return name.toLowerCase() + '|' + maxSize;
	}
}
