package emcshop;

import java.util.ArrayList;
import java.util.Iterator;
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
     *
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
     *
     * @param <T>   the object class
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
     * Adds an object.
     *
     * @param object the object to add
     */
    public void add(Object object) {
        objects.add(object);
    }

    /**
     * Removes an objects that share the same class as the given object, then
     * adds the given object to the context.
     *
     * @param object the object to add
     */
    public void set(Object object) {
        remove(object.getClass());
        add(object);
    }

    /**
     * Removes an object.
     *
     * @param clazz the class of the object to remove
     * @return the removed object or null if it couldn't find an object to
     * remove
     */
    public <T> T remove(Class<T> clazz) {
        Iterator<Object> it = objects.iterator();
        while (it.hasNext()) {
            Object object = it.next();
            if (clazz.isInstance(object)) {
                it.remove();
                return clazz.cast(object);
            }
        }
        return null;
    }
}
