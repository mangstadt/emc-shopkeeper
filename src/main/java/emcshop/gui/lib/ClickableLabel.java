package emcshop.gui.lib;

import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Icon;
import javax.swing.JLabel;

/**
 * A label that, when clicked, will open a browser window and load a webpage.
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class ClickableLabel extends JLabel implements MouseListener {
	private static final Logger logger = Logger.getLogger(ClickableLabel.class.getName());
	private static final Desktop desktop;
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

	private final URI uri;

	/**
	 * @param image the label image
	 * @param url the webpage URL
	 */
	public ClickableLabel(Icon image, String url) {
		super(image);

		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}

		if (desktop != null) {
			//only set these things if the user's computer supports opening a browser window
			setCursor(new Cursor(Cursor.HAND_CURSOR));
			addMouseListener(this);
		}
	}

	@Override
	public void mouseClicked(MouseEvent arg0) {
		try {
			desktop.browse(uri);
		} catch (IOException e) {
			logger.log(Level.WARNING, "Problem opening webpage.", e);
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		//empty
	}

	@Override
	public void mouseExited(MouseEvent e) {
		//empty
	}

	@Override
	public void mousePressed(MouseEvent e) {
		//empty
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		//empty
	}
}
