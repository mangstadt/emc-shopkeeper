package emcshop;

import static emcshop.util.TestUtils.mkfile;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/**
 * @author Michael Angstadt
 */
public class LogManagerTest {
	@Rule
	public final TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void getCombinedLogFiles() throws Exception {
		final String NL = System.lineSeparator();
		Path root = temp.getRoot().toPath();

		long ts = System.currentTimeMillis();
		Path file1 = mkfile(root, "err.log.1", "log1\nlog1");
		Files.setLastModifiedTime(file1, FileTime.fromMillis(ts));

		ts -= 60000;
		Path file0 = mkfile(root, "err.log.0", "log0\nlog0");
		Files.setLastModifiedTime(file0, FileTime.fromMillis(ts));

		ts -= 60000;
		Path file3 = mkfile(root, "err.log.3", "log3\nlog3");
		Files.setLastModifiedTime(file3, FileTime.fromMillis(ts));

		ts -= 60000;
		Path file2 = mkfile(root, "err.log.2", "log2\nlog2");
		Files.setLastModifiedTime(file2, FileTime.fromMillis(ts));

		mkfile(root, "err.log", "no number"); //should be ignored
		mkfile(root, "err.log.1.lck", "lock"); //should be ignored

		LogManager manager = Mockito.spy(new LogManager(root.resolve("err.log")));
		doThrow(new IOException("exception message")).when(manager).readAllBytes(file3);

		String actual = manager.getCombinedLogFiles();
		String expected = //@formatter:off
			"log2\nlog2" + NL +
			"========================================================" + NL +
			"========================================================" + NL +
			"ERROR: Could not open log file: " + file3 + NL +
			"exception message" + NL +
			"========================================================" + NL +
			"========================================================" + NL +
			"log0\nlog0" + NL +
			"log1\nlog1"; //@formatter:on

		assertEquals(expected, actual);
	}
}
