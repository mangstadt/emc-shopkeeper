package emcshop.gui;

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class UpdateDialog extends JDialog implements WindowListener {
	private JButton cancel;
	private JLabel transactions;
	private JLabel timeElapsed;
	private Thread timeThread;

	public UpdateDialog(Frame owner) {
		super(owner, "Updating Transactions", true);
		setLocationRelativeTo(owner);

		createWidgets();
		layoutWidgets();
		setSize(200, 170);
		addWindowListener(this);

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		timeThread = new Thread() {
			@Override
			public void run() {
				int time = 0;
				NumberFormat nf = new DecimalFormat("00");
				while (isVisible()) {
					int minutes = time / 60;
					int seconds = time % 60;
					timeElapsed.setText(nf.format(minutes) + ":" + nf.format(seconds));

					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						break;
					}
					time++;
				}
			}
		};
	}

	private void createWidgets() {
		cancel = new JButton("Cancel");
		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dispose();
			}
		});

		transactions = new JLabel("0");
		timeElapsed = new JLabel();
	}

	private void layoutWidgets() {
		setLayout(new MigLayout("w 100%!"));

		JPanel p = new JPanel(new FlowLayout());
		JLabel l = new JLabel(new ImageIcon(getClass().getResource("loading.gif")));
		p.add(l);
		p.add(new JLabel("<html><b>Updating...</b></html>"));
		add(p, "span 2, align center, wrap");

		add(new JLabel("Transactions found:"));
		add(transactions, "wrap");

		add(new JLabel("Time elapsed:"));
		add(timeElapsed, "wrap");

		add(cancel, "span 2, align center");
	}

	////////////////////////////////

	@Override
	public void windowActivated(WindowEvent arg0) {
		//do nothing
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
		//do nothing
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
		timeThread.start();
	}
}
