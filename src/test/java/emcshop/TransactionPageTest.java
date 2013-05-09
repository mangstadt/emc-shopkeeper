package emcshop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
public class TransactionPageTest {
	@Test
	public void transactionPage() throws Exception {
		InputStream in = ClasspathUtils.getResourceAsStream("transaction-page-sample.html", TransactionPageTest.class);
		Document d = Jsoup.parse(in, "UTF-8", "");
		TransactionPage page = new TransactionPage(d);

		assertTrue(page.isLoggedIn());
		assertEquals(new Date(1354210000000L), page.getFirstTransactionDate());

		{
			Iterator<ShopTransaction> it = page.getShopTransactions().iterator();

			ShopTransaction t = it.next();
			assertEquals(10, t.getAmount());
			assertEquals(213329, t.getBalance());
			assertEquals("Leather", t.getItem());
			assertEquals("jtc0999", t.getPlayer());
			assertEquals(-1, t.getQuantity());
			assertEquals(new Date(1354230649000L), t.getTs());

			t = it.next();
			assertEquals(30000, t.getAmount());
			assertEquals(213194, t.getBalance());
			assertEquals("Brewing Stand", t.getItem());
			assertEquals("SebaB2001", t.getPlayer());
			assertEquals(-2, t.getQuantity());
			assertEquals(new Date(1354227236000L), t.getTs());

			t = it.next();
			assertEquals(-8, t.getAmount());
			assertEquals(200, t.getBalance());
			assertEquals("Blue Wool", t.getItem());
			assertEquals("longtimeshelf8", t.getPlayer());
			assertEquals(8, t.getQuantity());
			assertEquals(new Date(1354225000000L), t.getTs());

			assertFalse(it.hasNext());
		}

		{
			Iterator<PaymentTransaction> it = page.getPaymentTransactions().iterator();

			PaymentTransaction t = it.next();
			assertEquals(-100, t.getAmount());
			assertEquals(212994, t.getBalance());
			assertEquals("WeirdManaico", t.getPlayer());
			assertEquals(new Date(1354226347000L), t.getTs());

			t = it.next();
			assertEquals(6, t.getAmount());
			assertEquals(212990, t.getBalance());
			assertEquals("ColeWalser", t.getPlayer());
			assertEquals(new Date(1354226247000L), t.getTs());

			assertFalse(it.hasNext());
		}
	}

	@Test
	public void loggedOutPage() throws Exception {
		InputStream in = ClasspathUtils.getResourceAsStream("transaction-page-not-logged-in.html", TransactionPageTest.class);
		Document d = Jsoup.parse(in, "UTF-8", "");
		TransactionPage page = new TransactionPage(d);

		assertFalse(page.isLoggedIn());
		assertNull(page.getFirstTransactionDate());
		assertTrue(page.getShopTransactions().isEmpty());
	}
}
