package emcshop.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;

import net.miginfocom.swing.MigLayout;
import emcshop.gui.images.ImageManager;
import emcshop.util.GuiUtils;

/**
 * Confirmation dialog for when the user chooses to wipe the database.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class ResetDatabaseDialog extends JDialog {
	private boolean result;

	/**
	 * Shows the dialog.
	 * @return true if the user chose to wipe the database, false if not
	 */
	public static boolean show(Window owner) {
		ResetDatabaseDialog dialog = new ResetDatabaseDialog(owner);
		dialog.setVisible(true);
		return dialog.result;
	}

	private ResetDatabaseDialog(Window owner) {
		super(owner, "Reset Database");
		setModal(true);
		setResizable(false);

		JButton resetButton = new JButton("Reset Database");
		resetButton.setForeground(new Color(128, 0, 0));
		resetButton.setFont(new Font(getFont().getName(), Font.BOLD, getFont().getSize()));
		resetButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				result = true;
				dispose();
			}
		});

		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				result = false;
				dispose();
			}
		});
		GuiUtils.onEscapeKeyPress(this, cancel);

		JLabel warningIcon = new JLabel(ImageManager.getWarningIcon());

		JLabel warningTitle = new JLabel("<html><center><h2>WARNING!</h2></center></html>");

		//@formatter:off
		JLabel text = new JLabel(
		"<html><div width=500>" +
		"Resetting the database will delete all transaction data within this profile.  " +
		"This includes shop transactions, payment transactions, bonus/fee tallies, and shop inventory.<br><br>" +
		"Re-downloading your transaction data from Empire Minecraft will restore your shop transactions and bonus/fee tallies, but all payment transaction assignments and inventory data will not be restored." +
		"</div></html>");
		//@formatter:on

		////////////////

		setLayout(new MigLayout());

		add(warningTitle, "span 2, align center, wrap");
		add(warningIcon);
		add(text, "gapleft 20, align center, wrap");
		add(resetButton, "span 2, split 2, align center");
		add(cancel);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				result = false;
				dispose();
			}
		});

		pack();
		setLocationRelativeTo(owner);
	}
}