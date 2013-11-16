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
 * Holds the stats of each potion.
 * @author Michael Angstadt
 */
public class PotionDirectory {
	private final Document xml;
	private final XPath xpath;

	/**
	 * Reads the potion information from the application's default location.
	 * @return the potion directory
	 */
	public static PotionDirectory create() {
		InputStream in = PotionDirectory.class.getResourceAsStream("potions.xml");
		try {
			return new PotionDirectory(in);
		} catch (Exception e) {
			//nothing should be thrown
			throw new RuntimeException(e);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	/**
	 * Creates a new potion directory.
	 * @param in the input stream to the XML document
	 * @throws SAXException if there was a problem parse the XML
	 * @throws IOException if there was a problem reading from the stream
	 */
	PotionDirectory(InputStream in) throws SAXException, IOException {
		DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
		fact.setIgnoringComments(true);
		fact.setIgnoringElementContentWhitespace(true);

		try {
			xml = fact.newDocumentBuilder().parse(in);
		} catch (ParserConfigurationException e) {
			//should never be thrown
			throw new RuntimeException(e);
		}

		xpath = XPathFactory.newInstance().newXPath();
	}

	/**
	 * Gets the name of a potion.
	 * @param id the potion's ID (e.g. "8233")
	 * @return the potion name (e.g. "Potion of Strength II") or null if no
	 * potion with the given ID was found
	 */
	public String getName(String id) {
		Element element = findPotionById(id);
		return (element == null) ? null : getName(element);
	}

	private String getName(Element element) {
		StringBuilder sb = new StringBuilder();

		if (isSplash(element)) {
			sb.append("Splash ");
		}

		sb.append("Potion of ").append(potionName(element));

		if (isLevel2(element)) {
			sb.append(" II");
		}

		if (isExtended(element)) {
			sb.append(" Extended");
		}

		return sb.toString();
	}

	public Map<String, String> getAllNames() {
		NodeList potionNodes;
		try {
			potionNodes = (NodeList) xpath.evaluate("/Potions//Potion", xml, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			//should never be thrown
			throw new RuntimeException(e);
		}

		Map<String, String> names = new HashMap<String, String>();
		for (int i = 0; i < potionNodes.getLength(); i++) {
			Element potion = (Element) potionNodes.item(i);

			String id = id(potion);
			String name = getName(potion);
			names.put(id, name);
		}
		return names;
	}

	private Element findPotionById(String id) {
		//Element.getElementById() does not work (see Javadocs)
		try {
			return (Element) xpath.evaluate("/Potions//Potion[@id=" + id + "]", xml, XPathConstants.NODE);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean isSplash(Element element) {
		return is(element, "splash");
	}

	private String id(Element element) {
		return element.getAttribute("id");
	}

	private String potionName(Element element) {
		Element parent = (Element) element.getParentNode();
		return parent.getNodeName().replace("-", " ");
	}

	private boolean isLevel2(Element element) {
		String value = element.getAttribute("level");
		return "2".equals(value);
	}

	private boolean isExtended(Element element) {
		return is(element, "ext");
	}

	private boolean is(Element element, String attribute) {
		String value = element.getAttribute(attribute);
		return value.isEmpty() ? false : Boolean.parseBoolean(value);
	}
}
