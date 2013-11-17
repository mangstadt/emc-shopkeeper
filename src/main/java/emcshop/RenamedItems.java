package emcshop;

import java.io.IOException;
import java.io.InputStream;
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
 * Contains new names for certain items. For example,
 * "Water Bottle - ¤aZombie Virus" from the transaction history is converted to
 * "Zombie Virus".
 * @author Michael Angstadt
 */
public class RenamedItems {
	private final Map<String, String> mappings = new HashMap<String, String>();

	/**
	 * Reads the item names from the application's default location.
	 * @return the item names object
	 */
	public static RenamedItems create() {
		InputStream in = RenamedItems.class.getResourceAsStream("renamed-items.xml");
		try {
			return new RenamedItems(in);
		} catch (Exception e) {
			//nothing should be thrown
			throw new RuntimeException(e);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	/**
	 * Creates a new item names DAO.
	 * @param in the input stream to the XML document
	 * @throws SAXException if there was a problem parse the XML
	 * @throws IOException if there was a problem reading from the stream
	 */
	RenamedItems(InputStream in) throws SAXException, IOException {
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

		NodeList mappingNodes;
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			mappingNodes = (NodeList) xpath.evaluate("/Mappings/Mapping", xml, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			//should never be thrown
			throw new RuntimeException(e);
		}

		for (int i = 0; i < mappingNodes.getLength(); i++) {
			Element mapping = (Element) mappingNodes.item(i);
			String from = mapping.getAttribute("from");
			String to = mapping.getAttribute("to");
			mappings.put(from, to);
		}
	}

	/**
	 * Gets the new name of an item.
	 * @param origName the original item name (e.g.
	 * "Water Bottle - ¤aZombie Virus")
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
