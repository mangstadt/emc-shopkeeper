package emcshop.scraper;

import org.junit.Before;
import org.junit.Test;

public class TransactionPullerFactoryTest {
	private TransactionPullerFactory factory;

	@Before
	public void before() {
		factory = new TransactionPullerFactory();
	}

	@Test(expected = IllegalArgumentException.class)
	public void setMaxPaymentTransactionAge() {
		factory.setMaxPaymentTransactionAge(0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setStartAtPage() {
		factory.setStartAtPage(0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setStopAtPage() {
		factory.setStopAtPage(0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setThreadCount() {
		factory.setThreadCount(0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void create_stopAtPage_less_than_startAtPage() throws Throwable {
		factory.setStartAtPage(5);
		factory.setStopAtPage(1);
		factory.create(null);
	}
}
