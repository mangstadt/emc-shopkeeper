package emcshop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.List;

import org.junit.Test;

import emcshop.db.DbDao;

/**
 * @author Michael Angstadt
 */
public class ClasspathUtilsTest {
	@Test
	public void getResourceAsStream_relative_to_ClasspathUtils() throws Exception {
		assertNotNull(ClasspathUtils.getResourceAsStream(TestUtils.class.getSimpleName() + ".class"));
	}

	@Test(expected = FileNotFoundException.class)
	public void getResourceAsStream_non_existent_path() throws Exception {
		ClasspathUtils.getResourceAsStream("does-not-exist");
	}

	@Test
	public void getResourceAsStream_with_relative_class() throws Exception {
		assertNotNull(ClasspathUtils.getResourceAsStream("schema.sql", DbDao.class));
	}

	@Test(expected = FileNotFoundException.class)
	public void getResourceAsStream_non_existent_path_with_relative_class() throws Exception {
		ClasspathUtils.getResourceAsStream("does-not-exist", DbDao.class);
	}

	@Test
	public void getResourceAsStream_absolute_path() throws Exception {
		assertNotNull(ClasspathUtils.getResourceAsStream("/emcshop/db/schema.sql"));
	}

	@Test(expected = FileNotFoundException.class)
	public void getResourceAsStream_non_existent_absolute_path() throws Exception {
		ClasspathUtils.getResourceAsStream("/sleet/db/does-not-exist");
	}

	@Test
	public void listFilesInPackage() throws Exception {
		List<URI> actual = ClasspathUtils.listFilesInPackage("emcshop.util.cp");
		assertEquals(2, actual.size());
		assertTrue(actual.toString(), actual.contains(new File("target/test-classes/emcshop/util/cp/test1").toURI()));
		assertTrue(actual.toString(), actual.contains(new File("target/test-classes/emcshop/util/cp/test2").toURI()));
	}

	@Test
	public void listFilesInPackageFromJar() throws Exception {
		File jarFile = new File("src/test/resources/emcshop/util/test.jar");
		List<URI> actual = ClasspathUtils.listFilesInPackageFromJar(jarFile, "emcshop.util.cp");
		assertEquals(1, actual.size());

		URI jarFileUri = jarFile.toURI();
		assertTrue(actual.toString(), actual.contains(URI.create("jar:" + jarFileUri.getPath() + "!/emcshop/util/cp/test3")));
	}
}
