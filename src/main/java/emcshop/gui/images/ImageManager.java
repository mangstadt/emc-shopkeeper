package emcshop.gui.images;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

/**
 * Manages the images of the application.
 * @author Michael Angstadt
 */
public class ImageManager {
	private static final Map<String, ImageIcon> itemImages = new HashMap<String, ImageIcon>();

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

	public static ImageIcon getItemImage(String item) {
		ImageIcon image = itemImages.get(item);
		if (image == null) {
			String fixedItem = item.toLowerCase().replace(" ", "_");
			image = getImageIcon("items/" + fixedItem + ".png");
			if (image == null) {
				image = getItemImage("_empty");
			} else {
				image = scale(image, 16, 16);
			}
			itemImages.put(item, image);
		}
		return image;
	}

	public static ImageIcon scale(String path, int width, int height) {
		return scale(getImageIcon(path), width, height);
	}

	public static ImageIcon scale(ImageIcon image, int width, int height) {
		return new ImageIcon(image.getImage().getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH));
	}

	public static ImageIcon getImageIcon(String path) {
		URL url = ImageManager.class.getResource(path);
		return (url == null) ? null : new ImageIcon(url);
	}
}
