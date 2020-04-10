package emcshop.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.intThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class TestUtils {
	private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	public static Date date(String date) {
		if (date.length() == 10) {
			date += " 00:00:00";
		}
		try {
			return df.parse(date);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	public static void assertIntEquals(Integer expected, int actual) {
		assertNotNull(expected);
		assertEquals(expected.intValue(), actual);
	}

	public static void assertIntEquals(int expected, Integer actual) {
		assertNotNull("Integer object is null", actual);
		assertEquals(Integer.valueOf(expected), actual);
	}

	/**
	 * Converts a {@link LocalDate} to a {@link Timestamp}.
	 * @param date the date
	 * @return the timestamp
	 */
	public static Timestamp timestamp(LocalDate date) {
		return (date == null) ? null : Timestamp.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	/**
	 * Converts a {@link LocalDateTime} to a {@link Timestamp}.
	 * @param date the date
	 * @return the timestamp
	 */
	public static Timestamp timestamp(LocalDateTime date) {
		return (date == null) ? null : Timestamp.from(date.atZone(ZoneId.systemDefault()).toInstant());
	}

	/**
	 * Used in Mockito expressions to perform an operation if an "int" argument
	 * is greater than or equal to the given value.
	 * @param value the value
	 * @return the value to pass to Mockito
	 */
	public static int gte(final int value) {
		return intThat(new BaseMatcher<Integer>() {
			@Override
			public void describeTo(Description description) {
				//empty
			}

			@Override
			public boolean matches(Object obj) {
				Integer i = (Integer) obj;
				return i >= value;
			}
		});
	}

	/**
	 * Creates an empty file.
	 * @param parent the parent folder
	 * @param name the file name
	 * @return the file
	 * @throws IOException if there's a problem creating the file
	 */
	public static Path mkfile(Path parent, String name) throws IOException {
		return mkfile(parent, name, "");
	}

	/**
	 * Creates a file.
	 * @param parent the parent folder
	 * @param name the file name
	 * @param content the file content
	 * @return the file
	 * @throws IOException if there's a problem creating the file
	 */
	public static Path mkfile(Path parent, String name, String content) throws IOException {
		Path file = parent.resolve(name);
		Files.write(file, content.getBytes());
		return file;
	}

	/**
	 * Creates a directory.
	 * @param parent the parent directory
	 * @param name the directory name
	 * @return the directory
	 * @throws IOException if there's a problem creating the directory
	 */
	public static Path mkdir(Path parent, String name) throws IOException {
		Path folder = parent.resolve(name);
		Files.createDirectory(folder);
		return folder;
	}

	/**
	 * Asserts the contents of a text file.
	 * @param path the file
	 * @param expectedContent the expected content
	 * @throws IOException if there's a problem reading the file
	 */
	public static void assertFileContent(Path path, String expectedContent) throws IOException {
		String actualContent = new String(Files.readAllBytes(path));
		assertEquals(expectedContent, actualContent);
	}
}
