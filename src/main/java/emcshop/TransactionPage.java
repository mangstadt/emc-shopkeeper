package emcshop;

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

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Screen-scrapes the transactions from a rupee transaction history page.
 * @author Michael Angstadt
 */
public class TransactionPage {
	private static final Logger logger = Logger.getLogger(TransactionPage.class.getName());
	private static final Pattern soldRegex = Pattern.compile("^Player shop sold ([\\d,]+) (.*?) to (.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern boughtRegex = Pattern.compile("^Your player shop bought ([\\d,]+) (.*?) from (.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern amountRegex = Pattern.compile("^(-|\\+)\\s*([\\d,]+)$", Pattern.CASE_INSENSITIVE);

	private static final Pattern paymentFromRegex = Pattern.compile("^Payment from (.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern paymentToRegex = Pattern.compile("^Payment to (.*)$", Pattern.CASE_INSENSITIVE);

	private static final Pattern balanceRegex = Pattern.compile("^Your balance: ([\\d,]+)$", Pattern.CASE_INSENSITIVE);

	private static final RenamedItems renamedItems = RenamedItems.instance();

	private final DateFormat df = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm aa", Locale.US);
	private final Document document;

	/**
	 * @param document the webpage
	 */
	public TransactionPage(Document document) {
		this.document = document;
	}

	/**
	 * Gets the DOM of the transaction page.
	 * @return the HTML DOM
	 */
	public Document getDocument() {
		return document;
	}

	/**
	 * Determines whether the user has been logged in.
	 * @return true if the user has been logged in, false if not
	 */
	public boolean isLoggedIn() {
		Elements elements = document.select("li.sectionItem");
		return !elements.isEmpty();
	}

	/**
	 * Gets the date of the very first transaction on the page (may or may not
	 * be a shop transaction).
	 * @return the date or null if no transactions were found
	 */
	public Date getFirstTransactionDate() {
		Element element = document.select("li.sectionItem").first();
		if (element == null) {
			return null;
		}
		return parseTs(element);
	}

	/**
	 * Gets the player's total rupee balance.
	 * @return the total rupee balance or null if not found
	 */
	public Integer getRupeeBalance() {
		Element element = document.getElementById("rupeesBalance");
		if (element == null) {
			return null;
		}

		Matcher m = balanceRegex.matcher(element.text());
		if (!m.find()) {
			return null;
		}

		return Integer.valueOf(m.group(1).replace(",", ""));
	}

	/**
	 * Gets the payment transactions from the page.
	 * @return the payment transactions or empty list if none were found
	 */
	public List<PaymentTransaction> getPaymentTransactions() {
		List<PaymentTransaction> transactions = new ArrayList<PaymentTransaction>();

		for (Element element : getTransactionElements()) {
			PaymentTransaction transaction = scrapePaymentElement(element);
			if (transaction != null) {
				transactions.add(transaction);
			}
		}

		return transactions;
	}

	/**
	 * Gets the shop transactions from the page.
	 * @return the shop transactions or empty list if none were found
	 */
	public List<ShopTransaction> getShopTransactions() {
		List<ShopTransaction> transactions = new ArrayList<ShopTransaction>();

		for (Element element : getTransactionElements()) {
			ShopTransaction transaction = scrapeShopElement(element);
			if (transaction != null) {
				transactions.add(transaction);
			}
		}

		return transactions;
	}

	private Elements getTransactionElements() {
		return document.select("li.sectionItem");
	}

	private ShopTransaction scrapeShopElement(Element element) {
		ShopTransaction transaction = new ShopTransaction();

		//description
		Element descriptionElement = element.select("div.description").first();
		if (descriptionElement != null) {
			String description = descriptionElement.text();
			Matcher m = soldRegex.matcher(description);
			if (m.find()) {
				transaction.setQuantity(-Integer.parseInt(m.group(1).replace(",", "")));
				transaction.setItem(renamedItems.getSanitizedName(m.group(2)));
				transaction.setPlayer(m.group(3));
			} else {
				m = boughtRegex.matcher(description);
				if (m.find()) {
					transaction.setQuantity(Integer.parseInt(m.group(1).replace(",", "")));
					transaction.setItem(renamedItems.getSanitizedName(m.group(2)));
					transaction.setPlayer(m.group(3));
				} else {
					//not a shop transaction
					return null;
				}
			}
		}

		//timestamp
		transaction.setTs(parseTs(element));

		//amount
		transaction.setAmount(parseAmount(element));

		//balance
		transaction.setBalance(parseBalance(element));

		return transaction;
	}

	private PaymentTransaction scrapePaymentElement(Element element) {
		PaymentTransaction transaction = new PaymentTransaction();

		//description
		Element descriptionElement = element.select("div.description").first();
		if (descriptionElement != null) {
			String description = descriptionElement.text();
			Matcher m = paymentFromRegex.matcher(description);
			if (!m.find()) {
				m = paymentToRegex.matcher(description);
				if (!m.find()) {
					return null;
				}
			}

			transaction.setPlayer(m.group(1));
		}

		//timestamp
		transaction.setTs(parseTs(element));

		//amount
		transaction.setAmount(parseAmount(element));

		//balance
		transaction.setBalance(parseBalance(element));

		return transaction;
	}

	private Date parseTs(Element transactionElement) {
		Element tsElement = transactionElement.select("div.time abbr[data-time]").first();
		if (tsElement != null) {
			String dataTime = tsElement.attr("data-time");
			try {
				long ts = Long.parseLong(dataTime) * 1000;
				return new Date(ts);
			} catch (NumberFormatException e) {
				logger.log(Level.WARNING, "Transaction time could not be parsed from webpage: " + dataTime, e);
			}
		} else {
			tsElement = transactionElement.select("div.time span[title]").first();
			String tsText = tsElement.attr("title");
			try {
				return df.parse(tsText);
			} catch (ParseException e) {
				logger.log(Level.WARNING, "Transaction time could not be parsed from webpage: " + tsText, e);
			}
		}
		return null;
	}

	private int parseAmount(Element element) {
		Element amountElement = element.select("div.amount").first();
		if (amountElement != null) {
			String amountText = amountElement.text();
			Matcher m = amountRegex.matcher(amountText);
			if (m.find()) {
				try {
					int amount = Integer.parseInt(m.group(2).replace(",", ""));
					if ("-".equals(m.group(1))) {
						amount *= -1;
					}
					return amount;
				} catch (NumberFormatException e) {
					logger.log(Level.WARNING, "Transaction amount could not be parsed from webpage: " + amountText, e);
				}
			}
		}
		return 0;
	}

	private int parseBalance(Element element) {
		Element balanceElement = element.select("div.balance").first();
		if (balanceElement != null) {
			String balanceText = balanceElement.text().replace(",", "");
			try {
				return Integer.parseInt(balanceText);
			} catch (NumberFormatException e) {
				logger.log(Level.WARNING, "Transaction balance could not be parsed from webpage: " + balanceText, e);
			}
		}
		return 0;
	}
}