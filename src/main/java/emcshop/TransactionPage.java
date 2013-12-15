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
	 * Transactions that are older than one week have a different timestamp
	 * format.
	 */
	private final DateFormat weekOldTimestampFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm aa", Locale.US);

	private boolean loggedIn;
	private Integer rupeeBalance;
	private Date firstTransactionDate;
	private List<RupeeTransaction> transactions = new ArrayList<RupeeTransaction>();

	/**
	 * @param document the webpage to parse
	 */
	public TransactionPage(Document document) {
		rupeeBalance = parseRupeeBalance(document);

		Elements elements = document.select("li.sectionItem");
		if (elements.isEmpty()) {
			loggedIn = false;
			return;
		}

		loggedIn = true;

		firstTransactionDate = parseTs(elements.first());

		for (Element element : elements) {
			String description = parseDescription(element);
			RupeeTransaction transaction = parseTransaction(description);

			transaction.setTs(parseTs(element));
			transaction.setAmount(parseAmount(element));
			transaction.setBalance(parseBalance(element));
			transactions.add(transaction);
		}
	}

	public RupeeTransaction parseTransaction(String description) {
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

	/**
	 * Determines whether the user has been logged in.
	 * @return true if the user has been logged in, false if not
	 */
	public boolean isLoggedIn() {
		return loggedIn;
	}

	/**
	 * Gets the date of the very first transaction on the page (may or may not
	 * be a shop transaction).
	 * @return the date or null if no transactions were found
	 */
	public Date getFirstTransactionDate() {
		return firstTransactionDate;
	}

	/**
	 * Gets the player's total rupee balance.
	 * @return the total rupee balance or null if not found
	 */
	public Integer getRupeeBalance() {
		return rupeeBalance;
	}

	/**
	 * Gets the transactions.
	 * @return the miscellaneous transactions
	 */
	public List<RupeeTransaction> getTransactions() {
		return transactions;
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
		transaction.setQuantity(Integer.parseInt(m.group(1).replace(",", "")) * negate);
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

		return Integer.valueOf(m.group(1).replace(",", ""));
	}

	private RawTransaction scrapeTransaction(Element element) {
		RawTransaction transaction = new RawTransaction();

		transaction.setDescription(parseDescription(element));
		transaction.setTs(parseTs(element));
		transaction.setAmount(parseAmount(element));
		transaction.setBalance(parseBalance(element));

		return transaction;
	}

	private String parseDescription(Element transactionElement) {
		Element descriptionElement = transactionElement.select("div.description").first();
		return (descriptionElement == null) ? null : descriptionElement.text();
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
				return weekOldTimestampFormat.parse(tsText);
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