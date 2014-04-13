package emcshop;

import java.util.Arrays;
import java.util.List;

/**
 * Holds objects that are used throughout the application.
 */
public class AppContext {
	private static AppContext INSTANCE;

	private final List<Object> objects;

	/**
	 * @param objects the objects to add to the context
	 */
	private AppContext(Object... objects) {
		this.objects = Arrays.asList(objects);
	}

	/**
	 * Creates the application context.
	 * @param objects the objects to add to the context
	 * @return the application context
	 */
	public static synchronized AppContext init(Object... objects) {
		INSTANCE = new AppContext(objects);
		return INSTANCE;
	}

	/**
	 * Gets the application context
	 * @return the application context
	 */
	public static synchronized AppContext instance() {
		return INSTANCE;
	}

	/**
	 * Gets an object.
	 * @param <T>
	 * @param clazz the object class
	 * @return the object or null if not found
	 */
	public <T> T get(Class<T> clazz) {
		for (Object object : objects) {
			if (clazz.isInstance(object)) {
				return clazz.cast(object);
			}
		}
		return null;
	}
}
