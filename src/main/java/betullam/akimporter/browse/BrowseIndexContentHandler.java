package main.java.betullam.akimporter.browse;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import ak.xmlhelper.XmlParser;
import main.java.betullam.akimporter.main.AkImporterHelper;

public class BrowseIndexContentHandler implements ContentHandler {

	private HttpSolrServer solrServer;
	private String timeStamp;
	private boolean print;
	private String recordToIndex;
	private String biIdXpath;
	private String elementContent;
	private String xmlRecord;
	private boolean isRecord = false;
	private List<Map<String, List<String>>> xmlSolrRecords = null;
	private int recordCounter = 0;
	private int NO_OF_DOCS = 500;

	//private Map<String, List<String>> record = new HashMap<String, List<String>>();
	private Map<String, List<String>> record = new HashMap<String, List<String>>();
	private String fieldType = null;
	private String controlfield = null;
	private String tag = null;
	private String ind1 = null;
	private String ind2 = null;
	private String subfield = null;
	private List<String> subfieldValues = new ArrayList<String>();
	private String code = null;

	public BrowseIndexContentHandler(HttpSolrServer solrServer, String recordToIndex, String biIdXpath, String timeStamp, boolean print) {
		this.solrServer = solrServer;
		this.recordToIndex = recordToIndex;
		this.biIdXpath = biIdXpath;
		this.timeStamp = timeStamp;
		this.print = print;
	}


