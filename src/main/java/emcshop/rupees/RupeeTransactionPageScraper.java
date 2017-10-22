package emcshop.rupees;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emcshop.rupees.dto.RupeeTransaction;
import emcshop.rupees.dto.RupeeTransactionPage;
import emcshop.rupees.scribe.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Scrapes rupee transaction history HTML pages. This class is thread-safe.
 *
 * @author Michael Angstadt
 */
public class RupeeTransactionPageScraper {
    private static final Logger logger = Logger.getLogger(RupeeTransactionPageScraper.class.getName());

    private final Pattern balanceRegex = Pattern.compile("^Your balance: ([\\d,]+)$", Pattern.CASE_INSENSITIVE);
    private final Pattern amountRegex = Pattern.compile("^(-|\\+)\\s*([\\d,]+)$", Pattern.CASE_INSENSITIVE);

    private final List<RupeeTransactionScribe<?>> scribes = new ArrayList<RupeeTransactionScribe<?>>();

    {
        scribes.add(new ShopTransactionScribe());
        scribes.add(new PaymentTransactionScribe());
        scribes.add(new DailySigninBonusScribe());
        scribes.add(new HorseSummonFeeScribe());
        scribes.add(new MailFeeScribe());
        scribes.add(new EggifyFeeScribe());
        scribes.add(new LockTransactionScribe());
        scribes.add(new VoteBonusScribe());
        scribes.add(new VaultFeeScribe());
    }

    public RupeeTransactionPageScraper() {
        //empty
    }

    /**
     * @param customScribes any additional, custom scribes to use to parse the
     *                      transactions
     */
    public RupeeTransactionPageScraper(Collection<RupeeTransactionScribe<?>> customScribes) {
        this.scribes.addAll(customScribes);
    }

    /**
     * Scrapes a transaction page.
     *
     * @param document the HTML page to scrape
     * @return the scraped page or null if the given HTML page is not a rupee
     * transaction page
     */
    public RupeeTransactionPage scrape(Document document) {
        List<RupeeTransaction> transactions = parseTransactions(document);
        if (transactions == null) {
            return null;
        }

        //@formatter:off
        return new RupeeTransactionPage(
                parseRupeeBalance(document),
                parseCurrentPage(document),
                parseTotalPages(document),
                transactions
        );
        //@formatter:on
    }

    /**
     * Parses the transactions from a transaction page.
     *
     * @param document the transaction HTML page
     * @return the transactions or null if the given HTML page is not a rupee
     * transaction page
     */
    private List<RupeeTransaction> parseTransactions(Document document) {
        Element containerElement = document.select("ol.sectionItems").first();
        if (containerElement == null) {
            return null;
        }

		/*
         * Set initial capacity to 30 because each rupee transaction page
		 * contains that many transactions.
		 */
        List<RupeeTransaction> transactions = new ArrayList<RupeeTransaction>(30);

        for (Element element : containerElement.select("li.sectionItem")) {
            try {
                String description = parseDescription(element);
                RupeeTransaction.Builder<?> builder = null;
                for (RupeeTransactionScribe<?> scribe : scribes) {
                    try {
                        builder = scribe.parse(description);
                        if (builder != null) {
                            break;
                        }
                    } catch (Exception e) {
                        logger.log(Level.WARNING, scribe.getClass().getSimpleName() + " scribe threw an excception. Skipping it.", e);
                    }
                }
                if (builder == null) {
                    builder = new RupeeTransaction.Builder<RupeeTransaction.Builder<?>>();
                }

                builder.ts(parseTs(element));
                builder.description(description);
                builder.amount(parseAmount(element));
                builder.balance(parseBalance(element));

                transactions.add(builder.build());
            } catch (Exception e) {
                /*
				 * Skip the transaction if any of the fields cannot be properly
				 * parsed.
				 */
                logger.log(Level.WARNING, "Problem parsing rupee transaction, skipping.", e);
            }
        }

        return transactions;
    }

    /**
     * Parses the player's total rupee balance from a transaction page.
     *
     * @param document the transaction HTML page
     * @return the rupee balance or null if not found
     */
    private Integer parseRupeeBalance(Document document) {
        Element element = document.getElementById("rupeesBalance");
        if (element == null) {
            return null;
        }

        Matcher m = balanceRegex.matcher(element.text());
        if (!m.find()) {
            return null;
        }

        try {
            return parseNumber(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses the page number of a transaction page
     *
     * @param document the transaction HTML page
     * @return the page number or null if not found
     */
    private Integer parseCurrentPage(Document document) {
        Element element = document.select(".PageNav").first();
        if (element == null) {
            return null;
        }

        try {
            return parseNumber(element.attr("data-page"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse the total number of transaction pages from a transaction page.
     *
     * @param document the transaction HTML page
     * @return the total number of pages or null if not found
     */
    private Integer parseTotalPages(Document document) {
        Element element = document.select(".PageNav").first();
        if (element == null) {
            return null;
        }

        try {
            return parseNumber(element.attr("data-last"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses a transaction's description.
     *
     * @param transactionElement the transaction HTML element
     * @return the description
     */
    private String parseDescription(Element transactionElement) {
        Element descriptionElement = transactionElement.select("div.description").first();
        return descriptionElement.text();
    }

    /**
     * Parses a transaction's timestamp.
     *
     * @param transactionElement the transaction HTML element
     * @return the timestamp
     * @throws ParseException if the timestamp can't be parsed
     */
    private Date parseTs(Element transactionElement) throws ParseException {
        Element tsElement = transactionElement.select("div.time abbr[data-time]").first();
        if (tsElement != null) {
            String dataTime = tsElement.attr("data-time");
            long ts = Long.parseLong(dataTime) * 1000;
            return new Date(ts);
        }

        tsElement = transactionElement.select("div.time span[title]").first();
        String tsText = tsElement.attr("title");

		/*
		 * Instantiate new DateFormat object to keep this class thread-safe.
		 * Also, note that English month names are used, no matter where the
		 * player lives.
		 */
        DateFormat df = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm aa", Locale.US);

        return df.parse(tsText);
    }

    /**
     * Parses the amount of rupees that were processed in a transaction.
     *
     * @param transactionElement the transaction HTML element
     * @return the amount
     */
    private int parseAmount(Element transactionElement) {
        Element amountElement = transactionElement.select("div.amount").first();
        String amountText = amountElement.text();

        Matcher m = amountRegex.matcher(amountText);
        m.find();

        int amount = parseNumber(m.group(2));
        if ("-".equals(m.group(1))) {
            amount *= -1;
        }
        return amount;
    }

    /**
     * Parses the player's balance after the transaction was applied.
     *
     * @param transactionElement the transaction HTML element
     * @return the balance
     */
    private int parseBalance(Element transactionElement) {
        Element balanceElement = transactionElement.select("div.balance").first();
        String balanceText = balanceElement.text();
        return parseNumber(balanceText);
    }

    /**
     * Parses a number that may or may not have commas in it.
     *
     * @param value the string value (e.g. "12,560")
     * @return the parsed number
     */
    private static int parseNumber(String value) {
        value = value.replace(",", "");
        return Integer.parseInt(value);
    }
}