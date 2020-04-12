package emcshop.util;

import static emcshop.util.TestUtils.assertFileContent;
import static emcshop.util.TestUtils.mkdir;
import static emcshop.util.TestUtils.mkfile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import emcshop.util.ZipUtils.ZipListener;

/**
 * @author Michael Angstadt
 */
public class ZipUtilsTest {
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void getDirectorySize() throws Exception {
		Path root = temp.getRoot().toPath();

		mkfile(root, "file1.txt", "a");
		mkfile(root, "file2.txt", "ab");
		Path dir = mkdir(root, "dir");
		mkfile(dir, "file3.txt", "abc");

		assertEquals(6, ZipUtils.getDirectorySize(root));
	}

	@Test
	public void zipAndUnzipDirectory() throws Exception {
		Path root = temp.getRoot().toPath();
		Path zip = root.resolve("zip.zip");

		//create directory structure for testing
		//@formatter:off
		Path dir = mkdir(root, "zipMe");
			Path file1 = mkfile(dir, "file1.txt", "data1");
			Path file2 = mkfile(dir, "file2.txt", "data2");
			Path dir2 = mkdir(dir, "folder2");
				Path file3 = mkfile(dir2, "file3.txt", "data3");
			Path dir3 = mkdir(dir, "folder3");
		//@formatter:on

		{
			ZipListener listener = mock(ZipListener.class);
			ZipUtils.zipDirectory(dir, zip, listener);

			verify(listener).onZippedFile(file1, 33);
			verify(listener).onZippedFile(file2, 66);
			verify(listener).onZippedFile(file3, 100);

			Set<String> actualPaths;
			try (FileSystem zipFs = ZipUtils.openExistingZipFile(zip)) {
				actualPaths = Files.walk(zipFs.getPath("/")) //@formatter:off
					.map(Path::toString)
				.collect(Collectors.toSet()); //@formatter:on
			}

			Set<String> expectedPaths = new HashSet<>(Arrays.asList( //@formatter:off
				"/",
				"/" + dir.getFileName() + "/",
				"/" + dir.getFileName() + "/" + file1.getFileName(),
				"/" + dir.getFileName() + "/" + file2.getFileName(),
				"/" + dir.getFileName() + "/" + dir2.getFileName() + "/",
				"/" + dir.getFileName() + "/" + dir2.getFileName() + "/" + file3.getFileName(),
				"/" + dir.getFileName() + "/" + dir3.getFileName() + "/"
			)); //@formatter:on

			assertEquals(expectedPaths, actualPaths);
		}

		{
			Path destinationDir = root.resolve("destination");

			ZipUtils.unzip(destinationDir, zip);

			Set<Path> actualPaths = Files.walk(destinationDir).collect(Collectors.toSet());

			Set<Path> expectedPaths = new HashSet<>(Arrays.asList( //@formatter:off
				destinationDir,
				destinationDir.resolve(dir.getFileName()),
				destinationDir.resolve(dir.getFileName() + "/" + file1.getFileName()),
				destinationDir.resolve(dir.getFileName() + "/" + file2.getFileName()),
				destinationDir.resolve(dir.getFileName() + "/" + dir2.getFileName()),
				destinationDir.resolve(dir.getFileName() + "/" + dir2.getFileName() + "/" + file3.getFileName()),
				destinationDir.resolve(dir.getFileName() + "/" + dir3.getFileName())
			)); //@formatter:on

			assertEquals(expectedPaths, actualPaths);

			assertFileContent(destinationDir.resolve(dir.getFileName() + "/" + file1.getFileName()), "data1");
			assertFileContent(destinationDir.resolve(dir.getFileName() + "/" + file2.getFileName()), "data2");
			assertFileContent(destinationDir.resolve(dir.getFileName() + "/" + dir2.getFileName() + "/" + file3.getFileName()), "data3");
		}
	}

	@Test
	public void repairCorruptedZipFile() throws Exception {
		Path root = temp.getRoot().toPath();
		Path zip = root.resolve("zip.zip");

		//create directory structure for testing
		//@formatter:off
		Path dir = mkdir(root, "zipMe");
			Path file1 = mkfile(dir, "file1.txt", "data1");
			Path file2 = mkfile(dir, "file2.txt", "data2");
			Path dir2 = mkdir(dir, "folder2");
				Path file3 = mkfile(dir2, "file3.txt", "data3");
			Path dir3 = mkdir(dir, "folder3");
		//@formatter:on

		oldZipMethod(dir.toFile(), zip.toFile());

		try (FileSystem zipFs = ZipUtils.openExistingZipFile(zip)) {
			Files.walk(zipFs.getPath("/")).collect(Collectors.toSet());
			fail("Files.walk() method was supposed to throw an exception.");
		} catch (UncheckedIOException expected) {
		}

		ZipUtils.repairCorruptedZipFile(zip);

		Set<Path> actualPaths;
		try (FileSystem zipFs = ZipUtils.openExistingZipFile(zip)) {
			actualPaths = Files.walk(zipFs.getPath("/")).collect(Collectors.toSet());

			assertFileContent(zipFs.getPath(dir.getFileName() + "/" + file1.getFileName()), "data1");
			assertFileContent(zipFs.getPath(dir.getFileName() + "/" + file2.getFileName()), "data2");
			assertFileContent(zipFs.getPath(dir.getFileName() + "/" + dir2.getFileName() + "/" + file3.getFileName()), "data3");
		}

		Set<String> expectedPaths = new HashSet<>(Arrays.asList( //@formatter:off
			"/",
			"/" + dir.getFileName() + "/",
			"/" + dir.getFileName() + "/" + file1.getFileName(),
			"/" + dir.getFileName() + "/" + file2.getFileName(),
			"/" + dir.getFileName() + "/" + dir2.getFileName() + "/",
			"/" + dir.getFileName() + "/" + dir2.getFileName() + "/" + file3.getFileName(),
			"/" + dir.getFileName() + "/" + dir3.getFileName() + "/"
		)); //@formatter:on

		assertEquals(expectedPaths, actualPaths.stream().map(Path::toString).collect(Collectors.toSet()));
	}

	/**
	 * This is the old method that used to be used to zip directories.
	 */
	private static void oldZipMethod(File directory, File zipFile) throws IOException {
		String rootPath = directory.getParent();
		LinkedList<File> folders = new LinkedList<>();
		folders.add(directory);

		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile))) {
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
					zip.putNextEntry(new ZipEntry(folderPath + "empty")); //empty dirs are not being added, so add an empty file to the folder
					continue;
				}

				//add files to zip
				for (File file : files) {
					if (file.isDirectory()) {
						folders.add(file);
						continue;
					}

					try (FileInputStream in = new FileInputStream(file)) {
						zip.putNextEntry(new ZipEntry(folderPath + file.getName()));
						IOUtils.copy(in, zip);
					}
				}
			}
		}
	}
}
