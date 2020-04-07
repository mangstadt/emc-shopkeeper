package emcshop.chat;

import static emcshop.util.TestUtils.date;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * @author Michael Angstadt
 */
public class ChatLogFileReaderTest {
	@Test
	public void test() throws Exception {
		//this log file should be edited in Notepad to preserve the character encoding
		List<String> fileNames = Arrays.asList("ChatLogFileReaderTest.log", "ChatLogFileReaderTest.log.gz");
		for (String fileName : fileNames) {
			Path path = Paths.get(getClass().getResource(fileName).toURI());

			try (ChatLogFileReader reader = new ChatLogFileReader(path, LocalDate.of(2020, 4, 1))) {
				ChatMessage message = reader.readNext();
				assertEquals("", message.getMessage());
				assertEquals(date("2020-04-01 14:49:26"), message.getDate());

				message = reader.readNext();
				assertEquals("Welcome to Empire Minecraft - SMP5, shavingfoam!", message.getMessage());
				assertEquals(date("2020-04-01 14:49:26"), message.getDate());

				message = reader.readNext();
				assertEquals("Next rupee bonus: 9 hrs, 11 mins, Vote for over 1k more!", message.getMessage());
				assertEquals(date("2020-04-01 14:49:26"), message.getDate());

				message = reader.readNext();
				assertEquals("» Welcome to shavingfoam's shoppe 2.0 /v 11372", message.getMessage());
				assertEquals(date("2020-04-01 14:49:26"), message.getDate());

				message = reader.readNext();
				assertEquals("M-5 • Ides_Of_March: Is anyone selling tridents?", message.getMessage());
				assertEquals(date("2020-04-01 14:50:53"), message.getDate());

				assertNull(reader.readNext());
			}
		}
	}
}
