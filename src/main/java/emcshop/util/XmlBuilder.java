package emcshop.util;

import java.io.StringWriter;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Helper class used to construct XML documents.
 */
public class XmlBuilder {
	private final Document document;
	private final Element root;
	private final String ns;

	public XmlBuilder(String ns, String root) {
		this.ns = ns;

		try {
			DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
			fact.setNamespaceAware(ns != null);
			DocumentBuilder db = fact.newDocumentBuilder();
			document = db.newDocument();
		} catch (ParserConfigurationException e) {
			//will probably never be thrown because we're not doing anything fancy with the configuration
			throw new RuntimeException(e);
		}

		this.root = element(root);
		document.appendChild(this.root);
	}

	public XmlBuilder(String root) {
		this(null, root);
	}

	public Document document() {
		return document;
	}

	public Element root() {
		return root;
	}

	public Element element(String localName) {
		return element(ns, localName);
	}

	public Element element(String ns, String localName) {
		if (ns == null) {
			return document.createElement(localName);
		}
		return document.createElementNS(ns, localName);
	}

	public Element element(QName qname) {
		return element(qname.getNamespaceURI(), qname.getLocalPart());
	}

	public Element append(String localName, String text) {
		return append(root, localName, text);
	}

	public Element append(Element parent, String localName) {
		return append(parent, new QName(ns, localName));
	}

	public Element append(Element parent, String localName, String text) {
		Element element = append(parent, localName);
		element.setTextContent(text);
		return element;
	}

	public Element append(Element parent, QName qname) {
		Element element = element(qname);
		parent.appendChild(element);
		return element;
	}

	public void append(Element parent, Element child) {
		Node imported = document.importNode(child, true);
		parent.appendChild(imported);
	}

	@Override
	public String toString() {
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			DOMSource source = new DOMSource(document);
			StringWriter writer = new StringWriter();
			StreamResult result = new StreamResult(writer);
			transformer.transform(source, result);
			return writer.toString();
		} catch (TransformerConfigurationException e) {
			//no complex configurations
		} catch (TransformerFactoryConfigurationError e) {
			//no complex configurations
		} catch (TransformerException e) {
			//writing to a string
		}
		return "";
	}
}