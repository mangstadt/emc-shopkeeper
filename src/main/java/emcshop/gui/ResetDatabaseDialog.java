package emcshop.gui;

import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import net.miginfocom.swing.MigLayout;
import emcshop.gui.images.ImageManager;

/**
 * Confirmation dialog for when the user chooses to wipe the database.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class ResetDatabaseDialog extends JDialog implements WindowListener {
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
		setLocationRelativeTo(owner);
		setResizable(false);

		JButton resetButton = new JButton("Reset");
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

		//cancel when escape is pressed
		getRootPane().registerKeyboardAction(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				result = false;
				dispose();
			}
		}, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		JLabel warningIcon = new JLabel(ImageManager.getWarningIcon());

		//@formatter:off
		JLabel text = new JLabel(
		"<html><div width=600>" +
		"This will delete all transactions from your hard drive.  To get them back, you will have to download them again from EMC.<br><br>" +
		"Are you sure you want to do this?" +
		"</div></html>");
		//@formatter:on

		setLayout(new MigLayout());

		add(warningIcon, "span 1 2");
		add(text, "align center, wrap");
		JPanel p = new JPanel(new FlowLayout());
		p.add(resetButton);
		p.add(cancel);
		add(p, "align center");

		pack();
	}

	////////////////////////////////////////////////

	@Override
	public void windowActivated(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
		result = false;
		dispose();
	}

	@Override
	public void windowClosing(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowIconified(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowOpened(WindowEvent arg0) {
		//do nothing
	}
}