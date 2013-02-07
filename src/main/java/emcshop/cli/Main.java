package emcshop.cli;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import emcshop.PageScraper;
import emcshop.Transaction;
import emcshop.db.DirbyDbDao;
import emcshop.db.DirbyEmbeddedDbDao;

public class Main {
	private static final Logger logger = Logger.getLogger(Main.class.getName());

	private static final PrintStream out = System.out;

	private static int threadCount;
	private static int stopAtPage;
	private static DirbyDbDao dao;
	private static int curPage;
	private static int pagesProcessed = 0;
	private static int transactionsSaved = 0;
	private static Date page1TransactionDate = null;
	private static Transaction latestTransactionFromDb = null;
	private static Map<String, String> cookies;

	private static final Set<String> validArgs = new HashSet<String>();
	static {
		validArgs.add("help");
		validArgs.add("db");
		validArgs.add("stop-at-page");
		validArgs.add("start-at-page");
		validArgs.add("threads");
		validArgs.add("cookies-file");
		validArgs.add("latest");
	}

	public static void main(String[] args) throws Exception {
		//create the config folder in the user's home directory
		File config = new File(System.getProperty("user.home"), ".emc-shopkeeper");
		if (!config.exists() && !config.mkdir()) {
			throw new IOException("Could not create config folder: " + config.getAbsolutePath());
		}

		File defaultDbFolder = new File(config, "db");
		File defaultCookiesFile = new File(config, "cookies.properties");
		int defaultThreads = 4;
		int defaultStartPage = 1;

		Arguments arguments = new Arguments(args);

		if (arguments.exists(null, "help")) {
			//@formatter:off
			out.print(
			"EMC Shopkeeper\n" +
			"by shavingfoam\n" +
			"\n" +
			"--latest\n" +
			"  Prints out the latest transaction from the database.\n" +
			"--db=PATH\n" +
			"  Specifies the location of the database.\n" +
			"  Defaults to " + defaultDbFolder.getAbsolutePath() + "\n" +
			"--cookies-file=PATH\n" +
			"  Specifies the location of the file that has the login cookies.\n" +
			"  Defaults to " + defaultCookiesFile.getAbsolutePath() + "\n" +
			"--threads=NUM\n" +
			"  Specifies the number of pages to parse at once.\n" +
			"  Defaults to " + defaultThreads + "\n" +
			"--start-at-page=PAGE\n" +
			"  Specifies the page number to start at.\n" +
			"  Defaults to " + defaultStartPage + "\n" +
			"--stop-at-page=PAGE\n" +
			"  Specifies the page number to stop parsing at.\n" +
			"  Defaults to the last transaction page.\n" +
			"--help\n" +
			"  Prints this help message.\n"
			);
			//@formatter:on
			return;
		}

		Collection<String> invalidArgs = arguments.invalidArgs(validArgs);
		if (!invalidArgs.isEmpty()) {
			out.println("The following arguments are invalid:");
			for (String invalidArg : invalidArgs) {
				out.println("  " + invalidArg);
			}
			System.exit(1);
		}

		String cookiesFilePath = arguments.value(null, "cookies-file");
		File cookiesFile = (cookiesFilePath == null) ? defaultCookiesFile : new File(cookiesFilePath);
		if (!cookiesFile.exists()) {
			createEmptyCookiesFile(cookiesFile);
			out.println("No cookies set.  Log into empireminecraft.com, and then copy all cookies to the following properties file: " + cookiesFile.getAbsolutePath());
			System.exit(1);
		} else {
			cookies = readCookiesFromFile(cookiesFile);
		}

		String dbPath = arguments.value(null, "db");
		File dbFolder = (dbPath == null) ? defaultDbFolder : new File(dbPath);
		stopAtPage = arguments.valueInt(null, "stop-at-page", -1);
		curPage = arguments.valueInt(null, "start-at-page", defaultStartPage);
		threadCount = arguments.valueInt(null, "threads", defaultThreads);
		boolean latest = arguments.exists(null, "latest");

		dao = initDao(dbFolder);

		latestTransactionFromDb = dao.getLatestTransaction();

		if (latest) {
			if (latestTransactionFromDb == null) {
				out.println("Database is empty!");
			} else {
				NumberFormat nf = NumberFormat.getNumberInstance();
				int quantity = latestTransactionFromDb.getQuantity();
				if (quantity < 0) {
					out.println("Sold " + -quantity + " " + latestTransactionFromDb.getItem() + " to " + latestTransactionFromDb.getPlayer() + " for " + nf.format(latestTransactionFromDb.getAmount()) + "r.");
				} else {
					out.println("Bought " + quantity + " " + latestTransactionFromDb.getItem() + " from " + latestTransactionFromDb.getPlayer() + " for " + nf.format(-latestTransactionFromDb.getAmount()) + "r.");
				}
				out.println("Current balance: " + nf.format(latestTransactionFromDb.getBalance()) + "r");
			}
			return;
		}

		if (latestTransactionFromDb == null) {
			//database is empty
			//keep scraping until there are no more pages
			page1TransactionDate = getLatestTransactionDateOnPage1();
		}

		long start = System.currentTimeMillis();

		List<ScrapeThread> threads = new ArrayList<ScrapeThread>(threadCount);
		for (int i = 0; i < threadCount; i++) {
			ScrapeThread thread = new ScrapeThread();
			thread.setName("EMC-Shopkeeper-" + i);
			threads.add(thread);

			logger.info("Starting thread \"" + thread.getName() + "\"");
			thread.start();
		}

		//wait for threads to finish
		for (ScrapeThread thread : threads) {
			thread.join();
		}

		long end = System.currentTimeMillis();
		double seconds = (end - start) / 1000.0;
		logger.info(pagesProcessed + " pages processed and " + transactionsSaved + " transactions saved in " + seconds + " seconds.");
	}

