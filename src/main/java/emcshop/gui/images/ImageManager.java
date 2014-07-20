package emcshop.gui.images;

import java.net.URL;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

import emcshop.ItemIndex;
import emcshop.scraper.EmcServer;
import emcshop.util.ImageCache;

/**
 * Manages the images of the application.
 * @author Michael Angstadt
 */
public class ImageManager {
	private static final ImageCache imageCache = new ImageCache();
	private static final ItemIndex itemIndex = ItemIndex.instance();

	public static Icon getErrorIcon() {
		//http://stackoverflow.com/questions/1196797/where-are-these-error-and-warning-icons-as-a-java-resource
		return UIManager.getIcon("OptionPane.errorIcon");
	}

	public static Icon getWarningIcon() {
		return UIManager.getIcon("OptionPane.warningIcon");
	}

	public static ImageIcon getLoading() {
		return getImageIcon("loading.gif");
	}

	public static ImageIcon getLoadingSmall() {
		return getImageIcon("loading-sml.gif");
	}

	public static ImageIcon getUpdate() {
		return getImageIcon("update.png");
	}

	public static ImageIcon getSearch() {
		return getImageIcon("search.png");
	}

	public static ImageIcon getAppIcon() {
		return getImageIcon("app-icon.png");
	}

	public static ImageIcon getHelpIcon() {
		return getImageIcon("help.png");
	}

	public static ImageIcon getClearIcon() {
		return getImageIcon("clear.png");
	}

	public static ImageIcon getUnknown() {
		return getImageIcon("unknown.png");
	}

	public static ImageIcon getEmcLogo() {
		return getImageIcon("emc-logo.png");
	}

	/**
	 * Gets the "online" icon.
	 * @param server the server that the player is logged into or null for the
	 * generic image
	 * @param size the size of the icon
	 * @return the icon
	 */
	public static ImageIcon getOnline(EmcServer server, int size) {
		String name = (server == null) ? "online.png" : "online-" + server.name().toLowerCase() + ".png";
		ImageIcon image = imageCache.get(name, size);
		if (image == null) {
			image = scale(name, size);
			imageCache.put(name, size, image);
		}
		return image;
	}

	/**
	 * Gets an item image.
	 * @param item the item name
	 * @return the item image or an empty image if none can be found
	 */
	public static ImageIcon getItemImage(String item) {
		String imageFileName = itemIndex.getImageFileName(item);
		ImageIcon image = imageCache.get(imageFileName, 16);
		if (image == null) {
			image = getImageIcon("items/" + imageFileName);
			if (image == null) {
				image = getItemImage("_empty");
			} else {
				image = scale(image, 16);
			}
			imageCache.put(item, 16, image);
		}
		return image;
	}

	/**
	 * Scales an image.
	 * @param path the classpath to the image
	 * @param maxSize the max height/width of the image
	 * @return the scaled image or the original image if it is smaller than the
	 * max size
	 */
	public static ImageIcon scale(String path, int maxSize) {
		return scale(getImageIcon(path), maxSize);
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

	/**
	 * Gets an image.
	 * @param path the classpath to the image
	 * @return the image or null if not found
	 */
	public static ImageIcon getImageIcon(String path) {
		URL url = ImageManager.class.getResource(path);
		return (url == null) ? null : new ImageIcon(url);
	}

	private ImageManager() {
		//hide
	}
}
