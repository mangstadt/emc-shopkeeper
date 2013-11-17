package emcshop;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

/**
 * Contains new names for certain items, which are more human-readable. For
 * example, the Zombie Virus potion has the name
 * "Water Bottle - ï¿½aZombie Virus" on the transaction history page. This class
 * gives this item the new name of "Zombie Virus".
 * @author Michael Angstadt
 */
public class ItemIndex {
	private static ItemIndex INSTANCE;

	private final Map<String, String> emcNameToDisplayName;
	private final Map<String, String> itemImages;
	private final List<String> itemNames;

	/**
	 * Gets the singleton instance of this class.
	 * @return the singleton object
	 */
	public static synchronized ItemIndex instance() {
		if (INSTANCE == null) {
			InputStream in = ItemIndex.class.getResourceAsStream("items.xml");
			try {
				INSTANCE = new ItemIndex(in);
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
	 * Creates a new renamed items object.
	 * @param in the input stream to the XML document
	 * @throws SAXException if there was a problem parse the XML
	 * @throws IOException if there was a problem reading from the stream
	 */
	ItemIndex(InputStream in) throws SAXException, IOException {
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

		NodeList itemNodes;
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			itemNodes = (NodeList) xpath.evaluate("/Items/Item", xml, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			//should never be thrown
			throw new RuntimeException(e);
		}

		Map<String, String> emcNameToDisplayName = new HashMap<String, String>();
		Map<String, String> itemImages = new HashMap<String, String>();
		List<String> itemNames = new ArrayList<String>();
		for (int i = 0; i < itemNodes.getLength(); i++) {
			Element itemNode = (Element) itemNodes.item(i);
			String name = itemNode.getAttribute("name");

			itemNames.add(name);

			String emcNames = itemNode.getAttribute("emcNames");
			if (!emcNames.isEmpty()) {
				for (String emcName : emcNames.split(",")) {
					emcNameToDisplayName.put(emcName, name);
				}
			}

			String image = itemNode.getAttribute("image");
			if (!image.isEmpty()) {
				itemImages.put(name, image);
			}
		}

		this.emcNameToDisplayName = Collections.unmodifiableMap(emcNameToDisplayName);
		this.itemImages = Collections.unmodifiableMap(itemImages);
		this.itemNames = Collections.unmodifiableList(itemNames);
	}

	/**
	 * Gets the display name of an item.
	 * @param emcName the name from the transaction history (e.g.
	 * "Water Bottle - ¤aZombie Virus")
	 * @return the display name (e.g. "Zombie Virus") or the transaction history
	 * name if no mapping exists
	 */
	public String getDisplayName(String emcName) {
		String displayName = emcNameToDisplayName.get(emcName);
		return (displayName == null) ? emcName : displayName;
	}

	/**
	 * Gets the image file name of an item
	 * @param displayName the item's display name
	 * @return the item's image file name
	 */
	public String getImageFileName(String displayName) {
		String image = itemImages.get(displayName);
		if (image == null) {
			image = displayName.toLowerCase().replace(" ", "_") + ".png";
		}
		return image;
	}

	/**
	 * Gets the EMC-to-display name mappings (only includes the mappings that
	 * differ from the default).
	 * @return the mappings
	 */
	public Map<String, String> getEmcNameToDisplayNameMapping() {
		return emcNameToDisplayName;
	}

	/**
	 * Gets the item image file names (only includes the images that differ from
	 * the default).
	 * @return the image file names
	 */
	public Map<String, String> getItemImages() {
		return itemImages;
	}

	/**
	 * Gets all the item names.
	 * @return the item names
	 */
	public List<String> getItemNames() {
		return itemNames;
	}
}
