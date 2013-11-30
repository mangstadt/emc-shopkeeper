package emcshop.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JTextField;

import emcshop.gui.images.ImageManager;

/**
 * A textbox used for entering the item/player names to filter by.
 */
@SuppressWarnings("serial")
public class FilterTextField extends JTextField {
	private final JButton clearButton;
	{
		clearButton = new JButton(ImageManager.getClearIcon());
		clearButton.setToolTipText("Clear");
		clearButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (getText().isEmpty()) {
					return;
				}
				setText("");
				fireActionEvent();
			}
		});
	}

	/**
	 * Splits the player/item names that are comma-delimited.
	 * @return the names
	 */
	public List<String> getNames() {
		String split[] = getText().trim().split("\\s*,\\s*");
		List<String> filteredItems = new ArrayList<String>(split.length);
		for (String s : split) {
			if (s.length() > 0) {
				filteredItems.add(s);
			}
		}
		return filteredItems;
	}

	/**
	 * Simulates pressing "enter" on the text field.
	 */
	public void fireActionEvent() {
		for (ActionListener listener : listenerList.getListeners(ActionListener.class)) {
			listener.actionPerformed(null);
		}
	}

	/**
	 * Gets the clear button associated with this text box.
	 * @return
	 */
	public JButton getClearButton() {
		return clearButton;
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		clearButton.setEnabled(enabled);
	}
}