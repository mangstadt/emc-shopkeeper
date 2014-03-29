package emcshop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import emcshop.util.ZipUtils.ZipListener;

public class ZipUtilsTest {
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void getDirectorySize() throws Throwable {
		File root = temp.getRoot();

		File file = new File(root, "file1.txt");
		FileUtils.write(file, "a");

		file = new File(root, "file2.txt");
		FileUtils.write(file, "ab");

		File dir = new File(root, "dir");
		dir.mkdir();

		file = new File(dir, "file3.txt");
		FileUtils.write(file, "abc");

		assertEquals(6, ZipUtils.getDirectorySize(root));
	}

	@Test
	public void zipAndUnzipDirectory() throws Throwable {
		File root = temp.getRoot();
		File zip = new File(root, "zip.zip");

		{
			File dir = new File(root, "zipMe");
			dir.mkdir();

			File file1 = new File(dir, "file1.txt");
			FileUtils.write(file1, "data1");

			File file2 = new File(dir, "file2.txt");
			FileUtils.write(file2, "data2");

			File dir2 = new File(dir, "folder2");
			dir2.mkdir();

			File file3 = new File(dir2, "file3.txt");
			FileUtils.write(file3, "data3");

			File dir3 = new File(dir, "folder3");
			dir3.mkdir();

			ZipListener listener = mock(ZipListener.class);
			ZipUtils.zipDirectory(dir, zip, listener);

			verify(listener).onZippedFile(file1);
			verify(listener).onZippedFile(file2);
			verify(listener).onZippedFile(file3);

			Set<String> actualPaths = new HashSet<String>();
			ZipFile zipFile = new ZipFile(zip);
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				actualPaths.add(entry.getName());
			}
			zipFile.close();

			Set<String> expectedPaths = new HashSet<String>();
			expectedPaths.add("/" + dir.getName() + "/" + file1.getName());
			expectedPaths.add("/" + dir.getName() + "/" + file2.getName());
			expectedPaths.add("/" + dir.getName() + "/" + dir2.getName() + "/" + file3.getName());
			expectedPaths.add("/" + dir.getName() + "/" + dir3.getName() + "/empty");

			assertEquals(expectedPaths, actualPaths);
		}

		{
			File destinationDir = new File(root, "destination");

			ZipUtils.unzip(destinationDir, zip);

			assertEquals("data1", FileUtils.readFileToString(new File(destinationDir, "zipMe/file1.txt")));
			assertEquals("data2", FileUtils.readFileToString(new File(destinationDir, "zipMe/file2.txt")));
			assertEquals("data3", FileUtils.readFileToString(new File(destinationDir, "zipMe/folder2/file3.txt")));
			assertTrue(new File(destinationDir, "zipMe/folder3").isDirectory());
			assertFalse(new File(destinationDir, "zipMe/folder3/empty").exists());
		}

	}
}
