package emcshop.gui;

import static emcshop.util.GuiUtils.boldFont;

import java.awt.Color;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import emcshop.gui.images.Images;
import emcshop.util.GuiUtils;
import net.miginfocom.swing.MigLayout;

/**
 * Confirmation dialog for when the user chooses to wipe the database.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class ResetDatabaseDialog extends JDialog {
	private boolean result;

	/**
	 * Shows the dialog.
	 * @param owner the parent window
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
		boldFont(resetButton);
		resetButton.addActionListener(event -> {
			result = true;
			dispose();
		});

		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(event -> {
			result = false;
			dispose();
		});
		GuiUtils.onEscapeKeyPress(this, cancel);

		ImageIcon tnt = Images.scale(Images.get("tnt.png"), 48);

		JLabel warningTitle = new JLabel("<html><center><h2>WARNING!</h2></center></html>");

		//@formatter:off
		JLabel text = new JLabel(
		"<html><div width=500>" +
		"Resetting the database will delete <b><u>all transaction data within this profile</u></b>.  " +
		"This includes shop transactions, payment transactions, bonus/fee tallies, and shop inventory.<br><br>" +
		"Re-downloading your transaction data from Empire Minecraft will restore your shop transactions and bonus/fee tallies, but all payment transaction assignments and inventory data will be lost forever." +
		"</div></html>");
		//@formatter:on

		////////////////

		setLayout(new MigLayout());

		JPanel title = new JPanel(new MigLayout("insets 0"));
		title.add(new JLabel(tnt), "align right");
		title.add(warningTitle, "gapleft 20, gapright 20, align center");
		title.add(new JLabel(tnt));

		add(title, "align center, wrap");
		add(text, "align center, gaptop 20, wrap");
		add(resetButton, "split 2, gaptop 20, align center");
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