	protected static void createEmptyCookiesFile(File cookiesFile) throws IOException {
		Properties props = new Properties();
		props.setProperty("__qca", "");
		props.setProperty("__gads", "");
		props.setProperty("emc_user", "");
		props.setProperty("emc_session", "");
		props.setProperty("__utma", "");
		props.setProperty("__utmb", "");
		props.setProperty("__utmc", "");
		props.setProperty("__utmz", "");

		Writer writer = null;
		try {
			writer = new FileWriter(cookiesFile);
			props.store(writer, null);
		} finally {
			IOUtils.closeQuietly(writer);
		}
	}

	protected static Map<String, String> readCookiesFromFile(File file) throws IOException {
		Properties props = null;
		Reader reader = null;
		try {
			reader = new FileReader(file);
			props = new Properties();
			props.load(reader);
		} finally {
			IOUtils.closeQuietly(reader);
		}

		Map<String, String> cookies = new HashMap<String, String>();
		for (Map.Entry<Object, Object> entry : props.entrySet()) {
			String name = (String) entry.getKey();
			String value = (String) entry.getValue();
			cookies.put(name, value);
		}
		return cookies;
	}

	private static class ScrapeThread extends Thread {
		@Override
		public void run() {
			try {
				while (true) {
					int page = nextPage();
					if (stopAtPage > 0 && page > stopAtPage) {
						break;
					}

					logger.info("Getting page " + page + ".");
					Document document = getPage(page);
					List<Transaction> transactions = PageScraper.scrape(document);

					boolean quitAfterInsert = false;
					if (latestTransactionFromDb != null) {
						int end = -1;
						Date latestTransactionFromDbDate = latestTransactionFromDb.getTs();
						for (int i = 0; i < transactions.size(); i++) {
							Transaction transaction = transactions.get(i);
							if (transaction.getTs().getTime() <= latestTransactionFromDbDate.getTime()) {
								end = i;
								break;
							}
						}
						if (end >= 0) {
							transactions = transactions.subList(0, end);
							quitAfterInsert = true;
						}
					} else if (page1TransactionDate != null && page > 1) {
						int end = -1;
						for (int i = 0; i < transactions.size(); i++) {
							Transaction transaction = transactions.get(i);
							if (transaction.getTs().getTime() >= page1TransactionDate.getTime()) {
								end = i;
								break;
							}
						}
						if (end >= 0) {
							transactions = transactions.subList(0, end);
							quitAfterInsert = true;
						}
					}

					synchronized (dao) {
						for (Transaction transaction : transactions) {
							dao.insertTransaction(transaction);
						}
						dao.commit();

						transactionsSaved += transactions.size();
						pagesProcessed++;
					}

					if (quitAfterInsert) {
						break;
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static Date getLatestTransactionDateOnPage1() throws IOException {
		Document page = getPage(1);
		List<Transaction> transactions = PageScraper.scrape(page);
		//TODO what if there are no shop transactions on this page?
		return transactions.get(0).getTs();
	}

	private static synchronized int nextPage() {
		return curPage++;
	}

	private static DirbyDbDao initDao(File folder) throws SQLException {
		return new DirbyEmbeddedDbDao(new File(folder, "data"));
	}

	private static Document getPage(int page) throws IOException {
		String url = "http://empireminecraft.com/rupees/transactions/?page=" + page;
		int tries = 0;
		do {
			try {
				return Jsoup.connect(url).timeout(30000).cookies(cookies).get();
			} catch (IOException e) {
				if (tries >= 3) {
					throw e;
				}
				logger.warning("IOException thrown when trying to load page, trying again.\n  URL: " + url + "\n  Message: " + e.getMessage());
				tries++;
			}
		} while (true);
	}
}
