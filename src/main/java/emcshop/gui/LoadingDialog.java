package emcshop.gui;

import java.awt.FlowLayout;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;

@SuppressWarnings("serial")
public class LoadingDialog extends JDialog {
	public LoadingDialog(final MainFrame owner, String title, String text) {
		super(owner, title, true);
		setLocationRelativeTo(owner);
		setResizable(false);
		setUndecorated(true);
		getRootPane().setWindowDecorationStyle(JRootPane.NONE);

		setLayout(new FlowLayout());

		JPanel p = new JPanel(new FlowLayout());
		p.add(new JLabel(new ImageIcon(getClass().getResource("loading.gif"))));
		p.add(new JLabel("<html><b>" + text + "</b></html>"));
		add(p, "align center");

		pack();
	}
}
