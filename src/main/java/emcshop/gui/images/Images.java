package emcshop.gui.images;

import java.net.URL;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

import com.github.mangstadt.emc.net.EmcServer;

import emcshop.ItemIndex;
import emcshop.util.ImageCache;

/**
 * Manages the images of the application.
 * @author Michael Angstadt
 */
public class Images {
	public static final ImageIcon APP_ICON = get("app-icon.png");
	public static final ImageIcon ASSIGN = get("assign.png");
	public static final ImageIcon BACKUP_DATABASE = get("backup-database.png");
	public static final ImageIcon CHANGELOG = get("changelog.png");
	public static final ImageIcon CHAT = get("chat.png");
	public static final ImageIcon CHAT_LARGE = get("chat-large.png");
	public static final ImageIcon CLEAR = get("clear.png");
	public static final ImageIcon DELETE = get("delete.png");
	public static final ImageIcon DOWN_ARROW = get("down-arrow.png");
	public static final ImageIcon DOWNLOAD = get("download.png");
	public static final ImageIcon EMC_LOGO = get("emc-logo.png");
	public static final ImageIcon HEADER = get("header.png");
	public static final ImageIcon HELP = get("help.png");
	public static final ImageIcon LOADING = get("loading.gif");
	public static final ImageIcon LOADING_SMALL = get("loading-sml.gif");
	public static final ImageIcon REPORT_UNKNOWN_ITEMS = get("report-unknown-items.png");
	public static final ImageIcon SEARCH = get("search.png");
	public static final ImageIcon SEARCH_DATE = get("search-date.png");
	public static final ImageIcon SEARCH_ITEMS = get("search-items.png");
	public static final ImageIcon SEARCH_PLAYERS = get("search-players.png");
	public static final ImageIcon SETTINGS = get("settings.png");
	public static final ImageIcon SHOW_PROFILES = get("show-profiles.png");
	public static final ImageIcon SPLIT = get("split.png");
	public static final ImageIcon STACK = get("stack.png");
	public static final ImageIcon TOOLS = get("tools.png");
	public static final ImageIcon UNKNOWN = get("unknown.png");
	public static final ImageIcon UP_ARROW = get("up-arrow.png");
	public static final ImageIcon UPDATE = get("update.png");
	public static final ImageIcon WIPE_DATABASE = get("wipe-database.png");

	private static final ImageCache imageCache = new ImageCache();
	private static final ItemIndex itemIndex = ItemIndex.instance();

	/**
	 * Loads an image.
	 * @param path the image path
	 * @return the image or null if not found
	 */
	public static ImageIcon get(String path) {
		URL url = Images.class.getResource(path);
		return (url == null) ? null : new ImageIcon(url);
	}

	/**
	 * Gets the platform's error icon.
	 * @see http://stackoverflow.com/q/1196797/
	 * @return the error icon
	 */
	public static Icon getErrorIcon() {
		return UIManager.getIcon("OptionPane.errorIcon");
	}

	/**
	 * Gets the platform's warning icon.
	 * @see http://stackoverflow.com/q/1196797/
	 * @return the error icon
	 */
	public static Icon getWarningIcon() {
		return UIManager.getIcon("OptionPane.warningIcon");
	}

	/**
	 * Gets the "player online" icon.
	 * @param server the server that the player is logged into or null for the
	 * generic image
	 * @param size the size of the icon
	 * @return the icon
	 */
	public static ImageIcon getOnline(EmcServer server, int size) {
		String name = (server == null) ? "online.png" : "online-" + server.name().toLowerCase() + ".png";
		ImageIcon image = imageCache.get(name, size);
		if (image == null) {
			image = scale(get(name), size);
			imageCache.put(name, size, image);
		}
		return image;
	}

	/**
	 * Gets an item image.
	 * @param item the item name (e.g. "Diamond")
	 * @return the item image or an empty image if none can be found
	 */
	public static ImageIcon getItemImage(String item) {
		String imageFileName = itemIndex.getImageFileName(item);
		ImageIcon image = imageCache.get(imageFileName, 16);
		if (image == null) {
			image = get("items/" + imageFileName);
			image = (image == null) ? getItemImage("_empty") : scale(image, 16);
			imageCache.put(item, 16, image);
		}
		return image;
	}

	/**
	 * Scales an image.
	 * @param image the image to scale
	 * @param maxSize the max height/width of the image
	 * @return the scaled image or the original image if it is smaller than the
	 * max size
	 */
	public static ImageIcon scale(ImageIcon image, int maxSize) {
		int width = image.getIconWidth();
		int height = image.getIconHeight();

		if (height <= maxSize && width <= maxSize) {
			return image;
		}

		int scaledWidth, scaledHeight;
		if (width > height) {
			double ratio = (double) height / width;
			scaledWidth = maxSize;
			scaledHeight = (int) (scaledWidth * ratio);
		} else if (height > width) {
			double ratio = (double) width / height;
			scaledHeight = maxSize;
			scaledWidth = (int) (scaledHeight * ratio);
		} else {
			scaledWidth = scaledHeight = maxSize;
		}

		return new ImageIcon(image.getImage().getScaledInstance(scaledWidth, scaledHeight, java.awt.Image.SCALE_SMOOTH));
	}

	private Images() {
		//hide
	}
}
