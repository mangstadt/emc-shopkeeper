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
	private static final String JRE_1_6_0 = "1.6.0_";

	/**
	 * The 1.6.0 update where this problem first occurred.
	 */
	private static final int PROBLEM_JRE_UPDATE = 19;

	public static final List<Object> sm_hardRefs = new ArrayList<Object>();

	private static void makeHardSignersRef(JarFile jar) throws java.io.IOException {
		logger.fine("Making hard refs for: " + jar);

		if (jar != null && jar.getClass().getName().equals("com.sun.deploy.cache.CachedJarFile")) {

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
	}

	/**
	 * If the specified field for the given instance is a {@link SoftReference},
	 * it is resolved and the returned ref is stored in a static list, making it
	 * a hard link that should never be garbage collected
	 * @param fieldName
	 * @param instance
	 */
	private static void makeHardLink(String fieldName, Object instance) {
		logger.fine("Attempting hard ref to " + instance.getClass().getName() + "." + fieldName);
		try {
			Field signersRef = instance.getClass().getDeclaredField(fieldName);

			signersRef.setAccessible(true);

			Object o = signersRef.get(instance);

			if (o instanceof SoftReference) {
				SoftReference<?> r = (SoftReference<?>) o;
				Object o2 = r.get();
				sm_hardRefs.add(o2);
			}
		} catch (Throwable t) {
			logger.log(Level.FINE, "Problem accessing field.", t);
		}
	}

	/**
	 * Call the given no-arg method on the given instance
	 * @param methodName
	 * @param instance
	 */
	private static void callNoArgMethod(String methodName, Object instance) {
		logger.fine("Calling noarg method hard ref to " + instance.getClass().getName() + "." + methodName + "()");
		try {
			Method m = instance.getClass().getDeclaredMethod(methodName);
			m.setAccessible(true);

			m.invoke(instance);
		} catch (Throwable t) {
			logger.log(Level.FINE, "Problem calling method.", t);
		}
	}

	/**
	 * is the preloader enabled. ie: will the preloader run in the current
	 * environment
	 * @return
	 */
	private static boolean isHardLinkerEnabled() {
		boolean isHardLinkerDisabled = false; //change this to use whatever mechanism you use to enable or disable the preloader

		return !isHardLinkerDisabled && isRunningOnJre1_6_0_19OrHigher() && isRunningOnWebstart();
	}

	/**
	 * Determines if the application is running on Web Start.
	 * @return
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
	 * Determines if the JRE is version 1.6.0_19 or higher.
	 * @return true if it's 1.6.0_19 or higher, false if not
	 */
	private static boolean isRunningOnJre1_6_0_19OrHigher() {
		String javaVersion = System.getProperty("java.version");

		if (javaVersion.startsWith(JRE_1_6_0)) {
			//then lets figure out what update we are on
			String updateStr = javaVersion.substring(JRE_1_6_0.length());

			try {
				return Integer.parseInt(updateStr) >= PROBLEM_JRE_UPDATE;
			} catch (NumberFormatException e) {
				//then unable to determine updatedate level
				return false;
			}
		}

		//all other cases
		return false;
	}

	/**
	 * Gets all the {@link JarFile} objects for all of the jars in the
	 * classpath.
	 * @return the {@link JarFile} objects
	 */
	private static Set<JarFile> getAllJarsFilesInClassPath() {
		Set<JarFile> jars = new LinkedHashSet<JarFile>();
		for (URL url : getAllJarUrls()) {
			try {
				jars.add(getJarFile(url));
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
	 */
	private static Set<URL> getAllJarUrls() {
		try {
			Set<URL> urls = new LinkedHashSet<URL>();
			Enumeration<URL> mfUrls = Thread.currentThread().getContextClassLoader().getResources("META-INF/MANIFEST.MF");
			while (mfUrls.hasMoreElements()) {
				URL jarUrl = mfUrls.nextElement();
				if (!jarUrl.getProtocol().equals("jar")) {
					continue;
				}
				urls.add(jarUrl);
			}
			return urls;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Gets the {@link JarFile} object for the given URL.
	 * @param jarUrl the JAR URL
	 * @return the JAR file
	 * @throws IOException
	 */
	private static JarFile getJarFile(URL jarUrl) throws IOException {
		URLConnection urlConnnection = jarUrl.openConnection();
		if (urlConnnection instanceof JarURLConnection) {
			// Using a JarURLConnection will load the JAR from the cache when using Webstart 1.6
			// In Webstart 1.5, the URL will point to the cached JAR on the local filesystem
			JarURLConnection jcon = (JarURLConnection) urlConnnection;
			return jcon.getJarFile();
		} else {
			throw new AssertionError("Expected JarURLConnection");
		}
	}

	/**
	 * Spawns a new thread to run through each jar in the classpath and create a
	 * hardlink to the jars softly referenced signers infomation.
	 */
	public static void go() {
		if (!isHardLinkerEnabled()) {
			return;
		}

		logger.fine("Starting Resource Preloader Hardlinker");

		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					Set<JarFile> jars = getAllJarsFilesInClassPath();

					for (JarFile jar : jars) {
						makeHardSignersRef(jar);
					}
				} catch (Throwable t) {
					logger.log(Level.SEVERE, "Problem preloading resources.", t);
				}
			}
		});
		t.setDaemon(true);
		t.start();
	}
}
