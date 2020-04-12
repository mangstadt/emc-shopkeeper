package emcshop;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import emcshop.gui.images.Images;

/**
 * Resizes all of the PNG images in a given directory.
 * @author Michael Angstadt
 */
public class ResizeImages {
	private static final int size = 16;

	public static void main(String args[]) throws Exception {
		Path srcDir = getSrcDir(args);

		Path destDir = srcDir.resolve("small");
		Files.createDirectories(destDir);

		List<Path> files = Files.list(srcDir) //@formatter:off
			.filter(file -> file.getFileName().toString().endsWith(".png"))
		.collect(Collectors.toList()); //@formatter:on

		for (Path file : files) {
			ImageIcon icon = new ImageIcon(Files.readAllBytes(file));
			if (icon.getIconHeight() <= size && icon.getIconWidth() <= size) {
				return;
			}

			System.out.println(file.getFileName());

			//scale image
			icon = Images.scale(icon, size);
			Image img = icon.getImage();

			//convert to buffered image
			BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = bi.createGraphics();
			g2.drawImage(img, 0, 0, null);
			g2.dispose();

			//write scaled image to file
			Path dest = destDir.resolve(file.getFileName().toString().toLowerCase());
			try (OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				ImageIO.write(bi, "png", out);
			}
		}

		System.out.println("Done.");
	}

	private static Path getSrcDir(String[] args) {
		if (args.length > 0) {
			Path dir = Paths.get(args[0]);
			if (Files.isDirectory(dir)) {
				return dir;
			}
		}

		throw new IllegalArgumentException("First argument must be the path to the directory that contains the images.");
	}
}
