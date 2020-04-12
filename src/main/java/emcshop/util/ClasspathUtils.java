package emcshop.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Contains classpath-related utility methods.
 * @author Michael Angstadt
 */
public final class ClasspathUtils {
	/**
	 * The same as {@link Class#getResourceAsStream(String)}, except throws a
	 * {@link FileNotFoundException} when null is returned.
	 * @param file the file name of the resource
	 * @param relativeLocation the class to use as the base location
	 * @return an input stream to the resource
	 * @throws FileNotFoundException if the resource does not exist
	 */
	public static InputStream getResourceAsStream(String file, Class<?> relativeLocation) throws FileNotFoundException {
		InputStream in = relativeLocation.getResourceAsStream(file);
		if (in == null) {
			String path;
			if (file.startsWith("/")) {
				path = file.substring(1); //remove starting slash so it's the same as Package.getName()
			} else {
				path = relativeLocation.getPackage().getName().replaceAll("\\.", "/");
				path += "/" + file;
			}
			throw new FileNotFoundException("File not found on classpath: " + path);
		}
		return in;
	}

	/**
	 * The same as {@link Class#getResourceAsStream(String)}, except throws a
	 * {@link FileNotFoundException} when null is returned.
	 * @param absolutePath the absolute path to the resource
	 * @return an input stream to the resource
	 * @throws FileNotFoundException if the resource does not exist
	 */
	public static InputStream getResourceAsStream(String absolutePath) throws FileNotFoundException {
		return getResourceAsStream(absolutePath, ClasspathUtils.class);
	}

	/**
	 * Lists all the files found in a given package on the classpath.
	 * @param packageName the package name (e.g. "emcshop.util")
	 * @return the files
	 * @throws IOException if there's a problem reading from any JAR files on
	 * the classpath
	 */
	public static List<URI> listFilesInPackage(String packageName) throws IOException {
		List<URI> filesInPackage = new ArrayList<>();
		String packagePath = packageName.replace(".", File.separator) + File.separator;

		String cp = System.getProperty("java.class.path");
		List<Path> files = Arrays.stream(cp.split(File.pathSeparator)) //@formatter:off
			.map(Paths::get)
			.filter(Files::exists)
		.collect(Collectors.toList()); //@formatter:on

		for (Path file : files) {
			if (Files.isDirectory(file)) {
				Path dir = file.resolve(packagePath);
				if (Files.isDirectory(dir)) {
					Files.list(dir) //@formatter:off
						.map(Path::toUri)
					.forEach(filesInPackage::add); //@formatter:on
				}

				continue;
			}

			if (file.getFileName().toString().endsWith(".jar")) {
				filesInPackage.addAll(listFilesInPackageFromJar(file, packageName));
				continue;
			}
		}

		return filesInPackage;
	}

	//this is in its own method so it can be unit tested (I can't add a JAR to the classpath when the unit tests are run)
	static List<URI> listFilesInPackageFromJar(Path jar, String packageName) throws IOException {
		packageName = packageName.replace(".", "/");

		try (JarFile jarFile = new JarFile(jar.toFile())) {
			List<URI> filesInPackage = new ArrayList<>();
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();

				//an entry's name looks like: "emcshop/gui/images/items/diamond.png"
				String entryName = entry.getName();

				if (!entry.isDirectory() && entryName.startsWith(packageName)) {
					URI uri = jar.toUri();
					uri = URI.create("jar:" + uri.getPath() + "!/" + entryName);
					filesInPackage.add(uri);
				}
			}
			return filesInPackage;
		}
	}

	private ClasspathUtils() {
		//hide
	}
}
