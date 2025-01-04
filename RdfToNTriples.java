import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class RdfToNTriples {
    // Dynamically populated namespace map
    private Map<String, String> prefixMap;

    // Predefined essential namespaces
    private static final Map<String, String> STANDARD_NAMESPACES = new HashMap<>();
    static {
        STANDARD_NAMESPACES.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        STANDARD_NAMESPACES.put("xml", "http://www.w3.org/XML/1998/namespace");
    }

    private static class RDFNamespaces {
        static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
        static final String XML_NS = "http://www.w3.org/XML/1998/namespace";

        // Method to extract namespaces from the document
        static Map<String, String> extractNamespaces(Document document) {
            Map<String, String> namespaces = new HashMap<>(STANDARD_NAMESPACES);
            Element root = document.getDocumentElement();
            
            // Get all attributes of the root element
            NamedNodeMap attributes = root.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                String name = attribute.getNodeName();
                
                // Look for xmlns attributes
                if (name.startsWith("xmlns:")) {
                    String prefix = name.substring(6); // Remove "xmlns:"
                    String uri = attribute.getNodeValue();
                    namespaces.put(prefix, uri);
                } else if (name.equals("xmlns")) {
                    // Default namespace handling
                    namespaces.put("", attribute.getNodeValue());
                }
            }
            
            return namespaces;
        }

        static String expandIRI(Map<String, String> namespaces, String iri) {
            // If already a full URI, return as-is
            if (iri.startsWith("http://") || iri.startsWith("https://")) {
                return iri;
            }

            // Handle default namespace
            if (!iri.contains(":")) {
                String defaultNs = namespaces.get("");
                return defaultNs != null ? defaultNs + iri : iri;
            }

            // Split prefix and local name
            String[] parts = iri.split(":", 2);
            String namespace = namespaces.get(parts[0]);
            
            // Expand if namespace found, otherwise return original
            return namespace != null ? namespace + parts[1] : iri;
        }
    }

    private static class BlankNodeGenerator {
        private static int counter = 0;

        static String generateBlankNode() {
            counter++;
            return "_:b" + counter;
        }
    }

    private static class Triple {
        String subject;
        String predicate;
        String object;

        Triple(String subject, String predicate, String object) {
            this.subject = subject;
            this.predicate = predicate;
            this.object = object;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Triple triple = (Triple) o;
            return subject.equals(triple.subject) &&
                   predicate.equals(triple.predicate) &&
                   object.equals(triple.object);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subject, predicate, object);
        }

        @Override
        public String toString() {
            return subject + " " + predicate + " " + object + " .";
        }
    }

    private List<Triple> triples;
    private Map<String, String> nodeReferences;

    public RdfToNTriples() {
        triples = new ArrayList<>();
        nodeReferences = new HashMap<>();
    }

    public List<Triple> convert(Document document) {
        // Extract namespaces dynamically
        this.prefixMap = RDFNamespaces.extractNamespaces(document);
        
        triples.clear();
        nodeReferences.clear();
        BlankNodeGenerator.counter = 0;

        Element root = document.getDocumentElement();
        NodeList elements = root.getChildNodes();
        for (int i = 0; i < elements.getLength(); i++) {
            if (elements.item(i) instanceof Element) {
                processElement((Element) elements.item(i));
            }
        }

        return resolveTriples();
    }

    private String processElement(Element element) {
        String subject = getSubject(element);

        // Expand element type URI
        String typeUri = "<" + RDFNamespaces.expandIRI(prefixMap, element.getTagName()) + ">";

        if (!typeUri.equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#Description>")) {
            triples.add(new Triple(
                subject,
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                typeUri
            ));
        }

        NodeList properties = element.getChildNodes();
        for (int i = 0; i < properties.getLength(); i++) {
            if (properties.item(i) instanceof Element) {
                processProperty(subject, (Element) properties.item(i));
            }
        }

        return subject;
    }

    private String getSubject(Element element) {
        String about = element.getAttributeNS(RDFNamespaces.RDF_NS, "about");
        if (about != null && !about.isEmpty()) {
            return "<" + RDFNamespaces.expandIRI(prefixMap, about) + ">";
        }

        String nodeId = element.getAttributeNS(RDFNamespaces.RDF_NS, "nodeID");
        if (nodeId != null && !nodeId.isEmpty()) {
            nodeReferences.putIfAbsent(nodeId, BlankNodeGenerator.generateBlankNode());
            return nodeReferences.get(nodeId);
        }

        return BlankNodeGenerator.generateBlankNode();
    }

    private void processProperty(String subject, Element prop) {
        // Expand predicate URI
        String predicate = "<" + RDFNamespaces.expandIRI(prefixMap, prop.getTagName()) + ">";

        String resource = prop.getAttributeNS(RDFNamespaces.RDF_NS, "resource");
        if (resource != null && !resource.isEmpty()) {
            triples.add(new Triple(subject, predicate, "<" + RDFNamespaces.expandIRI(prefixMap, resource) + ">"));
            return;
        }

        String nodeId = prop.getAttributeNS(RDFNamespaces.RDF_NS, "nodeID");
        if (nodeId != null && !nodeId.isEmpty()) {
            nodeReferences.putIfAbsent(nodeId, BlankNodeGenerator.generateBlankNode());
            triples.add(new Triple(subject, predicate, nodeReferences.get(nodeId)));
            return;
        }

        NodeList nestedElements = prop.getChildNodes();
        for (int i = 0; i < nestedElements.getLength(); i++) {
            if (nestedElements.item(i) instanceof Element) {
                String nestedSubject = processElement((Element) nestedElements.item(i));
                triples.add(new Triple(subject, predicate, nestedSubject));
                return;
            }
        }

        String text = prop.getTextContent();
        if (text != null && !text.trim().isEmpty()) {
            String value = escapeNTriples(text.trim());

            String datatype = prop.getAttributeNS(RDFNamespaces.RDF_NS, "datatype");
            String lang = prop.getAttributeNS(RDFNamespaces.XML_NS, "lang");

            String literal;
            if (datatype != null && !datatype.isEmpty()) {
                literal = "\"" + value + "\"^^<" + RDFNamespaces.expandIRI(prefixMap, datatype) + ">";
            } else if (lang != null && !lang.isEmpty()) {
                literal = "\"" + value + "\"@" + lang;
            } else {
                literal = "\"" + value + "\"";
            }

            triples.add(new Triple(subject, predicate, literal));
        }
    }

    private List<Triple> resolveTriples() {
        Set<Triple> seen = new HashSet<>();
        List<Triple> uniqueTriples = new ArrayList<>();
        for (Triple triple : triples) {
            if (!seen.contains(triple)) {
                uniqueTriples.add(triple);
                seen.add(triple);
            }
        }
        return uniqueTriples;
    }

    private static String escapeNTriples(String value) {
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java RdfToNTriples <rdf_xml_file>");
            System.exit(1);
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new File(args[0]));

            RdfToNTriples converter = new RdfToNTriples();
            List<Triple> triples = converter.convert(document);

            for (Triple triple : triples) {
                System.out.println(triple);
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }
}