	@Override
	public void startDocument() throws SAXException {
		xmlSolrRecords = new ArrayList<Map<String, List<String>>>();
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {	
		// Clear element content for fresh start
		elementContent = "";
		String startElement = "";

		if (qName.equals(recordToIndex)) {
			// Start fresh record
			isRecord = true;
			xmlRecord = "";
			xmlRecord += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

		}

		if (isRecord) {
			startElement += "<" + qName;			
			for (int i = 0; i < atts.getLength(); i++) {
				String attQName = atts.getQName(i);
				String attValue = atts.getValue(i);
				startElement += " " + attQName + "=\"" + StringEscapeUtils.escapeXml10(attValue).trim() + "\"";
			}
			startElement += ">";
			xmlRecord += startElement;
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (isRecord) {
			String endElement = "";
			endElement += StringEscapeUtils.escapeXml10(elementContent).trim();
			endElement += "</" + qName + ">";
			xmlRecord += endElement;
		}

		if (qName.equals(recordToIndex)) {
			// End of record
			recordCounter = recordCounter + 1;
			isRecord = false;

			Map<String, List<String>> xmlSolrRecord = getXmlSolrRecord(xmlRecord);
			if (xmlSolrRecord != null) {
				xmlSolrRecords.add(xmlSolrRecord);
				xmlSolrRecord = null;
			}

			// Every n-th record (= NO_OF_DOCS), add the generic XML records to Solr. Then we will empty all objects (set to "null") to save memory
			// and go on with the next n records. If there is a rest at the end of the file, do the same thing in the endDocument() method. E. g. NO_OF_DOCS 
			// is set to 100 and we have 733 records, but at this point, only 700 are indexed. The 33 remaining records will be indexed in endDocument() method.
			if (recordCounter % NO_OF_DOCS == 0) {
				//addRecordsToSolr(solrServer, xmlSolrRecords);
				xmlSolrRecords = null;
				xmlSolrRecords = new ArrayList<Map<String, List<String>>>();
			}
		}

		// Clear element content for fresh start
		elementContent = "";
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		elementContent += new String(ch, start, length).replaceAll("\\s+", " ");
	}

	@Override
	public void endDocument() throws SAXException {
		//addRecordsToSolr(solrServer, xmlSolrRecords);
		xmlSolrRecords = null;
	}


	/**
	 * Converts an XML record with the help of xPath to a Map that can be added to Solr.
	 * @param xmlRecord		String: The XML record as a String. It will be converted to a DOM document that can be queried with xPath.
	 * @return				Map&lt;String, List&lt;String&gt;&gt;: A Map that is used to add the data from the XML to Solr.
	 * 						The key (String) is the Solr fieldname, the value (List&lt;String&gt;) are the values that should be indexed into this field.
	 */
	private Map<String, List<String>> getXmlSolrRecord(String xmlRecord) {
		Map<String, List<String>> solrRecord = null;
		Document document = getDomDocument(xmlRecord);
		XmlParser xmlParser = new XmlParser();
		List<String> singleRecordId = new ArrayList<String>();

		// Get record ID
		try {
			List<String> recordIds = xmlParser.getXpathResult(document, biIdXpath, false);
			String recordId = (recordIds != null) ? recordIds.get(0) : null;
			singleRecordId.add(recordId);
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}

		// Get a Map that represents all fields of the DOM document
		solrRecord = getRecordFields(document.getDocumentElement());
		
		// Add a field for the solr record id
		solrRecord.put("id", singleRecordId);
		//AkImporterHelper.print(print, "\nsolrRecord " + solrRecord.toString());

		return solrRecord;
	}


	private Map<String, List<String>> getRecordFields(Node node) {

		if (node.getNodeType() == Node.ELEMENT_NODE) {
			fieldType = node.getNodeName();
			
			if (fieldType.equals("record")) {
				record = new HashMap<String, List<String>>();
			}

			// Get attributes
			NamedNodeMap nnM = node.getAttributes();
			if (nnM != null && nnM.getLength() > 0) {
				
				for(int a = 0; a < nnM.getLength(); a++) {
					Node attributeNode = nnM.item(a);
					if (attributeNode.getNodeType() == Node.ATTRIBUTE_NODE) {
						String attrName = attributeNode.getNodeName();
						String attrValue = attributeNode.getNodeValue();

						if (fieldType.equals("datafield")) {
							if (attrName.equals("tag")) {
								if (attrValue != null && !attrValue.isEmpty()) {
									tag = attrValue;
								} else {
									tag = "#";
								}
							}
							if (attrName.equals("ind1")) {
								if (attrValue != null && !attrValue.isEmpty() && !attrValue.equals("-")) {
									ind1 = attrValue;
								} else {
									ind1 = "#";
								}
							}
							if (attrName.equals("ind2")) {
								if (attrValue != null && !attrValue.isEmpty() && !attrValue.equals("1") && !attrValue.equals("-")) {
									ind2 = attrValue;
								} else {
									ind2 = "#";
								}
							}
						}

						if (fieldType.equals("subfield")) {
							if (attrName.equals("code")) {
								if (attrValue != null && !attrValue.isEmpty()) {
									code = attrValue;
								} else {
									code = "#";
								}
							};
						}

						if (fieldType.equals("controlfield")) {
							if (attrName.equals("tag")) {
								if (attrValue != null && !attrValue.isEmpty()) {
									tag = attrValue;
								} else {
									tag = "#";
								}
							}
						}
					}
				}

				if (fieldType.equals("subfield")) {
					subfield = tag + "$" + ind1 + ind2 + "$" + code;
					addToListInMap(record, tag, node.getTextContent()); // Add value on datafield-tag level
					addToListInMap(record, subfield, node.getTextContent()); // Add values on subfield-code level
				}

				if (fieldType.equals("controlfield")) {
					controlfield = tag;
					addToListInMap(record, controlfield, node.getTextContent()); // Add value for controlfield-tag level
				}
			}
		}

		// Repeat for child nodes:
		NodeList nodeList = node.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node currentNode = nodeList.item(i);
			if (currentNode.getNodeType() == Node.ELEMENT_NODE || currentNode.getNodeType() == Node.TEXT_NODE) {
				getRecordFields(currentNode);
			}
		}
		
		return record;
	}

	
	
	/**
	 * Add a value to the List of a HashMap&lt;String, List&lt;String&gt;&gt; if the List already exists for a given key.
	 * If not, a new one is created and added to the HashMap.
	 * 
	 * @param mapKey		The map key for which should be check if the List&lt;String&gt; already exists
	 * @param valueToAdd	The value to add to the List&lt;String&gt;
	 */
	private Map<String, List<String>> addToListInMap(Map<String, List<String>> record, String mapKey, String valueToAdd) {
		List<String> subfieldValues = record.get(mapKey);

		// Create new List<String> if none was found for the given key
	    if(subfieldValues == null) {
	    	subfieldValues = new ArrayList<String>();
	    	subfieldValues.add(valueToAdd);
	    	record.put(mapKey, subfieldValues);
	    } else {
	    	// Avoid duplicates: Add value to List<String> only if it does not already exist 
	    	if(!subfieldValues.contains(valueToAdd)) {
	    		subfieldValues.add(valueToAdd);
	    	}
	    }
	    
	    return record;
	}
	
	
	
	/**
	 * Get a DOM Document from a String representing an XML record.
	 * @param xmlRecord		String: The XML record as a String. It will be converted to a DOM document.
	 * @return				Document: A DOM Document
	 */
	private Document getDomDocument(String xmlRecord) {
		Document domDocument = null;
		try {
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilderFactory.setNamespaceAware(false); // Set namespace awareness to FALSE for MARC-XML. Otherwise, we won't get xPath results!
			DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputSource inputSource = new InputSource(new StringReader(xmlRecord));
			domDocument = documentBuilder.parse(inputSource);
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return domDocument;
	}


	/**
	 * Actually adds our data to the Solr server.
	 * @param solrServer		HttpSolrServer: The Solr server to which the data should be added
	 * @param xmlSolrRecords	List&lt;Map&lt;String, List&lt;String&gt;&gt;&gt;: A list of Maps, each representing a record that should be added to solr.
	 */
	private void addRecordsToSolr(HttpSolrServer solrServer, List<Map<String, List<String>>> xmlSolrRecords) {
		// Create a collection of all documents
		Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument>();

		for (Map<String, List<String>> xmlSolrRecord : xmlSolrRecords) {
			// Create a Solr input document
			SolrInputDocument doc = new SolrInputDocument();

			// Add fields to Solr document
			for (Entry<String, List<String>> dataField : xmlSolrRecord.entrySet()) {
				String solrFieldName = dataField.getKey();
				List<String> solrFieldValue = dataField.getValue();
				if (solrFieldValue != null && !solrFieldValue.isEmpty()) {
					doc.addField(solrFieldName, solrFieldValue);
				}
			}

			// Add the Solr document to a Solr document collection if it is not empty
			if (!doc.isEmpty()) {
				docs.add(doc);
			}
		}

		// If the Solr document collection is not empty, add the Solr documents to Solr
		if (!docs.isEmpty()) {
			// Now add the collection of documents to Solr:
			try {
				solrServer.add(docs);
			} catch (SolrServerException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Set "docs" to "null" (save memory):
			docs = null;
		}
	}



	// Unused methods from parent class
	@Override
	public void setDocumentLocator(Locator locator) {}
	@Override
	public void startPrefixMapping(String prefix, String uri) throws SAXException {}
	@Override
	public void endPrefixMapping(String prefix) throws SAXException {}
	@Override
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {}
	@Override
	public void processingInstruction(String target, String data) throws SAXException {}
	@Override
	public void skippedEntity(String name) throws SAXException {}
}