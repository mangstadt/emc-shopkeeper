package emcshop.gui;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.KeyStroke;

import net.miginfocom.swing.MigLayout;
import emcshop.Main;

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

		//close when escape is pressed
		getRootPane().registerKeyboardAction(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dispose();
			}
		}, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		//@formatter:off
		JLabel label = new JLabel(
		"<html><center>" +
		"<font size=4><b>EMC Shopkeeper</b></font><br>" +
		"Version " + Main.VERSION + "<br>" +
		"Copyright 2013 Michael Angstadt<br>" +
		"<u>" + Main.URL + "</u><br>" +
		"<br>" +
		"<font size=2>This program is a fan creation and is not affiliated with<br>Minecraft (copyright Mojang) or Empire Minecraft (copyright Kalland Labs).</font><br>" +
		"<br>" +
		"<i>No pigs were harmed in the making of this program.</i>" +
		"</center></html>"
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
		add(label, "wrap");
		add(close, "align center");

		pack();

		setLocationRelativeTo(owner);
	}

	public static void show(Window owner) {
		AboutDialog dialog = new AboutDialog(owner);
		dialog.setVisible(true);
	}
}