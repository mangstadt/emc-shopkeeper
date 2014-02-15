package emcshop.gui;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JRootPane;

import net.miginfocom.swing.MigLayout;
import emcshop.gui.images.ImageManager;

@SuppressWarnings("serial")
public class LoadingDialog extends JDialog {
	public LoadingDialog(MainFrame owner, String title, String text) {
		super(owner, title, true);
		setResizable(false);
		setUndecorated(true);
		getRootPane().setWindowDecorationStyle(JRootPane.NONE);

		setLayout(new MigLayout());
		add(new JLabel(ImageManager.getLoading()), "split 2, align center");
		add(new JLabel("<html><b>" + text + "</b></html>"));

		pack();
		setLocationRelativeTo(owner);
	}
}
