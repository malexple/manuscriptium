package ru.mcs.markdown.reference;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Подмножество CSL для author-date citation layout; неизвестные макросы не меняют исходный Markdown. */
record CslStyle(String prefix, String suffix, String delimiter, boolean yearFirst) {

    static final CslStyle DEFAULT = new CslStyle("(", ")", "; ", false);

    static CslStyle parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            NodeList citations = document.getElementsByTagName("citation");
            if (citations.getLength() == 0) {
                return DEFAULT;
            }
            Element citation = (Element) citations.item(0);
            NodeList children = citation.getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                Node node = children.item(index);
                if (node instanceof Element layout && layout.getTagName().equals("layout")) {
                    String source = source(layout);
                    int date = source.indexOf("<date");
                    int names = source.indexOf("<names");
                    return new CslStyle(attribute(layout, "prefix", "("), attribute(layout, "suffix", ")"),
                            attribute(layout, "delimiter", "; "), date >= 0 && (names < 0 || date < names));
                }
            }
        } catch (Exception ignored) {
            // Повреждённый CSL не должен блокировать открытие заметки.
        }
        return DEFAULT;
    }

    private static String source(Element element) {
        try {
            javax.xml.transform.Transformer transformer = javax.xml.transform.TransformerFactory.newInstance()
                    .newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            java.io.StringWriter result = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(element),
                    new javax.xml.transform.stream.StreamResult(result));
            return result.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String attribute(Element element, String name, String fallback) {
        return element.hasAttribute(name) ? element.getAttribute(name) : fallback;
    }
}
