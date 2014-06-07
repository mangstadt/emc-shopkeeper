package emcshop.gui.lib;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

/**
 * Creates a panel with a border around it and a title.
 */
@SuppressWarnings("serial")
public class GroupPanel extends JPanel {
	public GroupPanel(String title) {
		setBorder(BorderFactory.createTitledBorder(title));
	}
}
