package emcshop;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import emcshop.util.ClasspathUtils;

/**
 * @author Michael Angstadt
 */
public class PageScraperTest {
	@Test
	public void scrape() throws Exception {
		InputStream in = ClasspathUtils.getResourceAsStream("transaction-page-sample.html", PageScraperTest.class);
		Document d = Jsoup.parse(in, "UTF-8", "");
		Iterator<Transaction> it = PageScraper.scrape(d).iterator();

		Transaction t = it.next();
		assertEquals(10, t.getAmount());
		assertEquals(213329, t.getBalance());
		assertEquals("Leather", t.getItem());
		assertEquals("jtc0999", t.getPlayer());
		assertEquals(-1, t.getQuantity());
		assertEquals(new Date(1354230649000L), t.getTs());

		//assertFalse(it.hasNext());

	}
}
