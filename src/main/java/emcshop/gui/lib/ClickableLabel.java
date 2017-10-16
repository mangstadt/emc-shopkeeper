package emcshop.gui.lib;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.Icon;
import javax.swing.JLabel;

import emcshop.util.GuiUtils;

/**
 * A label that, when clicked, will open a browser window and load a webpage.
 *
 * @author Michael Angstadt
 */
@SuppressWarnings("serial")
public class ClickableLabel extends JLabel {
    private static final Logger logger = Logger.getLogger(ClickableLabel.class.getName());
    private URI uri;

    /**
     * @param image the label image
     * @param url   the webpage URL
     */
    public ClickableLabel(Icon image, String url) {
        super(image);
        init(url);
    }

    /**
     * @param text the label text
     * @param url  the webpage URL
     */
    public ClickableLabel(String text, String url) {
        super(text);
        init(url);
    }

    private void init(String url) {
        if (!GuiUtils.canOpenWebPages()) {
            return;
        }

        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            logger.log(Level.SEVERE, "Bad URI, cannot make label clickable.", e);
            return;
        }

        //only set these things if the user's computer supports opening a browser window
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                try {
                    GuiUtils.openWebPage(uri);
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Problem opening webpage.", e);
                }
            }
        });
    }
}
