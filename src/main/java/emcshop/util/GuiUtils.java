package emcshop.util;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Contains GUI utility methods.
 * @author Michael Angstadt
 */
public final class GuiUtils {
	/**
	 * True if the local operating system is Linux, false if not.
	 */
	public static final boolean linux = System.getProperty("os.name").toLowerCase().contains("linux");

	//this class should not be created in a static init block because it causes AWT to initialize when the CLI is run
	private static Desktop desktop;
	private static boolean desktopCreated = false;

	/**
	 * Opens a webpage in the user's default browser.
	 * @param uri the webpage address
	 * @throws IOException if there's a problem opening the web page
	 */
	public static void openWebPage(URI uri) throws IOException {
		if (!canOpenWebPages()) {
			return;
		}

		desktop.browse(uri);
	}

	/**
	 * Opens a file in the default program for that file type.
	 * @param file the file to open
	 * @throws IOException if there's a problem opening the file
	 */
	public static void openFile(Path file) throws IOException {
		if (!canOpenFiles()) {
			return;
		}

		desktop.open(file.toFile());
	}

	/**
	 * Determines if the user's JVM supports opening webpages.
	 * @return true if it can open web pages, false if not
	 */
	public static boolean canOpenWebPages() {
		createDesktop();
		return desktop != null && desktop.isSupported(Desktop.Action.BROWSE);
	}

	/**
	 * Determines if the user's JVM supports opening files in their native
	 * applications.
	 * @return true if it can open files, false if not
	 */
	public static boolean canOpenFiles() {
		createDesktop();
		return desktop != null && desktop.isSupported(Desktop.Action.OPEN);
	}

	private static void createDesktop() {
		if (desktopCreated) {
			return;
		}

		desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
		desktopCreated = true;
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
	public static void closeOnEscapeKeyPress(JDialog dialog) {
		onEscapeKeyPress(dialog, event -> dialog.dispose());
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
	public static void onEscapeKeyPress(JDialog dialog, AbstractButton button) {
		onKeyPress(dialog, KeyEvent.VK_ESCAPE, event -> {
			for (ActionListener listener : button.getActionListeners()) {
				listener.actionPerformed(event);
			}
		});
	}

	/**
	 * Adds a listener to a dialog which is fired when the dialog window is
	 * closed.
	 * @param dialog the dialog
	 * @param listener the listener
	 */
	public static void addCloseDialogListener(JDialog dialog, ActionListener listener) {
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
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
	public static void onKeyPress(JDialog dialog, int key, AbstractButton button) {
		dialog.getRootPane().registerKeyboardAction(event -> {
			for (ActionListener listener : button.getActionListeners()) {
				listener.actionPerformed(event);
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
		fireEvents(listeners, null);
	}

	/**
	 * Fires a list of events.
	 * @param listeners the events to fire
	 * @param event the event to fire
	 */
	public static void fireEvents(List<ActionListener> listeners, ActionEvent event) {
		for (ActionListener listener : listeners) {
			listener.actionPerformed(event);
		}
	}

	/**
	 * Fires a list of events using {@link SwingUtilities#invokeLater}.
	 * @param listeners the events to fire
	 */
	public static void fireEventsLater(List<ActionListener> listeners) {
		SwingUtilities.invokeLater(() -> fireEvents(listeners));
	}

	/**
	 * Copies text to the clipboard.
	 * @param text the text to copy to the clipboard
	 */
	public static void copyToClipboard(String text) {
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		StringSelection stringSelection = new StringSelection(text);
		clipboard.setContents(stringSelection, stringSelection);
	}

	/**
	 * "Shakes" a dialog box, like Mac OSX does when you enter a wrong password.
	 * @param dialog the dialog to shake
	 */
	public static void shake(final JDialog dialog) {
		final int SHAKE_DISTANCE = 20;
		final double SHAKE_CYCLE = 50;
		final int SHAKE_DURATION = 200;
		final int SHAKE_UPDATE = 5;
		final double TWO_PI = Math.PI * 2.0;

		final Point naturalLocation = dialog.getLocation();
		final long startTime = System.currentTimeMillis();

		final Timer shakeTimer = new Timer(SHAKE_UPDATE, null);
		shakeTimer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// calculate elapsed time
				long elapsed = System.currentTimeMillis() - startTime;
				// use sin to calculate an x-offset
				double waveOffset = (elapsed % SHAKE_CYCLE) / SHAKE_CYCLE;
				double angle = waveOffset * TWO_PI;

				// offset the x-location by an amount 
				// proportional to the sine, up to
				// shake_distance
				int shakenX = (int) ((Math.sin(angle) * SHAKE_DISTANCE) + naturalLocation.x);
				dialog.setLocation(shakenX, naturalLocation.y);
				dialog.repaint();

				// should we stop timer?
				if (elapsed >= SHAKE_DURATION) {
					shakeTimer.stop();
					dialog.setLocation(naturalLocation);
					dialog.repaint();
				}
			}
		});
		shakeTimer.start();
	}

	/**
	 * Shrinks the font of a component.
	 * @param component the component
	 */
	public static void shrinkFont(Component component) {
		Font font = component.getFont();
		if (font == null) {
			return;
		}

		Font smaller = new Font(font.getName(), font.getStyle(), font.getSize() - 2);
		component.setFont(smaller);
	}

	/**
	 * Adds bold styling to the font of a component.
	 * @param component the component
	 */
	public static void boldFont(Component component) {
		Font font = component.getFont();
		if (font == null) {
			return;
		}

		Font bold = new Font(font.getName(), Font.BOLD, font.getSize());
		component.setFont(bold);
	}

	private GuiUtils() {
		//hide
	}
}
