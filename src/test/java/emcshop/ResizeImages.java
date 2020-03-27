package emcshop;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileFilter;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import emcshop.gui.images.Images;

public class ResizeImages {
	private static final int size = 16;

	public static void main(String args[]) throws Exception {
		File dir = new File("path/to/folder");
		for (File file : dir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				String name = file.getName();
				return name.endsWith(".png") && !name.equals("_empty.png");
			}
		})) {
			ImageIcon icon = new ImageIcon(file.toURI().toURL());
			if (icon.getIconHeight() <= size && icon.getIconWidth() <= size) {
				continue;
			}

			System.out.println(file.getName());

			//scale image
			icon = Images.scale(icon, size);
			Image img = icon.getImage();

			//save to file
			BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = bi.createGraphics();
			g2.drawImage(img, 0, 0, null);
			g2.dispose();

			File destFolder = new File(file.getParent(), "small");
			destFolder.mkdir();
			File dest = new File(destFolder, file.getName().toLowerCase());
			ImageIO.write(bi, "png", dest);
		}
		System.out.println("Done.");
	}
}
