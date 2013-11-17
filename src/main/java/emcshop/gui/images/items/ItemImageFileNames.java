package emcshop.gui.images.items;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import emcshop.RenamedItems;

/**
 * Determines the file name of an item's image.
 * @author Michael Angstadt
 */
public class ItemImageFileNames {
	private static ItemImageFileNames INSTANCE;

	private final Map<String, String> fileNames;

	/**
	 * Gets the singleton instance of this class.
	 * @return the singleton object
	 */
	public static synchronized ItemImageFileNames instance() {
		if (INSTANCE == null) {
			InputStream in = RenamedItems.class.getResourceAsStream("item-file-names.xml");
			try {
				INSTANCE = new ItemImageFileNames(in);
			} catch (Exception e) {
				//nothing should be thrown
				throw new RuntimeException(e);
			} finally {
				IOUtils.closeQuietly(in);
			}
		}

		return INSTANCE;
	}

	/**
	 * Creates a new object.
	 * @param in the input stream to the XML document
	 * @throws SAXException if there was a problem parse the XML
	 * @throws IOException if there was a problem reading from the stream
	 */
	ItemImageFileNames(InputStream in) throws SAXException, IOException {
		//parse XML document
		Document xml;
		try {
			DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
			fact.setIgnoringComments(true);
			fact.setIgnoringElementContentWhitespace(true);

			xml = fact.newDocumentBuilder().parse(in);
		} catch (ParserConfigurationException e) {
			//should never be thrown
			throw new RuntimeException(e);
		}

		NodeList imageNodes;
		XPath xpath = XPathFactory.newInstance().newXPath();
		try {
			imageNodes = (NodeList) xpath.evaluate("/Images/Image", xml, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			//should never be thrown
			throw new RuntimeException(e);
		}

		Map<String, String> map = new HashMap<String, String>();
		for (int i = 0; i < imageNodes.getLength(); i++) {
			Element imageNode = (Element) imageNodes.item(i);
			String fileName = imageNode.getAttribute("name");

			NodeList itemNodes;
			try {
				itemNodes = (NodeList) xpath.evaluate("Item", imageNode, XPathConstants.NODESET);
			} catch (XPathExpressionException e) {
				//should never be thrown
				throw new RuntimeException(e);
			}

			for (int j = 0; j < itemNodes.getLength(); j++) {
				Element itemNode = (Element) itemNodes.item(j);
				String itemName = itemNode.getAttribute("name");
				map.put(itemName, fileName);
			}
		}

		fileNames = Collections.unmodifiableMap(map);
	}

	/**
	 * Gets the file name of an item's image.
	 * @param itemName the item name (e.g. "Diamond")
	 * @return the item's file name (e.g. "diamond.png")
	 */
	public String getFileName(String itemName) {
		String fileName = fileNames.get(itemName);
		if (fileName != null) {
			return fileName;
		}

		return itemName.toLowerCase().replace(" ", "_") + ".png";
	}
}
