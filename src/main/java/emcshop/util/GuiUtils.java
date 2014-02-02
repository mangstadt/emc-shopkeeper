package emcshop.util;

import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URI;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;

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
	 * Adds a listener to a dialog, which will fire when a key is pressed.
	 * @param dialog the dialog
	 * @param key the key (see constants in {@link KeyEvent})
	 * @param listener the listener
	 */
	public static void onKeyPress(JDialog dialog, int key, ActionListener listener) {
		dialog.getRootPane().registerKeyboardAction(listener, KeyStroke.getKeyStroke(key, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
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

	private GuiUtils() {
		//hide
	}
}
