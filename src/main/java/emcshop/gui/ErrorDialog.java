package emcshop.gui;

import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import net.miginfocom.swing.MigLayout;

import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Generic dialog for displaying uncaught exceptions.
 * @author Michael Angstadt
 */
public class ErrorDialog extends JDialog {
	private static final Logger logger = Logger.getLogger(ErrorDialog.class.getName());
	private JLabel displayText;
	private JTextArea stackTrace;
	private JButton close;

	private ErrorDialog(Window owner, String displayMessage, Throwable thrown) {
		super(owner, "Error");
		//setModal(true);
		setModalityType(ModalityType.DOCUMENT_MODAL); //go on top of all windows

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		displayText = new JLabel(displayMessage);
		stackTrace = new JTextArea(ExceptionUtils.getStackTrace(thrown));
		stackTrace.setEditable(false);
		stackTrace.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		close = new JButton("Close");
		close.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dispose();
			}
		});

		layoutWidgets();

		setSize(500, 350);
	}

	public static void show(Window owner, String displayMessage, Throwable thrown) {
		logger.log(Level.SEVERE, displayMessage, thrown);
		new ErrorDialog(owner, displayMessage, thrown).setVisible(true);
	}

	private void layoutWidgets() {
		setLayout(new MigLayout());

		add(displayText, "wrap");
		JScrollPane scroll = new JScrollPane(stackTrace);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		add(scroll, "w 470!, h 250!, align center, wrap");
		add(close, "align center");
	}
}
