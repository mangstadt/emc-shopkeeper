package emcshop.util;

import java.awt.Color;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Base class for various number formatters
 * @author Michael Angstadt
 */
public class BaseFormatter {
	protected final NumberFormat nf;
	protected boolean plus = false;
	protected boolean color = false;

	public BaseFormatter() {
		nf = NumberFormat.getInstance();
	}

	public BaseFormatter(String formatStr) {
		nf = new DecimalFormat(formatStr);
	}

	/**
	 * Sets whether to include a "+" prefix before all positive values.
	 * @param plus true to include the prefix, false not to (defaults to false)
	 */
	public void setPlus(boolean plus) {
		this.plus = plus;
	}

	/**
	 * Sets whether to add HTML coloring to the formatted value.
	 * @param color true to color the value, false not to (defaults to false)
	 */
	public void setColor(boolean color) {
		this.color = color;
	}

	/**
	 * Formats the value.
	 * @param amount the value to format
	 * @return the formatted value
	 */
	public String format(double value) {
		String str = nf.format(value);
		if (value > 0 && plus) {
			str = '+' + str;
		}
		if (color) {
			str = colorize(str, value);
		}
		return str;
	}

	/**
	 * Colors some text with HTML.
	 * @param text the formatted number
	 * @param number the number that is being formatted
	 * @return the colored text
	 */
	protected String colorize(String text, double number) {
		if (number == 0.0) {
			return text;
		}

		String color = (number < 0) ? "red" : "green";
		return "<font color=" + color + ">" + text + "</font>";
	}

	/**
	 * Gets the color used for a numeric value.
	 * @param value the value
	 * @return the color or null if the value is zero
	 */
	public static Color getColor(int value) {
		if (value == 0) {
			return null;
		}

		return (value < 0) ? Color.red : new Color(0, 128, 0);
	}
}
