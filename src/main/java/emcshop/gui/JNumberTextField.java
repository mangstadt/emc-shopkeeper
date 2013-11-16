package emcshop.gui;

import javax.swing.JTextField;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

/**
 * A text field that only accepts numeric values.
 * @see "http://stackoverflow.com/a/10927828/13379"
 */
@SuppressWarnings("serial")
public class JNumberTextField extends JTextField {
	private static final char DOT = '.';
	private static final char NEGATIVE = '-';
	private static final int DEF_PRECISION = 2;

	public static final int NUMERIC = 2;
	public static final int DECIMAL = 3;

	public static final String FM_NUMERIC = "0123456789";
	public static final String FM_DECIMAL = FM_NUMERIC + DOT;

	private int maxLength = 0;
	private int format = NUMERIC;
	private String negativeChars = "";
	private String allowedChars = null;
	private boolean allowNegative = false;
	private int precision = 0;

	protected PlainDocument numberFieldFilter;

	public JNumberTextField() {
		this(10, NUMERIC);
	}

	public JNumberTextField(int maxLen) {
		this(maxLen, NUMERIC);
	}

	public JNumberTextField(int maxLen, int format) {
		setAllowNegative(true);
		setMaxLength(maxLen);
		setFormat(format);

		numberFieldFilter = new JNumberFieldFilter();
		super.setDocument(numberFieldFilter);
	}

	public void setMaxLength(int maxLen) {
		maxLength = (maxLen > 0) ? maxLen : 0;
	}

	public int getMaxLength() {
		return maxLength;
	}

	public void setPrecision(int precision) {
		if (format == NUMERIC) {
			return;
		}

		this.precision = (precision >= 0) ? precision : DEF_PRECISION;
	}

	public int getPrecision() {
		return precision;
	}

	public Number getNumber() {
		String text = getText();
		if (text.isEmpty()) {
			return null;
		}

		return (format == NUMERIC) ? new Integer(text) : new Double(text);
	}

	public void setNumber(Number value) {
		setText(String.valueOf(value));
	}

	public Integer getInteger() {
		String text = getText();
		return text.isEmpty() ? null : Integer.valueOf(text);
	}

	public void setInt(int value) {
		setText(String.valueOf(value));
	}

	public Float getFloat() {
		String text = getText();
		return text.isEmpty() ? null : Float.valueOf(text);
	}

	public void setFloat(float value) {
		setText(String.valueOf(value));
	}

	public Double getDouble() {
		String text = getText();
		return text.isEmpty() ? null : Double.valueOf(text);
	}

	public void setDouble(double value) {
		setText(String.valueOf(value));
	}

	public int getFormat() {
		return format;
	}

	public void setFormat(int format) {
		switch (format) {
		case NUMERIC:
		default:
			this.format = NUMERIC;
			this.precision = 0;
			this.allowedChars = FM_NUMERIC;
			break;

		case DECIMAL:
			this.format = DECIMAL;
			this.precision = DEF_PRECISION;
			this.allowedChars = FM_DECIMAL;
			break;
		}
	}

	public void setAllowNegative(boolean value) {
		allowNegative = value;
		negativeChars = value ? NEGATIVE + "" : "";
	}

	public boolean isAllowNegative() {
		return allowNegative;
	}

	public void setDocument(Document document) {
	}

	private class JNumberFieldFilter extends PlainDocument {
		public JNumberFieldFilter() {
			super();
		}

		public void insertString(int offset, String str, AttributeSet attr) throws BadLocationException {
			String text = getText(0, offset) + str + getText(offset, (getLength() - offset));

			if (str == null || text == null) {
				return;
			}

			for (int i = 0; i < str.length(); i++) {
				if ((allowedChars + negativeChars).indexOf(str.charAt(i)) == -1) {
					return;
				}
			}

			int precisionLength = 0, dotLength = 0, minusLength = 0;
			int textLength = text.length();

			try {
				if (format == NUMERIC) {
					if (!((text.equals(negativeChars)) && (text.length() == 1))) {
						new Long(text);
					}
				} else if (format == DECIMAL) {
					if (!((text.equals(negativeChars)) && (text.length() == 1))) {
						new Double(text);
					}

					int dotIndex = text.indexOf(DOT);
					if (dotIndex != -1) {
						dotLength = 1;
						precisionLength = textLength - dotIndex - dotLength;

						if (precisionLength > precision) {
							return;
						}
					}
				}
			} catch (Exception ex) {
				return;
			}

			if (text.startsWith("" + NEGATIVE)) {
				if (!allowNegative) {
					return;
				}
				minusLength = 1;
			}

			if (maxLength < (textLength - dotLength - precisionLength - minusLength)) {
				return;
			}

			super.insertString(offset, str, attr);
		}
	}
}
