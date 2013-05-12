package emcshop.util;

/**
 * Just miscellaneous methods.
 * @author Michael Angstadt
 */
public class MiscUtils {
	/**
	 * Builds a standardized tooltip string.
	 * @param text the tooltip text
	 * @return the standardized tooltip string
	 */
	public static String toolTipText(String text) {
		text = text.replace("\n", "<br>");
		return "<html><div width=300>" + text + "</div></html>";
	}
}
