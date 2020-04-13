package emcshop.gui;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import emcshop.gui.images.Images;

/**
 * <p>
 * Chaining builder class for showing {@link JOptionPane} dialog boxes.
 * </p>
 * <p>
 * Note: When using "UIManager.getSystemLookAndFeelClassName()" on Windows, the
 * default icons get cropped if your display scaling is set to 150%. See:
 * https://stackoverflow.com/q/33926645
 * </p>
 * @author Michael Angstadt
 */
public class DialogBuilder {
	private Component parent;
	private String title, text;
	private int messageType, optionType = JOptionPane.DEFAULT_OPTION;
	private Icon icon;
	private String[] choices;
	private String defaultChoice;

	/**
	 * Shows a dialog box without an icon.
	 * @return the builder
	 */
	public static DialogBuilder plain() {
		return new DialogBuilder().messageType(JOptionPane.PLAIN_MESSAGE);
	}

	/**
	 * Shows a dialog box that uses a "question" icon.
	 * @return the builder
	 */
	public static DialogBuilder question() {
		return new DialogBuilder().messageType(JOptionPane.QUESTION_MESSAGE);
	}

	/**
	 * Shows a dialog box that uses an "information" icon.
	 * @return the builder
	 */
	public static DialogBuilder info() {
		return new DialogBuilder().messageType(JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * Shows a dialog box that uses a "warning" icon.
	 * @return the builder
	 */
	public static DialogBuilder warning() {
		return new DialogBuilder().messageType(JOptionPane.WARNING_MESSAGE);
	}

	/**
	 * Shows a dialog box that uses an "error" icon.
	 * @return the builder
	 */
	public static DialogBuilder error() {
		return new DialogBuilder().messageType(JOptionPane.ERROR_MESSAGE);
	}

	private DialogBuilder() {
		//hide
	}

	/**
	 * Sets the parent window.
	 * @param parent the parent
	 * @return this
	 */
	public DialogBuilder parent(Component parent) {
		this.parent = parent;
		return this;
	}

	/**
	 * Sets the text that will go in the title bar.
	 * @param title the title
	 * @return this
	 */
	public DialogBuilder title(String title) {
		this.title = title;
		return this;
	}

	/**
	 * Sets the text that will go inside of the dialog box.
	 * @param lines each line of the message
	 * @return this
	 */
	public DialogBuilder text(String... lines) {
		text = String.join(System.lineSeparator(), lines);
		return this;
	}

	/**
	 * <p>
	 * Sets the type of message the dialog box contains.
	 * </p>
	 * <p>
	 * <b>Available message types:</b>
	 * </p>
	 * <ul>
	 * <li>{@link JOptionPane#INFORMATION_MESSAGE}: An informational
	 * message</li>
	 * <li>{@link JOptionPane#QUESTION_MESSAGE}: A question</li>
	 * <li>{@link JOptionPane#WARNING_MESSAGE}: A warning message</li>
	 * <li>{@link JOptionPane#ERROR_MESSAGE}: An error message</li>
	 * </ul>
	 * @param messageType the message type (see list above)
	 * @return this
	 */
	public DialogBuilder messageType(int messageType) {
		this.messageType = messageType;
		return this;
	}

	/**
	 * Sets a custom icon that will display on the left side of the dialog box.
	 * The icon is automatically resized so that its height and width are no
	 * greater than 48 pixels.
	 * @param icon the icon
	 * @return this
	 */
	public DialogBuilder icon(ImageIcon icon) {
		this.icon = Images.scale(icon, 48);
		return this;
	}

	/**
	 * <p>
	 * Defines what kind of buttons the dialog box should have. This method
	 * should only be used if {@link #show} is going to be called. If you are
	 * creating an input dialog box using {@link #showInput}, then the buttons
	 * cannot be customized.
	 * </p>
	 * <p>
	 * <b>Available option types:</b>
	 * </p>
	 * <ul>
	 * <li>{@link JOptionPane#DEFAULT_OPTION}: A single "OK" button (the
	 * default)</li>
	 * <li>{@link JOptionPane#OK_CANCEL_OPTION}: An "OK" and "Cancel" button (in
	 * that order)</li>
	 * <li>{@link JOptionPane#YES_NO_OPTION}: A "Yes" and "No" button (in that
	 * order)</li>
	 * <li>{@link JOptionPane#YES_NO_CANCEL_OPTION}: A "Yes", "No", and "Cancel"
	 * button (in that order)</li>
	 * </ul>
	 * @param optionType specifies what kinds of buttons to display (see list
	 * above)
	 * @param buttonLabels the labels to assign to each button (if not
	 * specified, then default labels will be used). To specify which button
	 * will have keyboard focus, place an asterisk character at the beginning of
	 * the string. The button that has keyboard focus will be "clicked" if the
	 * user presses Enter or spacebar when the dialog opens.
	 * @return this
	 */
	public DialogBuilder buttons(int optionType, String... buttonLabels) {
		this.optionType = optionType;
		return choices(buttonLabels);
	}

	/**
	 * Causes a drop down list to be displayed instead of a text box. This
	 * method should only be used if {@link #showInput} is going to be called.
	 * @param choices the items to add to the drop down list. To specify which
	 * item will selected by default, place an asterisk character at the
	 * beginning of the string.
	 * @return this
	 */
	public DialogBuilder choices(String... choices) {
		if (choices.length > 0) {
			this.choices = new String[choices.length];
			for (int i = 0; i < choices.length; i++) {
				String label = choices[i];
				if (label.startsWith("*")) {
					label = label.substring(1);
					defaultChoice = label;
				}
				this.choices[i] = label;
			}
		}
		return this;
	}

	/**
	 * Creates the dialog box and displays it. This method is blocking.
	 * @return the user's choice (one of the constants in the
	 * {@link JOptionPane} class).
	 */
	public int show() {
		return JOptionPane.showOptionDialog( //@formatter:off
			parent,
			text,
			title,
			optionType,
			messageType,
			icon,
			choices,
			defaultChoice
		); //@formatter:on
	}

	/**
	 * Creates the dialog box as an input dialog and displays it. This method is
	 * blocking.
	 * @return the value the user entered or null if they cancelled it
	 */
	public String showInput() {
		return (String) JOptionPane.showInputDialog( //@formatter:off
			parent,
			text,
			title,
			messageType,
			icon,
			choices,
			defaultChoice
		); //@formatter:on
	}
}
