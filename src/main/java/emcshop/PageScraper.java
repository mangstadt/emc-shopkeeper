package emcshop;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Screen-scrapes the shop transactions from a rupee transaction page.
 * @author Michael Angstadt
 */
public class PageScraper {
	private static final Logger logger = Logger.getLogger(PageScraper.class.getName());
	private static final Pattern soldRegex = Pattern.compile("^Player shop sold (\\d+) (.*?) to (.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern boughtRegex = Pattern.compile("^Your player shop bought (\\d+) (.*?) from (.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern amountRegex = Pattern.compile("^(-|\\+)\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);

	private final DateFormat df = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm aa");

	/**
	 * Scrapes a transaction page.
	 * @param document the transaction page
	 * @return the transactions
	 */
	public static List<Transaction> scrape(Document document) {
		return new PageScraper().scrapeDocument(document);
	}

	private List<Transaction> scrapeDocument(Document document) {
		List<Transaction> transactions = new ArrayList<Transaction>();

		Elements elements = document.select("li.sectionItem");
		if (elements.isEmpty()) {
			throw new IllegalArgumentException("No rupee transactions found.  Check your cookies.");
		}
		for (Element element : elements) {
			Transaction transaction = scrapeElement(element);
			if (transaction != null) {
				transactions.add(transaction);
			}
		}

		return transactions;
	}

	private Transaction scrapeElement(Element element) {
		Transaction transaction = new Transaction();

		Element descriptionElement = element.select("div.description").first();
		if (descriptionElement != null) {
			String description = descriptionElement.text();
			Matcher m = soldRegex.matcher(description);
			if (m.find()) {
				transaction.setQuantity(-Integer.parseInt(m.group(1)));
				transaction.setItem(m.group(2));
				transaction.setPlayer(m.group(3));
			} else {
				m = boughtRegex.matcher(description);
				if (m.find()) {
					transaction.setQuantity(Integer.parseInt(m.group(1)));
					transaction.setItem(m.group(2));
					transaction.setPlayer(m.group(3));
				} else {
					//not a shop transaction
					return null;
				}
			}
		}

		Element tsElement = element.select("div.time abbr[data-time]").first();
		if (tsElement != null) {
			String dataTime = tsElement.attr("data-time");
			try {
				long ts = Long.parseLong(dataTime) * 1000;
				transaction.setTs(new Date(ts));
			} catch (NumberFormatException e) {
				logger.warning("Transaction time could not be parsed: " + dataTime);
			}
		} else {
			tsElement = element.select("div.time span[title]").first();
			String tsText = tsElement.attr("title");
			try {
				Date ts = df.parse(tsText);
				transaction.setTs(ts);
			} catch (ParseException e) {
				logger.warning("Transaction time could not be parsed: " + tsText);
			}
		}

		Element amountElement = element.select("div.amount").first();
		if (amountElement != null) {
			String amountText = amountElement.text();
			Matcher m = amountRegex.matcher(amountText);
			if (m.find()) {
				int amount = Integer.parseInt(m.group(2));
				if ("-".equals(m.group(1))) {
					amount *= -1;
				}
				transaction.setAmount(amount);
			}
		}

		Element balanceElement = element.select("div.balance").first();
		if (balanceElement != null) {
			String balanceText = balanceElement.text().replace(",", "");
			try {
				int balance = Integer.parseInt(balanceText);
				transaction.setBalance(balance);
			} catch (NumberFormatException e) {
				logger.warning("Transaction balance could not be parsed: " + balanceText);
			}
		}

		return transaction;
	}

	private PageScraper() {
		//hide constructor	
	}
}
