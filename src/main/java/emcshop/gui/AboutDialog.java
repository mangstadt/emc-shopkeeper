package emcshop.gui;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;

import net.miginfocom.swing.MigLayout;
import emcshop.Main;
import emcshop.gui.images.ImageManager;
import emcshop.gui.lib.ClickableLabel;
import emcshop.util.GuiUtils;

/**
 * Displays an about dialog.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class AboutDialog extends JDialog {
	protected AboutDialog(Window owner) {
		super(owner, "About");
		setModal(true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setResizable(false);
		GuiUtils.closeOnEscapeKeyPress(this);

		//@formatter:off
		JLabel label1 = new JLabel(
		"<html>" +
		"<font size=4><b>EMC Shopkeeper</b></font><br>" +
		"by Michael Angstadt (shavingfoam)<br>" +
		"Version: " + Main.VERSION + "<br>" +
		"</html>"
		);
		
		String forumUrl = "http://empire.us/threads/shop-statistics.22507/";
		ClickableLabel label2 = new ClickableLabel(
		"<html><center>" +
		"<u>" + forumUrl + "</u>" +
		"</center></html>",
		forumUrl);

		ClickableLabel label3 = new ClickableLabel(
		"<html><center>" +
		"<u>" + Main.URL + "</u>" +
		"</center></html>",
		Main.URL);

		JLabel label4 = new JLabel(
		"<html>" +
		"<br>" +
		"<font size=2>Copyright 2013 Michael Angstadt.  This program is a fan creation and is not affiliated with<br>Minecraft (copyright Mojang) or Empire Minecraft (copyright Kalland Labs).<br></font>" +
		"<br>" +
		"<center><i>No pigs were harmed in the making of this program.</i></center>" +
		"</html>"
		);
		//@formatter:on

		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dispose();
			}
		});

		setLayout(new MigLayout());
		add(label1, "split 2, w 100%");
		add(new JLabel(ImageManager.getImageIcon("creeper.png")), "wrap");

		add(new JLabel("Forum thread:"), "split 2");
		add(label2, "wrap");

		add(new JLabel("Source code:"), "split 2");
		add(label3, "wrap");

		add(label4, "wrap");
		add(close, "align center");

		pack();

		setLocationRelativeTo(owner);
	}

	public static void show(Window owner) {
		AboutDialog dialog = new AboutDialog(owner);
		dialog.setVisible(true);
	}
}