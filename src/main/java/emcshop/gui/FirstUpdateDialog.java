package emcshop.gui;

import java.awt.FlowLayout;
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

@SuppressWarnings("serial")
public class FirstUpdateDialog extends JDialog implements WindowListener {
	private boolean result;

	public static boolean show(JDialog owner) {
		FirstUpdateDialog dialog = new FirstUpdateDialog(owner);
		dialog.setVisible(true);
		return dialog.result;
	}

	public FirstUpdateDialog(JDialog owner) {
		super(owner, "First Update", true);
		setLocationRelativeTo(owner);
		setResizable(false);

		JButton continueButton = new JButton("Continue");
		continueButton.addActionListener(new ActionListener() {
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
		"<b><center>This is the first time you are updating your transactions!!</center></b><br>" +
		"If anyone buys/sells from your shop during the update, <font color=red><b>it will skew the results</b></font>.  Therefore, if you have a large transaction history, it is highly recommended that you <font color=red><b>disable move perms</b></font> on your res before starting the update.<br><br>" +
		"<center><b><font size=5><code>/res set move false</code></font></b></center><br>" +
		"It takes approximately 1 minute to process 200 pages of transactions. Click \"Continue\" when you are ready." +
		"</div></html>");
		//@formatter:on

		setLayout(new MigLayout());

		add(warningIcon, "span 1 2");
		add(text, "align center, wrap");
		JPanel p = new JPanel(new FlowLayout());
		p.add(continueButton);
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
