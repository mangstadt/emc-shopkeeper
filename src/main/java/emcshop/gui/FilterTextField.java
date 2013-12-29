package emcshop.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JTextField;

import emcshop.gui.images.ImageManager;
import emcshop.util.FilterList;
import emcshop.util.GuiUtils;

/**
 * A textbox used for entering the item/player names to filter by.
 */
@SuppressWarnings("serial")
public class FilterTextField extends JTextField {
	private final JButton clearButton;
	{
		clearButton = new JButton(ImageManager.getClearIcon());
		if (!GuiUtils.linux) {
			clearButton.setToolTipText("Clear");
		}
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
	public FilterList getNames() {
		FilterList list = new FilterList();
		String keywords[] = getText().trim().split("\\s*,\\s*");
		for (String keyword : keywords) {
			if (keyword.isEmpty()) {
				continue;
			}

			boolean wholeMatch = false;
			if (keyword.startsWith("\"") && keyword.endsWith("\"")) {
				keyword = keyword.substring(1, keyword.length() - 1); //remove double quotes
				wholeMatch = true;
			}

			list.add(keyword, wholeMatch);
		}
		return list;
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