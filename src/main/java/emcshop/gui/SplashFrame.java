package emcshop.gui;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;

import net.miginfocom.swing.MigLayout;
import emcshop.EMCShopkeeper;
import emcshop.gui.images.Images;

/**
 * The splash screen that is shown on startup.
 */
@SuppressWarnings("serial")
public class SplashFrame extends JFrame {
	private final JLabel message;

	public SplashFrame() {
		setResizable(false);
		setUndecorated(true);
		getRootPane().setWindowDecorationStyle(JRootPane.NONE);
		getRootPane().setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		setIconImage(Images.APP_ICON.getImage());
		setTitle("EMC Shopkeeper v" + EMCShopkeeper.VERSION);

		setLayout(new MigLayout("insets 5"));

		add(new JLabel(Images.HEADER), "align center, wrap");

		message = new JLabel(" ");
		message.setHorizontalAlignment(SwingConstants.CENTER);
		add(message, "w 100%");

		pack();
		setLocationRelativeTo(null);
	}

	/**
	 * Sets the text on the splash screen.
	 * @param message the text
	 */
	public void setMessage(final String message) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				SplashFrame.this.message.setText(message);
			}
		});
	}
}
