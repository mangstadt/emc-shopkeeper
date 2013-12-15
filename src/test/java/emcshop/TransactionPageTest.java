package emcshop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
		assertEquals(Integer.valueOf(214308), page.getRupeeBalance());

		List<RupeeTransaction> expected = new ArrayList<RupeeTransaction>();

		RawTransaction t = new RawTransaction();
		t.setTs(new Date(1354210000000L));
		t.setDescription("Blah");
		t.setAmount(500);
		t.setBalance(212994);
		expected.add(t);

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

		List<RupeeTransaction> actual = page.getTransactions();
		assertEquals(expected, actual);
	}

	@Test
	public void loggedOutPage() throws Exception {
		InputStream in = ClasspathUtils.getResourceAsStream("transaction-page-not-logged-in.html", TransactionPageTest.class);
		Document d = Jsoup.parse(in, "UTF-8", "");
		TransactionPage page = new TransactionPage(d);

		assertFalse(page.isLoggedIn());
		assertNull(page.getFirstTransactionDate());
		assertNull(page.getRupeeBalance());
		assertTrue(page.getTransactions().isEmpty());
	}
}
