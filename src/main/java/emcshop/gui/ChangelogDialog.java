package emcshop.gui;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.io.IOUtils;

import emcshop.util.MiscUtils;

/**
 * Displays the changelog.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class ChangelogDialog extends JDialog {
	private ChangelogDialog(Window owner) {
		super(owner, "Changelog");
		setModal(true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		MiscUtils.closeOnEscapeKeyPress(this);

		String changelogText;
		try {
			changelogText = IOUtils.toString(getClass().getResourceAsStream("/emcshop/changelog.html"));
		} catch (IOException e) {
			//should never be thrown
			throw new RuntimeException(e);
		}

		JLabel changelog = new JLabel("<html><div width=470>" + changelogText + "</div></html>");

		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dispose();
			}
		});

		setLayout(new MigLayout());
		MyJScrollPane scroll = new MyJScrollPane(changelog);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		add(scroll, "grow, w 100%, h 100%, wrap");
		add(close, "align center");

		setSize(530, 400);
		setResizable(false);

		setLocationRelativeTo(owner);
	}

	public static void show(Window owner) {
		new ChangelogDialog(owner).setVisible(true);
	}
}
