package emcshop.scraper;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import emcshop.ItemIndex;

/**
 * Downloads and scrapes rupee transaction history pages.
 */
public class TransactionPageScraper {
	private static final Logger logger = Logger.getLogger(TransactionPageScraper.class.getName());

	private static final Pattern balanceRegex = Pattern.compile("^Your balance: ([\\d,]+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern amountRegex = Pattern.compile("^(-|\\+)\\s*([\\d,]+)$", Pattern.CASE_INSENSITIVE);

	private static final Pattern soldRegex = Pattern.compile("^Player shop sold ([\\d,]+) (.*?) to (.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern boughtRegex = Pattern.compile("^Your player shop bought ([\\d,]+) (.*?) from (.*)$", Pattern.CASE_INSENSITIVE);

	private static final Pattern paymentFromRegex = Pattern.compile("^Payment from (.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern paymentToRegex = Pattern.compile("^Payment to (.*)$", Pattern.CASE_INSENSITIVE);

	private static final Pattern horseFeeRegex = Pattern.compile("^Summoned stabled horse in the wild.*", Pattern.CASE_INSENSITIVE);
	private static final Pattern lockFeeRegex = Pattern.compile("^(Locked an item wilderness|(Full|Partial) refund for unlocking item wilderness).*", Pattern.CASE_INSENSITIVE);
	private static final Pattern eggifyFeeRegex = Pattern.compile("^Eggified a .*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern vaultFeeRegex = Pattern.compile("^Opened cross-server vault$", Pattern.CASE_INSENSITIVE);
	private static final Pattern signInBonusRegex = Pattern.compile("^Daily sign-in bonus$", Pattern.CASE_INSENSITIVE);
	private static final Pattern voteBonusRegex = Pattern.compile("^Voted for Empire Minecraft on .*$", Pattern.CASE_INSENSITIVE);

	private static final ItemIndex itemIndex = ItemIndex.instance();

	/**
	 * Downloads and parses a rupee transaction page.
	 * @param page the page number
	 * @return the page or null if the session is bad
	 * @throws IOException if there's a problem downloading the page
	 */
	public TransactionPage download(int page) throws IOException {
		return download(page, new DefaultHttpClient());
	}

	/**
	 * Downloads and parses a rupee transaction page.
	 * @param page the page number
	 * @param client the HTTP client
	 * @return the page or null if the session is bad
	 * @throws IOException if there's a problem downloading the page
	 */
	public TransactionPage download(int page, HttpClient client) throws IOException {
		/*
		 * Note: The HttpClient library is used here because using
		 * "Jsoup.connect()" doesn't always work when the application is run as
		 * a Web Start app.
		 * 
		 * The login dialog was repeatedly appearing because, even though the
		 * login was successful (a valid session cookie was generated), the
		 * TransactionPuller would fail when it tried to get the first
		 * transaction from the first page (i.e. when calling "isLoggedIn()").
		 * It was failing because it was getting back the unauthenticated
		 * version of the rupee page. It was as if jsoup wasn't sending the
		 * session cookie with the request.
		 * 
		 * The issue appeared to only occur when running under Web Start. It
		 * could not be reproduced when running via Eclipse.
		 */

		String base = "http://empireminecraft.com/rupees/transactions/";
		String url = base + "?page=" + page;

		HttpGet request = new HttpGet(url);
		HttpResponse response = client.execute(request);
		HttpEntity entity = response.getEntity();
		try {
			Document document = Jsoup.parse(entity.getContent(), "UTF-8", base);
			return scrape(document);
		} finally {
			EntityUtils.consume(entity);
		}
	}

	/**
	 * Scrapes a transaction page.
	 * @param document the transaction page
	 * @return the scraped data or null if the session is bad
	 */
	public TransactionPage scrape(Document document) {
		Element containerElement = document.select("ol.sectionItems").first();
		if (containerElement == null) {
			return null;
		}

		TransactionPage page = new TransactionPage();
		page.setRupeeBalance(parseRupeeBalance(document));

		Elements elements = containerElement.select("li.sectionItem");
		List<RupeeTransaction> transactions = new ArrayList<RupeeTransaction>();
		for (Element element : elements) {
			try {
				String description = parseDescription(element);
				RupeeTransaction transaction = parseTransaction(description);

				transaction.setTs(parseTs(element));
				transaction.setAmount(parseAmount(element));
				transaction.setBalance(parseBalance(element));
				transactions.add(transaction);
			} catch (Throwable t) {
				//skip if any of the fields cannot be properly parsed
				logger.log(Level.WARNING, "Problem parsing transaction from webpage.", t);
			}
		}
		page.setTransactions(transactions);

		return page;
	}

	private RupeeTransaction parseTransaction(String description) {
		RupeeTransaction transaction = toShopTransaction(description);
		if (transaction != null) {
			return transaction;
		}

		transaction = toPaymentTransaction(description);
		if (transaction != null) {
			return transaction;
		}

		transaction = toBonusOrFeeTransaction(description);
		if (transaction != null) {
			return transaction;
		}

		return toRawTransaction(description);
	}

	private ShopTransaction toShopTransaction(String description) {
		int negate = -1;
		Matcher m = soldRegex.matcher(description);
		if (!m.find()) {
			negate = 1;
			m = boughtRegex.matcher(description);
			if (!m.find()) {
				//not a shop transaction
				return null;
			}
		}

		ShopTransaction transaction = new ShopTransaction();
		transaction.setQuantity(parseNumber(m.group(1)) * negate);
		transaction.setItem(itemIndex.getDisplayName(m.group(2)));
		transaction.setPlayer(m.group(3));
		return transaction;
	}

	private PaymentTransaction toPaymentTransaction(String description) {
		Matcher m = paymentFromRegex.matcher(description);
		if (!m.find()) {
			m = paymentToRegex.matcher(description);
			if (!m.find()) {
				//not a payment transaction
				return null;
			}
		}

		PaymentTransaction transaction = new PaymentTransaction();
		transaction.setPlayer(m.group(1));
		return transaction;
	}

	private BonusFeeTransaction toBonusOrFeeTransaction(String description) {
		Matcher m = signInBonusRegex.matcher(description);
		if (m.find()) {
			BonusFeeTransaction t = new BonusFeeTransaction();
			t.setSignInBonus(true);
			return t;
		}

		m = voteBonusRegex.matcher(description);
		if (m.find()) {
			BonusFeeTransaction t = new BonusFeeTransaction();
			t.setVoteBonus(true);
			return t;
		}

		m = lockFeeRegex.matcher(description);
		if (m.find()) {
			BonusFeeTransaction t = new BonusFeeTransaction();
			t.setLockFee(true);
			return t;
		}

		m = vaultFeeRegex.matcher(description);
		if (m.find()) {
			BonusFeeTransaction t = new BonusFeeTransaction();
			t.setVaultFee(true);
			return t;
		}

		m = horseFeeRegex.matcher(description);
		if (m.find()) {
			BonusFeeTransaction t = new BonusFeeTransaction();
			t.setHorseFee(true);
			return t;
		}

		m = eggifyFeeRegex.matcher(description);
		if (m.find()) {
			BonusFeeTransaction t = new BonusFeeTransaction();
			t.setEggifyFee(true);
			return t;
		}

		return null;
	}

	private RawTransaction toRawTransaction(String description) {
		RawTransaction t = new RawTransaction();
		t.setDescription(description);
		return t;
	}

	private Integer parseRupeeBalance(Document document) {
		Element element = document.getElementById("rupeesBalance");
		if (element == null) {
			return null;
		}

		Matcher m = balanceRegex.matcher(element.text());
		if (!m.find()) {
			return null;
		}

		return parseNumber(m.group(1));
	}

	private String parseDescription(Element transactionElement) {
		Element descriptionElement = transactionElement.select("div.description").first(); //do not check for null (exception is caught in the constructor)
		return descriptionElement.text();
	}

	private Date parseTs(Element transactionElement) throws ParseException {
		Element tsElement = transactionElement.select("div.time abbr[data-time]").first();
		if (tsElement == null) {
			tsElement = transactionElement.select("div.time span[title]").first();
			String tsText = tsElement.attr("title");
			DateFormat df = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm aa", Locale.US);
			return df.parse(tsText);
		}

		String dataTime = tsElement.attr("data-time");
		long ts = Long.parseLong(dataTime) * 1000; //NumberFormatException is caught elsewhere
		return new Date(ts);
	}

	private int parseAmount(Element element) {
		Element amountElement = element.select("div.amount").first();
		String amountText = amountElement.text();

		Matcher m = amountRegex.matcher(amountText);
		m.find();

		int amount = parseNumber(m.group(2));
		if ("-".equals(m.group(1))) {
			amount *= -1;
		}
		return amount;
	}

	private int parseBalance(Element element) {
		Element balanceElement = element.select("div.balance").first();
		String balanceText = balanceElement.text();
		return parseNumber(balanceText);
	}

	private int parseNumber(String value) {
		value = value.replace(",", "");
		return Integer.parseInt(value); //NumberFormatException is caught elsewhere
	}
}
