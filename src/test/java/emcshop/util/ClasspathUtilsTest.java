package emcshop.util;

import static org.junit.Assert.assertNotNull;

import java.io.FileNotFoundException;

import org.junit.Test;

import emcshop.db.DbDao;

/**
 * @author Michael Angstadt
 */
public class ClasspathUtilsTest {
	@Test
	public void getResourceAsStream_relative_to_ClasspathUtils() throws Exception {
		assertNotNull(ClasspathUtils.getResourceAsStream(Settings.class.getSimpleName() + ".class"));
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
}
