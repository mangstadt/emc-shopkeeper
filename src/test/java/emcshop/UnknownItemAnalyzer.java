package emcshop;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility program that reads the unknown item error reports from the error logs
 * and displays only those items which are not in the item index.
 * @author Michael Angstadt
 */
public class UnknownItemAnalyzer {
	private static final String ERROR_LOG_PATH = "errors.0.xml";

	public static void main(String[] args) throws Exception {
		/*
		 * The error log uses this charset because it is the charset of the
		 * server's PHP installation. There are some characters that only appear
		 * in this charset (notably: §).
		 */
		Charset errorLogCharset = Charset.forName("ISO-8859-1");

		String errorLogContent = new String(Files.readAllBytes(Paths.get(ERROR_LOG_PATH)), errorLogCharset);

		Pattern p = Pattern.compile("Unknown items: \\[(.*)\\]</Message></Error>");
		Matcher m = p.matcher(errorLogContent);

		Set<String> unknownItems = new TreeSet<>();
		while (m.find()) {
			String names[] = m.group(1).split(", ");

			Arrays.stream(names) //@formatter:off
				.filter(UnknownItemAnalyzer::isNotACustomNamedItem)
				.filter(UnknownItemAnalyzer::isNotInKnownItemsList)
			.forEach(unknownItems::add); //@formatter:on
		}

		for (String unknownItem : unknownItems) {
			/*
			 * Remove the color formatting codes.
			 */
			String displayName = unknownItem.replaceAll("§.", "");

			/*
			 * If a dash is present, use the name to the right of the dash as the display name.
			 * 
			 * Many promos have names like these:
			 * 
			 * @formatter:off 
			 * §fFragile Elytra - §fFragile Elytra
			 * Firework - §b§l+§7§lEmpire Firework§b§l+
			 * @formatter:on
			 */
			displayName = displayName.replaceAll(".* - ", "");

			String templateXml = String.format("<Item name=\"%s\" stack=\"1\" image=\"img.png\" emcNames=\"%s\" categories=\"5\" />", displayName, unknownItem);

			System.out.println(unknownItem);
			System.out.println(templateXml);
			System.out.println();
		}
	}

	private static boolean isNotACustomNamedItem(String name) {
		/*
		 * Ignore named books. For example:
		 * 
		 * Book: "10,000r Prize-1" by TrumanIII
		 */
		if (name.startsWith("Book: ")) {
			return false;
		}

		return true;
	}

	private static boolean isNotInKnownItemsList(String name) {
		return !ItemIndex.instance().isEmcNameRecognized(name);
	}
}
