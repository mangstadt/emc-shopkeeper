package emcshop.util;

import java.text.NumberFormat;

/**
 * Utility class for formatting numbers.
 * @author Michael Angstadt
 */
public class NumberFormatter {
	private static final NumberFormat nf = NumberFormat.getNumberInstance();

	/**
	 * Formats a quantity as a string.
	 * @param quantity the quantity
	 * @return the quantity string (e.g. "+1,210")
	 */
	public static String formatQuantity(int quantity) {
		String quantityStr = nf.format(quantity);
		if (quantity > 0) {
			quantityStr = "+" + quantityStr;
		}
		return quantityStr;
	}

	/**
	 * Formats a rupee amount as a string.
	 * @param rupees the amount of rupees
	 * @return the rupee string (e.g. "+1,210r")
	 */
	public static String formatRupees(int rupees) {
		return formatQuantity(rupees) + "r";
	}

	/**
	 * Formats a rupee amount as colored HTML.
	 * @param rupees the amount of rupees
	 * @return the colored HTML string
	 */
	public static String formatRupeesWithColor(int rupees) {
		String text = formatRupees(rupees);
		return colorize(text, rupees);
	}

	/**
	 * Formats a quantity as colored HTML.
	 * @param quantity the quantity
	 * @return the colored HTML string
	 */
	public static String formatQuantityWithColor(int quantity) {
		String text = formatQuantity(quantity);
		return colorize(text, quantity);
	}

	private static String colorize(String text, int number) {
		if (number == 0) {
			return text;
		}

		String color = (number < 0) ? "red" : "green";
		return "<font color=" + color + ">" + text + "</font>";
	}
}
