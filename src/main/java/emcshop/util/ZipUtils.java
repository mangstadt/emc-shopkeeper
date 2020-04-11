package emcshop.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.io.ByteStreams;

/**
 * Contains zip file utility methods.
 * @author Michael Angstadt
 */
public final class ZipUtils {
	private static final Logger logger = Logger.getLogger(ZipUtils.class.getName());

	/**
	 * Copies a directory into a zip file. The directory will be copied to the
	 * root of the zip file, meaning that root of the zip file will contain a
	 * single directory and nothing else.
	 * @param directory the directory to compress
	 * @param zipFile the zip file to create
	 * @param listener called every time a file is added to the zip file (may be
	 * null)
	 * @throws IOException if there's a problem creating the zip file
	 */
	public static void zipDirectory(Path directory, Path zipFile, ZipListener listener) throws IOException {
		long directorySize = (listener == null) ? 0 : getDirectorySize(directory);
		long[] bytesZipped = { 0 };
		int directoryPathPos = directory.getNameCount() - 1;

		try (FileSystem destZip = openNewZipFile(zipFile)) {
			Files.walk(directory).forEach(src -> {
				/*
				 * Get rid of parent directories when copying to zip file.
				 * 
				 * For example, if the path of the directory being zipped is
				 * "C:\one\two\three", this ensures that the root folder in the
				 * zip file is "three".
				 */
				Path dest = destZip.getPath(src.subpath(directoryPathPos, src.getNameCount()).toString());

				try {
					if (Files.isDirectory(src)) {
						Files.createDirectories(dest);
					} else {
						Files.createDirectories(dest.getParent());
						Files.copy(src, dest);

						if (listener != null) {
							try {
								bytesZipped[0] += Files.size(src);
							} catch (IOException e) {
								logger.log(Level.WARNING, "Could not get size of file: " + src, e);
							}
							int percent = (directorySize == 0) ? 0 : (int) ((double) bytesZipped[0] / directorySize * 100);
							listener.onZippedFile(src, percent);
						}
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	/**
	 * Gets the total size of all files in a directory.
	 * @param directory the directory
	 * @return the total size in bytes
	 * @throws IOException if there's a problem reading the files
	 */
	public static long getDirectorySize(Path directory) throws IOException {
		Predicate<Path> isFile = file -> !Files.isDirectory(file);
		ToLongFunction<Path> getSize = file -> {
			try {
				return Files.size(file);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		};

		try {
			return Files.walk(directory) //@formatter:off
				.filter(isFile)
				.mapToLong(getSize)
			.sum(); //@formatter:on
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	/**
	 * Extracts the contents of a zip file.
	 * @param directory the directory where the zip file should be extracted to
	 * @param zipFile the zip file
	 * @throws IOException if there's a problem extracting the zip file
	 */
	public static void unzip(Path directory, Path zipFile) throws IOException {
		try (FileSystem srcZip = openExistingZipFile(zipFile)) {
			Files.walk(srcZip.getPath("/")).forEach(src -> {
				Path dest = directory.resolve(src.toString().substring(1));

				try {
					if (Files.isDirectory(src)) {
						Files.createDirectories(dest);
					} else {
						Files.createDirectories(dest.getParent());
						Files.copy(src, dest);
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	/**
	 * <p>
	 * Fixes a ZIP file that was created with old code. This method reads the
	 * contents of the zip file using the old code, and then copies each file to
	 * a new zip file using Java's newer FileSystem API. The old zip file is
	 * then replaced with the new one.
	 * </p>
	 * <p>
	 * Something about the old code was bugged because other programs had
	 * trouble reading the zip file:
	 * </p>
	 * <ul>
	 * <li>On Windows, File Explorer said the file was empty.</li>
	 * <li>In 7zip, everything was inside of a root directory named "_".</li>
	 * <li>While walking through the compressed files using Java's FileSystem
	 * API, it would throw an exception.</li>
	 * </ul>
	 * @param file the zip file to fix
	 * @throws IOException if there's a problem fixing the zip file
	 */
	public static void repairCorruptedZipFile(Path file) throws IOException {
		Path destFile = file.resolveSibling(file.getFileName() + ".tmp");

		try ( //@formatter:off
			ZipInputStream srcZip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(file)));
			FileSystem destZip = openNewZipFile(destFile) //@formatter:on
		) {
			ZipEntry entry;
			while ((entry = srcZip.getNextEntry()) != null) {
				String src = entry.getName();
				Path dest = destZip.getPath(src);

				//create all non-existent parent folders
				Path destParent = dest.getParent();
				if (destParent != null) {
					Files.createDirectories(destParent);
				}

				//ignore the "empty" files that were added to empty directories
				if (entry.getCompressedSize() <= 0 && dest.getFileName().toString().equals("empty")) {
					continue;
				}

				//copy the file to the new zip archive
				try (OutputStream out = Files.newOutputStream(dest)) {
					ByteStreams.copy(srcZip, out);
				}
			}
		} catch (IOException | RuntimeException e) {
			//delete the destination zip file if the operation failed
			try {
				Files.delete(destFile);
			} catch (IOException ignore) {
			}
			throw e;
		}

		/*
		 * Delete the corrupted zip file, now that the new one has been created.
		 * 
		 * This line of code must be called after the file handle to the new zip
		 * file has been closed to ensure that the file was created
		 * successfully. So, this line of code should not be put in the above
		 * try block, even though its catch block is identical to the one above.
		 */
		try {
			Files.delete(file);
		} catch (IOException e) {
			//delete the destination zip file if the old file can't be deleted
			try {
				Files.delete(destFile);
			} catch (IOException ignore) {
			}
			throw e;
		}

		//replace the new zip file with the old one
		Files.move(destFile, file);
	}

	/**
	 * Creates a new zip file and opens it.
	 * @param file the zip file
	 * @return the file handle
	 * @throws IOException if there's a problem creating and opening the file
	 */
	public static FileSystem openNewZipFile(Path file) throws IOException {
		return openZipFile(file, true);
	}

	/**
	 * Opens an existing zip file.
	 * @param file the zip file
	 * @return the file handle
	 * @throws IOException if there's a problem opening the file
	 */
	public static FileSystem openExistingZipFile(Path file) throws IOException {
		return openZipFile(file, false);
	}

	private static FileSystem openZipFile(Path file, boolean create) throws IOException {
		URI uri = URI.create("jar:file:" + file.toUri().getPath());

		Map<String, String> env = new HashMap<>();
		if (create) {
			env.put("create", "true");
		}

		return FileSystems.newFileSystem(uri, env);
	}

	/**
	 * Callback function for {@link ZipUtils#zipDirectory}.
	 */
	public static interface ZipListener {
		/**
		 * Called when a file was added to the zip archive.
		 * @param file the file that was added
		 * @param percentComplete the percentage of total bytes that have been
		 * added to the zip file (0-100)
		 */
		void onZippedFile(Path file, int percentComplete);
	}

	private ZipUtils() {
		//hide
	}
}
