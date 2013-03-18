package emcshop.gui;

import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang3.exception.ExceptionUtils;

import emcshop.LogManager;

/**
 * Displays the contents of the log files.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class ShowLogDialog extends JDialog {
	private ShowLogDialog(Window owner, LogManager logManager) {
		super(owner, "Log");
		setModal(true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		//close when escape is pressed
		getRootPane().registerKeyboardAction(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dispose();
			}
		}, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

		JTextArea location = new JTextArea("Location: " + logManager.getFile().getAbsolutePath());
		location.setLineWrap(true);
		location.setWrapStyleWord(true);
		location.setEditable(false);
		location.setBackground(getBackground());

		String logText = null;
		try {
			logText = logManager.getEntireLog();
		} catch (IOException e) {
			logText = "Error getting log:\n" + ExceptionUtils.getStackTrace(e);
		}
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
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		log.setCaretPosition(log.getDocument().getLength()); //scroll to bottom
		add(scroll, "grow, w 100%, h 100%, wrap");
		add(close, "align center");

		setSize(500, 300);
	}

	public static void show(Window owner, LogManager logManager) {
		new ShowLogDialog(owner, logManager).setVisible(true);
	}
}
