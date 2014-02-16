package emcshop.util;

import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Contains GUI utility methods.
 * @author Michael Angstadt
 */
public class GuiUtils {
	/**
	 * True if the local operating system is Linux, false if not.
	 */
	public static final boolean linux = System.getProperty("os.name").toLowerCase().contains("linux");

	public static final Desktop desktop;
	static {
		Desktop d = null;
		if (Desktop.isDesktopSupported()) {
			d = Desktop.getDesktop();
			if (d != null && !d.isSupported(Desktop.Action.BROWSE)) {
				d = null;
			}
		}
		desktop = d;
	}

	/**
	 * Opens a webpage in the user's browser.
	 * @param uri the URI
	 */
	public static void openWebPage(URI uri) throws IOException {
		if (desktop == null) {
			return;
		}

		desktop.browse(uri);
	}

	/**
	 * Determines if the user's computer supports opening webpages.
	 * @return true if it can open web pages, false if not
	 */
	public static boolean canOpenWebPages() {
		return desktop != null;
	}

	/**
	 * Builds a standardized tooltip string.
	 * @param text the tooltip text
	 * @return the standardized tooltip string
	 */
	public static String toolTipText(String text) {
		text = text.replace("\n", "<br>");
		return "<html><div width=300>" + text + "</div></html>";
	}

	/**
	 * Configures a dialog to close when the escape key is pressed.
	 * @param dialog the dialog
	 */
	public static void closeOnEscapeKeyPress(final JDialog dialog) {
		onEscapeKeyPress(dialog, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dialog.dispose();
			}
		});
	}

	/**
	 * Adds a listener to a dialog, which will fire when the escape key is
	 * pressed.
	 * @param dialog the dialog
	 * @param listener the listener
	 */
	public static void onEscapeKeyPress(JDialog dialog, ActionListener listener) {
		onKeyPress(dialog, KeyEvent.VK_ESCAPE, listener);
	}

	/**
	 * Adds all the listeners of a button to a dialog, which will fire when the
	 * escape key is pressed.
	 * @param dialog the dialog
	 * @param button the button
	 */
	public static void onEscapeKeyPress(JDialog dialog, final AbstractButton button) {
		onKeyPress(dialog, KeyEvent.VK_ESCAPE, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (ActionListener listener : button.getActionListeners()) {
					listener.actionPerformed(e);
				}
			}
		});
	}

	/**
	 * Adds a listener to a dialog which is fired when the dialog window is
	 * closed.
	 * @param dialog the dialog
	 * @param listener the listener
	 */
	public static void addCloseDialogListener(JDialog dialog, final ActionListener listener) {
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				listener.actionPerformed(null);
			}
		});
	}

	/**
	 * Adds a listener to a dialog, which will fire when a key is pressed.
	 * @param dialog the dialog
	 * @param key the key (see constants in {@link KeyEvent})
	 * @param listener the listener
	 */
	public static void onKeyPress(JDialog dialog, int key, ActionListener listener) {
		dialog.getRootPane().registerKeyboardAction(listener, KeyStroke.getKeyStroke(key, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
	}

	/**
	 * Adds all the listeners of a button to a dialog, which will fire when a
	 * key is pressed.
	 * @param dialog the dialog
	 * @param key the key (see constants in {@link KeyEvent})
	 * @param button the button
	 */
	public static void onKeyPress(JDialog dialog, int key, final AbstractButton button) {
		dialog.getRootPane().registerKeyboardAction(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (ActionListener listener : button.getActionListeners()) {
					listener.actionPerformed(e);
				}
			}
		}, KeyStroke.getKeyStroke(key, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
	}

	/**
	 * Used for changing the cursor to a busy cursor.
	 * @param window the window
	 * @param busy true for a busy cursor, false for the default cursor
	 */
	public static void busyCursor(Window window, boolean busy) {
		Cursor cursor = busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor();
		window.setCursor(cursor);
	}

	/**
	 * Fires a list of events.
	 * @param listeners the events to fire
	 */
	public static void fireEvents(List<ActionListener> listeners) {
		for (ActionListener listener : listeners) {
			listener.actionPerformed(null);
		}
	}

	/**
	 * Fires a list of events using {@link SwingUtilities#invokeLater}.
	 * @param listeners the events to fire
	 */
	public static void fireEventsLater(final List<ActionListener> listeners) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				fireEvents(listeners);
			}
		});
	}

	private GuiUtils() {
		//hide
	}
}
