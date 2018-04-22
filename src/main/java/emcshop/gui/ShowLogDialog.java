package emcshop.gui;

import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

import emcshop.AppContext;
import emcshop.LogManager;
import emcshop.util.GuiUtils;
import net.miginfocom.swing.MigLayout;

/**
 * Displays the contents of the log files.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class ShowLogDialog extends JDialog {
	private ShowLogDialog(Window owner) {
		super(owner, "Log");
		setModal(true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		GuiUtils.closeOnEscapeKeyPress(this);

		LogManager logManager = AppContext.instance().get(LogManager.class);

		JTextArea location = new JTextArea("Location: " + logManager.getFile().getAbsolutePath());
		location.setLineWrap(true);
		location.setWrapStyleWord(true);
		location.setEditable(false);
		location.setBackground(getBackground());

		String logText = logManager.getEntireLog();
		JTextArea log = new JTextArea(logText);
		log.setLineWrap(true);
		log.setWrapStyleWord(true);
		log.setEditable(false);
		log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

		JButton close = new JButton("Close");
		close.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dispose();
			}
		});

		setLayout(new MigLayout());
		add(location, "w 100:100%:100%, wrap");
		JScrollPane scroll = new JScrollPane(log);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		log.setCaretPosition(log.getDocument().getLength()); //scroll to bottom
		add(scroll, "grow, w 100%, h 100%, wrap");
		add(close, "align center");

		setSize(500, 400);
		setLocationRelativeTo(owner);
	}

	public static void show(Window owner) {
		new ShowLogDialog(owner).setVisible(true);
	}
}
