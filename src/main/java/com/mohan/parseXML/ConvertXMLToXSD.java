package com.mohan.parseXML;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.wiztools.commons.Charsets;
import org.wiztools.commons.StringUtil;
import org.xml.sax.SAXException;

import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;
import nu.xom.Serializer;
import nu.xom.ValidityException;

public class ConvertXMLToXSD {

	public static String XSD_NS_URI = "http://www.w3.org/2001/XMLSchema";
	public static String xsdPrefix = "xs";
	private static Document xsdDoc = null;
	public TypeInferUtil typeInferObj = new TypeInferUtil();

	public static void main(String[] args) throws ParserConfigurationException, TransformerException, IOException,
			SAXException, ValidityException, ParsingException {

		File file = new File("D:\\Users\\bramasam\\Downloads\\xml.xml");
		File outFile = new File("D:\\Users\\bramasam\\Documents\\mohan\\XSDnew.xml");
		
/*		if(args.length < 2)
		{
			System.out.println("format is java -jar input_file_name output_file_name");
		}
		
		File file = new File(args[0].toString().trim());
		File outFile = new File(args[1].toString().trim());*/
		
		ConvertXMLToXSD getDocObj = new ConvertXMLToXSD();
		xsdDoc = getDocObj.getDocument(new FileInputStream(file));
		OutputStream os = new FileOutputStream(outFile);
		getDocObj.write(os, Charsets.UTF_8);
		System.out.println("Successfully converted XML to XSD");
	}

	private Document getDocument(InputStream is) throws IOException, ValidityException, ParsingException {
		try {
			Builder parser = new Builder();
			nu.xom.Document d = parser.build(is);
			final Element rootElement = d.getRootElement();

			// output Document
			Element outRoot = new Element(xsdPrefix + ":schema", XSD_NS_URI);
			Document outDoc = new Document(outRoot);

			// setting targetNamespace
			final String nsPrefix = rootElement.getNamespacePrefix();
			final boolean hasXmlns = rootElement.getNamespaceDeclarationCount() > 0;
			if (hasXmlns || StringUtil.isNotEmpty(nsPrefix)) {
				outRoot.addAttribute(new Attribute("targetNamespace", rootElement.getNamespaceURI()));
				outRoot.addAttribute(new Attribute("elementFormDefault", "qualified"));
			}

			// adding all other namespace attributes
			for (int i = 0; i < rootElement.getNamespaceDeclarationCount(); i++) {
				final String nsPrefix2 = rootElement.getNamespacePrefix(i);
				final String nsURI = rootElement.getNamespaceURI(nsPrefix2);
				outRoot.addNamespaceDeclaration(nsPrefix2, nsURI);
			}

			// adding the root element
			Element rootElementXsd = new Element(xsdPrefix + ":element", XSD_NS_URI);
			rootElementXsd.addAttribute(new Attribute("name", rootElement.getLocalName()));
			outRoot.appendChild(rootElementXsd);

			recurseGen(rootElement, rootElementXsd);
			return outDoc;
		} finally {
			if (is != null) {
				is.close();
			}
		}
	}

	private void recurseGen(Element parent, Element parentOutElement) {
		// Adding complexType element:
		Element complexType = new Element(xsdPrefix + ":complexType", XSD_NS_URI);
		complexType.addAttribute(new Attribute("mixed", "true"));
		Element sequence = new Element(xsdPrefix + ":sequence", XSD_NS_URI);
		complexType.appendChild(sequence);
		processAttributes(parent, complexType);
		parentOutElement.appendChild(complexType);

		Elements children = parent.getChildElements();
		final Set<String> elementNamesProcessed = new HashSet<>();
		for (int i = 0; i < children.size(); i++) {
			Element e = children.get(i);
			final String localName = e.getLocalName();
			final String nsURI = e.getNamespaceURI();
			final String nsName = e.getQualifiedName();

			if (!elementNamesProcessed.contains(nsName)) { // process an element first time only
				if (e.getChildElements().size() > 0) { // Is complex type with children!
					Element element = new Element(xsdPrefix + ":element", XSD_NS_URI);
					element.addAttribute(new Attribute("name", localName));
					processOccurences(element, parent, localName, nsURI);
					recurseGen(e, element); // recurse into children:
					sequence.appendChild(element);

				} else {
					final String cnt = e.getValue();
					final String eValue = cnt == null ? null : cnt.trim();
					@SuppressWarnings("static-access")
					final String type = xsdPrefix + new TypeInferUtil().getTypeOfContent(eValue);
					Element element = new Element(xsdPrefix + ":element", XSD_NS_URI);
					element.addAttribute(new Attribute("name", localName));
					processOccurences(element, parent, localName, nsURI);

					// Attributes
					final int attrCount = e.getAttributeCount();
					if (attrCount > 0) {
						// has attributes: complex type without sequence!
						Element complexTypeCurrent = new Element(xsdPrefix + ":complexType", XSD_NS_URI);
						complexType.addAttribute(new Attribute("mixed", "true"));
						Element simpleContent = new Element(xsdPrefix + ":simpleContent", XSD_NS_URI);
						Element extension = new Element(xsdPrefix + ":extension", XSD_NS_URI);
						extension.addAttribute(new Attribute("base", type));
						processAttributes(e, extension);
						simpleContent.appendChild(extension);
						complexTypeCurrent.appendChild(simpleContent);
						element.appendChild(complexTypeCurrent);
					} else { // if no attributes, just put the type:
						element.addAttribute(new Attribute("type", type));
					}
					sequence.appendChild(element);
				}
			}
			elementNamesProcessed.add(nsName);
		}
	}

	public void write(final OutputStream os, final Charset charset) throws IOException {
		if (xsdDoc == null)
			throw new IllegalStateException("Call parse() before calling this method!");
		// Display output:
		Serializer serializer = new Serializer(os, charset.name());
		serializer.setIndent(4);
		serializer.write(xsdDoc);
	}

	private void processOccurences(final Element element, final Element parent, final String localName,
			final String nsURI) {
		if (parent.getChildElements(localName, nsURI).size() > 1) {
			element.addAttribute(new Attribute("maxOccurs", "unbounded"));
		} else {
			element.addAttribute(new Attribute("minOccurs", "0"));
		}
	}

	private void processAttributes(final Element inElement, final Element outElement) {
		for (int i = 0; i < inElement.getAttributeCount(); i++) {
			final Attribute attr = inElement.getAttribute(i);
			final String name = attr.getLocalName();
			final String value = attr.getValue();
			Element attrElement = new Element(xsdPrefix + ":attribute", XSD_NS_URI);
			attrElement.addAttribute(new Attribute("name", name));
			attrElement.addAttribute(new Attribute("type", xsdPrefix + TypeInferUtil.getTypeOfContent(value)));
			attrElement.addAttribute(new Attribute("use", "required"));
			outElement.appendChild(attrElement);
		}
	}
}
