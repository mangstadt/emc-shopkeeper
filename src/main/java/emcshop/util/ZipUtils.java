package emcshop.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;

/**
 * Contains ZIP file utility methods.
 */
public final class ZipUtils {
	/**
	 * Zips a directory.
	 * @param directory the directory to zip
	 * @param zipFile the ZIP file
	 * @param listener called every time a file is zipped
	 * @throws IOException if there's a problem creating the ZIP file
	 */
	public static void zipDirectory(File directory, File zipFile, ZipListener listener) throws IOException {
		ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile));

		String rootPath = directory.getParent();
		LinkedList<File> folders = new LinkedList<File>();
		folders.add(directory);

		try {
			while (!folders.isEmpty()) {
				File folder = folders.removeLast();

				String folderPath;
				{
					String zipParent;
					String folderParent = folder.getParent();
					if (folderParent == null) {
						zipParent = "";
					} else {
						zipParent = folderParent;
						if (rootPath != null) {
							zipParent = zipParent.substring(rootPath.length());
						}
					}
					folderPath = zipParent + "/" + folder.getName() + "/";
				}

				File files[] = folder.listFiles();
				if (files.length == 0) {
					//add folder to zip
					zip.putNextEntry(new ZipEntry(folderPath + "empty")); //TODO empty dirs are not being added
					continue;
				}

				//add files to zip
				for (File file : files) {
					if (file.isDirectory()) {
						folders.add(file);
						continue;
					}

					FileInputStream in = new FileInputStream(file);
					try {
						zip.putNextEntry(new ZipEntry(folderPath + file.getName()));
						IOUtils.copy(in, zip);
						if (listener != null) {
							listener.onZippedFile(file);
						}
					} finally {
						in.close();
					}
				}
			}
		} finally {
			zip.close();
		}
	}

	/**
	 * Gets the total size of all files in a directory.
	 * @param directory the directory
	 * @return the total size
	 */
	public static long getDirectorySize(File directory) {
		long size = 0;

		LinkedList<File> folders = new LinkedList<File>();
		folders.add(directory);
		while (!folders.isEmpty()) {
			File folder = folders.removeLast();

			for (File file : folder.listFiles()) {
				if (file.isDirectory()) {
					folders.add(file);
					continue;
				}

				size += file.length();
			}
		}

		return size;
	}

	/**
	 * Unzips a ZIP file.
	 * @param destinationDir the directory where the ZIP file should be unzipped
	 * @param zipFile the ZIP file
	 * @throws IOException if there's a problem extracting the ZIP file
	 */
	public static void unzip(File destinationDir, File zipFile) throws IOException {
		InputStream in = new BufferedInputStream(new FileInputStream(zipFile));
		ZipInputStream zin = new ZipInputStream(in);
		try {
			byte[] buffer = new byte[4096];
			ZipEntry entry;
			while ((entry = zin.getNextEntry()) != null) {
				String zipPath = entry.getName();
				File file = new File(destinationDir, zipPath);

				//create all non-existent parent folders
				new File(file.getParent()).mkdirs();

				//ignore the "empty" files that were added to empty directories
				if (entry.getCompressedSize() <= 0 && file.getName().equals("empty")) {
					continue;
				}

				//create the file
				FileOutputStream fos = new FileOutputStream(file);
				try {
					//note: IOUtils.copy() doesn't work for some reason
					int len;
					while ((len = zin.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
				} finally {
					fos.close();
				}
			}
		} finally {
			IOUtils.closeQuietly(zin);
		}
	}

	public static interface ZipListener {
		void onZippedFile(File file);
	}

	private ZipUtils() {
		//hide
	}
}