package emcshop.gui.lib;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A utility class for working around the java Web Start jar signing/security
 * bug.
 * @see "http://bugs.sun.com/view_bug.do?bug_id=6967414"
 * @see "http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6805618"
 * @see "https://community.oracle.com/thread/1305543"
 * @see "http://stackoverflow.com/a/13513987/13379"
 * @author Scott Chan
 */
public class JarSignersHardLinker {
	private static final Logger logger = Logger.getLogger(JarSignersHardLinker.class.getName());
	private static final List<Object> hardRefs = new ArrayList<Object>();

	private static void makeHardSignersRef(JarFile jar) throws IOException {
		logger.fine("Making hard refs for: " + jar);
		if (!jar.getClass().getName().equals("com.sun.deploy.cache.CachedJarFile")) {
			return;
		}

		//lets attempt to get at the each of the soft links.
		//first neet to call the relevant no-arg method to ensure that the soft ref is populated
		//then we access the private member, resolve the softlink and throw it in a static list.

		callNoArgMethod("getSigners", jar);
		makeHardLink("signersRef", jar);

		callNoArgMethod("getSignerMap", jar);
		makeHardLink("signerMapRef", jar);

		//            callNoArgMethod("getCodeSources", jar);
		//            makeHardLink("codeSourcesRef", jar);

		callNoArgMethod("getCodeSourceCache", jar);
		makeHardLink("codeSourceCacheRef", jar);

		//see: http://stackoverflow.com/a/14947422/13379
		callNoArgMethod("getSigningData", jar);
		makeHardLink("signingDataRef", jar);
		callNoArgMethod("getManifest", jar);
		makeHardLink("manRef", jar);
	}

	/**
	 * If the specified field for the given instance is a {@link SoftReference},
	 * it is resolved and the returned reference is stored in a static list,
	 * making it a hard link that should never be garbage collected.
	 * @param fieldName the field name
	 * @param instance the instance
	 */
	private static void makeHardLink(String fieldName, Object instance) {
		logger.fine("Attempting hard ref to " + instance.getClass().getName() + "." + fieldName);
		try {
			Field signersRef = instance.getClass().getDeclaredField(fieldName);
			signersRef.setAccessible(true);

			Object o = signersRef.get(instance);
			if (!(o instanceof SoftReference)) {
				return;
			}

			SoftReference<?> r = (SoftReference<?>) o;
			Object o2 = r.get();
			hardRefs.add(o2);
		} catch (Throwable t) {
			logger.log(Level.FINE, "Problem accessing field: " + fieldName, t);
		}
	}

	/**
	 * Calls the given no-arg method on the given instance.
	 * @param methodName the method name
	 * @param instance the instance
	 */
	private static void callNoArgMethod(String methodName, Object instance) {
		logger.fine("Calling noarg method hard ref to " + instance.getClass().getName() + "." + methodName + "()");
		try {
			Method m = instance.getClass().getDeclaredMethod(methodName);
			m.setAccessible(true);

			m.invoke(instance);
		} catch (Throwable t) {
			logger.log(Level.FINE, "Problem calling method: " + methodName, t);
		}
	}

	/**
	 * Determines if the application is running on Web Start.
	 * @return true if it's running on Sun's implementation of Web Start, false
	 * if not
	 */
	public static boolean isRunningOnWebstart() {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();

		while (cl != null) {
			if (cl.getClass().getName().equals("com.sun.jnlp.JNLPClassLoader")) {
				return true;
			}
			cl = cl.getParent();
		}

		return false;
	}

	/**
	 * Gets all the {@link JarFile} objects for all of the jars in the
	 * classpath.
	 * @return the {@link JarFile} objects
	 * @throws IOException if there's a problem getting the list of JARs
	 */
	private static Set<JarFile> getAllJarsFilesInClassPath() throws IOException {
		Set<JarFile> jars = new LinkedHashSet<JarFile>();
		for (URL url : getAllJarUrls()) {
			try {
				JarFile jar = getJarFile(url);
				if (jar != null) {
					jars.add(jar);
				}
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Unable to retrieve jar at URL: " + url, e);
			}
		}
		return jars;
	}

	/**
	 * Returns set of URLs for the jars in the classpath.
	 * @return the URLs (e.g.
	 * "jar:http://HOST/PATH/JARNAME.jar!/META-INF/MANIFEST.MF")
	 * @throws IOException if there's a problem getting the list of JARs
	 */
	private static Set<URL> getAllJarUrls() throws IOException {
		Set<URL> urls = new LinkedHashSet<URL>();
		Enumeration<URL> mfUrls = Thread.currentThread().getContextClassLoader().getResources("META-INF/MANIFEST.MF");
		while (mfUrls.hasMoreElements()) {
			URL jarUrl = mfUrls.nextElement();
			if (jarUrl.getProtocol().equals("jar")) {
				urls.add(jarUrl);
			}
		}
		return urls;
	}

	/**
	 * Gets the {@link JarFile} object for the given URL.
	 * @param jarUrl the JAR URL
	 * @return the JAR file
	 * @throws IOException if there's a problem reading the JAR file
	 */
	private static JarFile getJarFile(URL jarUrl) throws IOException {
		URLConnection urlConnection = jarUrl.openConnection();
		if (!(urlConnection instanceof JarURLConnection)) {
			logger.severe("Expected JarURLConnection, but was " + urlConnection.getClass().getSimpleName());
			return null;
		}

		// Using a JarURLConnection will load the JAR from the cache when using Webstart 1.6
		// In Webstart 1.5, the URL will point to the cached JAR on the local filesystem
		JarURLConnection jcon = (JarURLConnection) urlConnection;
		return jcon.getJarFile();
	}

	/**
	 * Spawns a new thread to run through each jar in the classpath and create a
	 * hardlink to the JARs' softly-referenced signer information.
	 */
	public static void go() {
		if (!isRunningOnWebstart()) {
			return;
		}

		logger.fine("Starting Resource Preloader Hardlinker");

		Thread thread = new Thread(() -> {
			try {
				Set<JarFile> jars = getAllJarsFilesInClassPath();
				for (JarFile jar : jars) {
					makeHardSignersRef(jar);
				}
			} catch (Throwable t) {
				logger.log(Level.SEVERE, "Problem preloading resources.", t);
			}
		});
		thread.setDaemon(true);
		thread.start();
	}
}
