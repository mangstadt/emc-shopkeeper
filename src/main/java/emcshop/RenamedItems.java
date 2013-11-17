package emcshop;

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

/**
 * Contains new names for certain items, which are more human-readable. For
 * example, the Zombie Virus potion has the name "Water Bottle - �aZombie Virus"
 * on the transaction history page. This class gives this item the new name of
 * "Zombie Virus".
 * @author Michael Angstadt
 */
public class RenamedItems {
	private static RenamedItems INSTANCE;

	private final Map<String, String> mappings;

	/**
	 * Gets the singleton instance of this class.
	 * @return the singleton object
	 */
	public static synchronized RenamedItems instance() {
		if (INSTANCE == null) {
			InputStream in = RenamedItems.class.getResourceAsStream("renamed-items.xml");
			try {
				INSTANCE = new RenamedItems(in);
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
	RenamedItems(InputStream in) throws SAXException, IOException {
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

		//get XML elements
		NodeList mappingNodes;
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			mappingNodes = (NodeList) xpath.evaluate("/Mappings/Mapping", xml, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			//should never be thrown
			throw new RuntimeException(e);
		}

		//add mappings to map
		Map<String, String> map = new HashMap<String, String>();
		for (int i = 0; i < mappingNodes.getLength(); i++) {
			Element mapping = (Element) mappingNodes.item(i);
			String from = mapping.getAttribute("from");
			String to = mapping.getAttribute("to");
			map.put(from, to);
		}

		mappings = Collections.unmodifiableMap(map);
	}

	/**
	 * Gets the new name of an item.
	 * @param origName the original item name (e.g.
	 * "Water Bottle - �aZombie Virus")
	 * @return the new name (e.g. "Zombie Virus") or the original name if no
	 * mapping exists
	 */
	public String getSanitizedName(String origName) {
		String newName = mappings.get(origName);
		return (newName == null) ? origName : newName;
	}

	/**
	 * Gets all the name mappings.
	 * @return the name mappings
	 */
	public Map<String, String> getMappings() {
		return mappings;
	}
}
