package emcshop;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

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
			System.out.print(file.getFileName() + " ... ");

			Path dest = destDir.resolve(file.getFileName().toString().toLowerCase());

			ImageIcon icon = new ImageIcon(Files.readAllBytes(file));
			int iconHeight = icon.getIconHeight();
			int iconWidth = icon.getIconWidth();
			if (iconHeight == -1 || iconWidth == -1) {
				throw new IllegalArgumentException("Java cannot read image \"" + file.getFileName() + "\". This image may have been downloaded directly from the Wiki. Try opening the image in Paint.net and re-saving it.");
			}
			if (iconHeight <= size && iconWidth <= size) {
				Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
				System.out.println("resize not needed");
				continue;
			}

			//scale image
			icon = scaleImage(icon, size);
			Image img = icon.getImage();

			//convert to buffered image
			BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = bi.createGraphics();
			g2.drawImage(img, 0, 0, null);
			g2.dispose();

			//write scaled image to file
			try (OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				ImageIO.write(bi, "png", out);
			}
			System.out.println("resized");
		}

		System.out.println("Done.");
	}

	public static ImageIcon scaleImage(ImageIcon image, int maxSize) {
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
