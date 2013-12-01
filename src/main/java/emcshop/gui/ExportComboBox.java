package emcshop.gui;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;
import javax.swing.JOptionPane;

@SuppressWarnings("serial")
public abstract class ExportComboBox extends JComboBox implements ActionListener {
	private final MainFrame owner;

	private final String heading = "<select format>";
	private final String bbCode = "BB Code";
	private final String csv = "CSV";

	public ExportComboBox(MainFrame owner) {
		this.owner = owner;

		addItem(heading);
		addItem(bbCode);
		addItem(csv);
		addActionListener(this);
	}

	@Override
	public void actionPerformed(ActionEvent arg0) {
		String selected = (String) getSelectedItem();
		String text = null;
		if (selected == csv) {
			text = csv();
		} else if (selected == bbCode) {
			text = bbCode();
		}

		//re-select the first element
		setSelectedItem(heading);

		if (text != null) {
			Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
			StringSelection stringSelection = new StringSelection(text);
			c.setContents(stringSelection, stringSelection);

			JOptionPane.showMessageDialog(owner, "Copied to clipboard.", "Copied", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	public abstract String bbCode();

	public abstract String csv();
}
