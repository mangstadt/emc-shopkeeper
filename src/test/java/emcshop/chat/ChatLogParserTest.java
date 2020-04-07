package emcshop.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author Michael Angstadt
 */
public class ChatLogParserTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test(expected = IllegalArgumentException.class)
	public void not_a_directory() throws Exception {
		File file = folder.newFile();
		new ChatLogParser(file);
	}

	@Test
	public void getLog_no_logs_found() throws Exception {
		ChatLogParser parser = new ChatLogParser(folder.getRoot());
		List<ChatMessage> messages = parser.getLog(LocalDate.of(2020, 3, 1));

		assertTrue(messages.isEmpty());
	}

	@Test
	public void getLog() throws Exception {
		gzip("2020-03-01-1.log.gz", "[10:11:12] [Render thread/INFO]: [CHAT] March log file"); //should be ignored
		gzip("2020-04-01-1.log.gz", "[01:11:12] [Render thread/INFO]: [CHAT] Log file 1");
		gzip("2020-04-01-2.log.gz", "[02:11:12] [Render thread/INFO]: [CHAT] Log file 2");
		folder.newFolder("folder");
		gzip("folder\\2020-04-01-3.log.gz", "[03:11:12] [Render thread/INFO]: [CHAT] Log file 3"); //should be ignored
		gzip("2020-04-01-10.log.gz", "[10:11:12] [Render thread/INFO]: [CHAT] Log file 10");
		Path latest = file("latest.log", "[20:11:12] [Render thread/INFO]: [CHAT] Log file latest");

		//without latest.log
		{
			ChatLogParser parser = new ChatLogParser(folder.getRoot());
			Iterator<ChatMessage> messages = parser.getLog(LocalDate.of(2020, 4, 1)).iterator();

			ChatMessage message = messages.next();
			assertEquals("Log file 1", message.getMessage());
			assertEquals(LocalDateTime.of(2020, 4, 1, 1, 11, 12), message.getDate());

			message = messages.next();
			assertEquals("Log file 2", message.getMessage());
			assertEquals(LocalDateTime.of(2020, 4, 1, 2, 11, 12), message.getDate());

			message = messages.next();
			assertEquals("Log file 10", message.getMessage());
			assertEquals(LocalDateTime.of(2020, 4, 1, 10, 11, 12), message.getDate());

			assertFalse(messages.hasNext());
		}

		//with latest.log
		{
			//set last modified date of "latest.log" to 4/1/2020
			Files.setLastModifiedTime(latest, filetime(LocalDateTime.of(2020, 4, 1, 12, 0, 0)));

			ChatLogParser parser = new ChatLogParser(folder.getRoot());
			Iterator<ChatMessage> messages = parser.getLog(LocalDate.of(2020, 4, 1)).iterator();

			ChatMessage message = messages.next();
			assertEquals("Log file 1", message.getMessage());
			assertEquals(LocalDateTime.of(2020, 4, 1, 1, 11, 12), message.getDate());

			message = messages.next();
			assertEquals("Log file 2", message.getMessage());
			assertEquals(LocalDateTime.of(2020, 4, 1, 2, 11, 12), message.getDate());

			message = messages.next();
			assertEquals("Log file 10", message.getMessage());
			assertEquals(LocalDateTime.of(2020, 4, 1, 10, 11, 12), message.getDate());

			message = messages.next();
			assertEquals("Log file latest", message.getMessage());
			assertEquals(LocalDateTime.of(2020, 4, 1, 20, 11, 12), message.getDate());

			assertFalse(messages.hasNext());
		}
	}

	private Path file(String name, String content) throws IOException {
		return file(name, content, false);
	}

	private Path gzip(String name, String content) throws IOException {
		return file(name, content, true);
	}

	private Path file(String name, String content, boolean gzip) throws IOException {
		Path file = folder.newFile(name).toPath();

		OutputStream out = Files.newOutputStream(file);
		if (gzip) {
			out = new GZIPOutputStream(out);
		}
		out.write(content.getBytes());
		out.close();

		return file;
	}

	private FileTime filetime(LocalDateTime ts) {
		return FileTime.from(ts.atZone(ZoneId.systemDefault()).toInstant());
	}
}
