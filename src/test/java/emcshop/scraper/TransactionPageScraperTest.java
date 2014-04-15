package emcshop.scraper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

		List<RupeeTransaction> expected = new ArrayList<RupeeTransaction>();

		RawTransaction rt = new RawTransaction();
		rt.setTs(new Date(1354210000000L));
		rt.setDescription("Blah");
		rt.setAmount(500);
		rt.setBalance(212994);
		expected.add(rt);

		ShopTransaction st = new ShopTransaction();
		st.setTs(new Date(1354230649000L));
		st.setAmount(10);
		st.setBalance(213329);
		st.setItem("Leather");
		st.setPlayer("jtc0999");
		st.setQuantity(-1);
		expected.add(st);

		st = new ShopTransaction();
		st.setTs(new Date(1354227236000L));
		st.setAmount(30000);
		st.setBalance(213194);
		st.setItem("Brewing Stand");
		st.setPlayer("SebaB2001");
		st.setQuantity(-2);
		expected.add(st);

		PaymentTransaction pt = new PaymentTransaction();
		pt.setTs(new Date(1354226347000L));
		pt.setAmount(-100);
		pt.setBalance(212994);
		pt.setPlayer("WeirdManaico");
		expected.add(pt);

		pt = new PaymentTransaction();
		pt.setTs(new Date(1354226247000L));
		pt.setAmount(6);
		pt.setBalance(212990);
		pt.setPlayer("ColeWalser");
		expected.add(pt);

		st = new ShopTransaction();
		st.setTs(new Date(1354225000000L));
		st.setAmount(-8);
		st.setBalance(200);
		st.setItem("Blue Wool");
		st.setPlayer("longtimeshelf8");
		st.setQuantity(8);
		expected.add(st);

		BonusFeeTransaction bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(400);
		bft.setBalance(212990);
		bft.setSignInBonus(true);
		expected.add(bft);

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(100);
		bft.setBalance(212990);
		bft.setVoteBonus(true);
		expected.add(bft);

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(-100);
		bft.setBalance(212990);
		bft.setHorseFee(true);
		expected.add(bft);

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(-1000);
		bft.setBalance(212990);
		bft.setLockFee(true);
		expected.add(bft);

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(1000);
		bft.setBalance(212990);
		bft.setLockFee(true);
		expected.add(bft);

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(500);
		bft.setBalance(212990);
		bft.setLockFee(true);
		expected.add(bft);

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(-10);
		bft.setBalance(212990);
		bft.setVaultFee(true);
		expected.add(bft);

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(-100);
		bft.setBalance(212990);
		bft.setEggifyFee(true);
		expected.add(bft);

		bft = new BonusFeeTransaction();
		bft.setTs(new Date(1354226247000L));
		bft.setAmount(-50);
		bft.setBalance(212990);
		bft.setMailFee(true);
		expected.add(bft);

		DateFormat df = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm aa", Locale.US);
		rt = new RawTransaction();
		rt.setTs(df.parse("Nov 22, 2012 at 9:54 PM"));
		rt.setDescription("Week-old transaction");
		rt.setAmount(-100);
		rt.setBalance(212990);
		expected.add(rt);

		List<RupeeTransaction> actual = page.getTransactions();
		assertEquals(expected, actual);
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
