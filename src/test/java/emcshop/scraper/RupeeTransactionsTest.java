package emcshop.scraper;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class RupeeTransactionsTest {
	@Test
	public void find() {
		ShopTransaction t1 = new ShopTransaction();
		PaymentTransaction t2 = new PaymentTransaction();
		PaymentTransaction t3 = new PaymentTransaction();
		RupeeTransactions list = new RupeeTransactions(Arrays.asList(t1, t2, t3));

		{
			List<ShopTransaction> actual = list.find(ShopTransaction.class);
			List<ShopTransaction> expected = Arrays.asList(t1);
			assertEquals(expected, actual);
		}

		{
			List<PaymentTransaction> actual = list.find(PaymentTransaction.class);
			List<PaymentTransaction> expected = Arrays.asList(t2, t3);
			assertEquals(expected, actual);
		}

		{
			List<RawTransaction> actual = list.find(RawTransaction.class);
			List<RawTransaction> expected = Arrays.asList();
			assertEquals(expected, actual);
		}
	}
}
