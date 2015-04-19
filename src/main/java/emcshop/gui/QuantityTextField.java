package emcshop.gui;

import javax.swing.JTextField;

import emcshop.util.QuantityFormatter;

/**
 * A text field that accepts a quantity value.
 */
@SuppressWarnings("serial")
public class QuantityTextField extends JTextField {
	private final QuantityFormatter qf = new QuantityFormatter();

	/**
	 * Gets the quantity value.
	 * @param stackSize the size of a stack (e.g. "64")
	 * @return the quantity value or null if the text field is empty
	 * @throws NumberFormatException if the text is not in the correct format
	 */
	public Integer getQuantity(int stackSize) throws NumberFormatException {
		String text = getText();
		if (text.isEmpty()) {
			return null;
		}

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
	 * Sets the value of this quantity field.
	 * @param quantity the quantity or null to empty the field
	 * @param stackSize the size of a stack (will cause the value to be
	 * displayed using "stack" formatting), or null to format the value as a
	 * regular integer
	 */
	public void setQuantity(Integer quantity, Integer stackSize) {
		if (quantity == null) {
			setText("");
			return;
		}

		if (stackSize == null) {
			setText(quantity.toString());
			return;
		}

		setText(qf.format(quantity, stackSize));
	}

	/**
	 * Determines if the quantity should be added to the existing total.
	 * @return true to add, false not to
	 */
	public boolean isAdd() {
		String text = getText();
		return text.startsWith("+") || text.startsWith("-");
	}

	/**
	 * Determines if the text box contains a parsable value.
	 * @return true if the value is valid, false if not
	 */
	public boolean hasValidValue() {
		try {
			getQuantity(64);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
