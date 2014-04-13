package emcshop;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds objects that are used throughout the application.
 */
public class AppContext {
	private static AppContext INSTANCE;

	private final List<Object> objects = new ArrayList<Object>();

	AppContext() {
		//empty
	}

	/**
	 * Gets the application context
	 * @return the application context
	 */
	public static synchronized AppContext instance() {
		if (INSTANCE == null) {
			INSTANCE = new AppContext();
		}
		return INSTANCE;
	}

	public static synchronized void init(Object... objects) {
		if (INSTANCE == null) {
			INSTANCE = new AppContext();
		}

		INSTANCE.objects.clear();
		for (Object object : objects) {
			INSTANCE.add(object);
		}
	}

	/**
	 * Gets an object.
	 * @param <T> the object class
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

	/**
	 * Adds an object
	 * @param object the object to add
	 */
	public void add(Object object) {
		objects.add(object);
	}
}
