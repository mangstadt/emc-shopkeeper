package emcshop.scraper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.logging.LogManager;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.BeforeClass;
import org.junit.Test;

import emcshop.util.ClasspathUtils;

/**
 * @author Michael Angstadt
 */
public class TransactionPageScraperTest {
	@BeforeClass
	public static void beforeClass() {
		LogManager.getLogManager().reset();
	}

	@Test
	public void transactionPage() throws Exception {
		TransactionPage page = load("transaction-page-sample.html");

		assertEquals(new Date(1354210000000L), page.getFirstTransactionDate());
		assertEquals(Integer.valueOf(214308), page.getRupeeBalance());

		Iterator<RupeeTransaction> it = page.getTransactions().iterator();

		RawTransaction rt = new RawTransaction();
		rt.setTs(new Date(1354210000000L));
		rt.setDescription("Blah");
		rt.setAmount(500);
		rt.setBalance(212994);
		assertEquals(rt, it.next());

		ShopTransaction st = new ShopTransaction();
		st.setTs(new Date(1354230649000L));
		st.setAmount(10);
		st.setBalance(213329);
		st.setItem("Leather");
		st.setPlayer("jtc0999");
		st.setQuantity(-1);
		assertEquals(st, it.next());

		st = new ShopTransaction();
		st.setTs(new Date(1354227236000L));
		st.setAmount(30000);
		st.setBalance(213194);
		st.setItem("Brewing Stand");
		st.setPlayer("SebaB2001");
		st.setQuantity(-2);
		assertEquals(st, it.next());

		PaymentTransaction pt = new PaymentTransaction();
		pt.setTs(new Date(1354226347000L));
		pt.setAmount(-100);
		pt.setBalance(212994);
		pt.setPlayer("WeirdManaico");
		assertEquals(pt, it.next());

		pt = new PaymentTransaction();
		pt.setTs(new Date(1354226247000L));
		pt.setAmount(6);
		pt.setBalance(212990);
		pt.setPlayer("ColeWalser");
		assertEquals(pt, it.next());

		pt = new PaymentTransaction();
		pt.setTs(new Date(1354226347000L));
		pt.setAmount(-100);
		pt.setBalance(212994);
		pt.setPlayer("PenguinDJ");
		pt.setReason("Reason");
		assertEquals(pt, it.next());

		st = new ShopTransaction();
		st.setTs(new Date(1354225000000L));
		st.setAmount(-8);
		st.setBalance(200);
		st.setItem("Blue Wool");
		st.setPlayer("longtimeshelf8");
		st.setQuantity(8);
		assertEquals(st, it.next());

		BonusFeeTransaction bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(400);
		bft.setBalance(212990);
		bft.setSignInBonus(true);
		assertEquals(bft, it.next());

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(100);
		bft.setBalance(212990);
		bft.setVoteBonus(true);
		assertEquals(bft, it.next());

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(-100);
		bft.setBalance(212990);
		bft.setHorseFee(true);
		assertEquals(bft, it.next());

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(-1000);
		bft.setBalance(212990);
		bft.setLockFee(true);
		assertEquals(bft, it.next());

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(1000);
		bft.setBalance(212990);
		bft.setLockFee(true);
		assertEquals(bft, it.next());

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(500);
		bft.setBalance(212990);
		bft.setLockFee(true);
		assertEquals(bft, it.next());

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(-10);
		bft.setBalance(212990);
		bft.setVaultFee(true);
		assertEquals(bft, it.next());

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(-100);
		bft.setBalance(212990);
		bft.setEggifyFee(true);
		assertEquals(bft, it.next());

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(-50);
		bft.setBalance(212990);
		bft.setMailFee(true);
		assertEquals(bft, it.next());

		st = new ShopTransaction();
		st.setTs(new Date(1401375916000L));
		st.setAmount(4);
		st.setBalance(212990);
		st.setItem("Bread");
		st.setShopOwner("wassatthen");
		st.setQuantity(-8);
		assertEquals(st, it.next());

		st = new ShopTransaction();
		st.setTs(new Date(1401375913000L));
		st.setAmount(-8);
		st.setBalance(212990);
		st.setItem("Bread");
		st.setShopOwner("wassatthen");
		st.setQuantity(8);
		assertEquals(st, it.next());

		DateFormat df = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm aa", Locale.US);
		rt = new RawTransaction();
		rt.setTs(df.parse("Nov 22, 2012 at 9:54 PM"));
		rt.setDescription("Week-old transaction");
		rt.setAmount(-100);
		rt.setBalance(212990);
		assertEquals(rt, it.next());

		assertFalse(it.hasNext());
	}

	@Test
	public void loggedOutPage() throws Exception {
		TransactionPage page = load("transaction-page-not-logged-in.html");
		assertNull(page);
	}

	@Test
	public void invalidRupeeBalance() throws Exception {
		TransactionPage page = load("transaction-page-invalid-rupee-balance.html");

		assertNull(page.getFirstTransactionDate());
		assertNull(page.getRupeeBalance());
		assertTrue(page.getTransactions().isEmpty());
	}

	@Test
	public void missingRupeeBalance() throws Exception {
		TransactionPage page = load("transaction-page-missing-rupee-balance.html");

		assertNull(page.getFirstTransactionDate());
		assertNull(page.getRupeeBalance());
		assertTrue(page.getTransactions().isEmpty());
	}

	private TransactionPage load(String file) throws IOException {
		InputStream in = ClasspathUtils.getResourceAsStream(file, TransactionPageScraperTest.class);
		Document document = Jsoup.parse(in, "UTF-8", "");
		return new TransactionPageScraper().scrape(document);
	}
}
