package emcshop.gui;

import javax.swing.JTextField;

/**
 * A text field that accepts a quantity value.
 */
@SuppressWarnings("serial")
public class QuantityTextField extends JTextField {
	/**
	 * Gets the quantity value.
	 * @param stackSize the size of a stack (e.g. "64")
	 * @return the quantity value
	 * @throws NumberFormatException if the text is not in the correct format
	 */
	public Integer getQuantity(int stackSize) throws NumberFormatException {
		String text = getText();
		if (text.startsWith("+")) {
			text = text.substring(1);
		}

		String split[] = text.split("/", 2);
		if (split.length == 1) {
			return Integer.valueOf(split[0]);
		}

		int remainder = split[1].isEmpty() ? 0 : Integer.valueOf(split[1]);
		return Integer.valueOf(split[0]) * stackSize + remainder;
	}

	/**
	 * Determines if the quantity should be added to the existing total.
	 * @return true to add, false not to
	 */
	public boolean isAdd() {
		String text = getText();
		return text.startsWith("+") || text.startsWith("-");
	}
}
