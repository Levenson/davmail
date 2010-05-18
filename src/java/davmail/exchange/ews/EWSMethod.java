/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2010  Mickael Guessant
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package davmail.exchange.ews;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.log4j.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * EWS SOAP method.
 */
public abstract class EWSMethod extends PostMethod {
    protected static final Logger logger = Logger.getLogger(EWSMethod.class);

    public static final class BaseShape {
        private final String value;

        private BaseShape(String value) {
            this.value = value;
        }

        public void write(Writer writer) throws IOException {
            writer.write("<t:BaseShape>");
            writer.write(value);
            writer.write("</t:BaseShape>");
        }

        public static final BaseShape IdOnly = new BaseShape("IdOnly");
        public static final BaseShape Default = new BaseShape("Default");
        public static final BaseShape AllProperties = new BaseShape("AllProperties");
    }

    public static final class DistinguishedFolderId {
        private final String value;

        private DistinguishedFolderId(String value) {
            this.value = value;
        }

        public void write(Writer writer) throws IOException {
            writer.write("<t:DistinguishedFolderId Id=\"");
            writer.write(value);
            writer.write("\"/>");
        }

        public static final DistinguishedFolderId msgfolderroot = new DistinguishedFolderId("msgfolderroot");
        public static final DistinguishedFolderId inbox = new DistinguishedFolderId("inbox");
        public static final DistinguishedFolderId publicfoldersroot = new DistinguishedFolderId("publicfoldersroot");
    }

    public static final class Traversal {
        private final String value;

        private Traversal(String value) {
            this.value = value;
        }

        public void write(Writer writer) throws IOException {
            writer.write(" Traversal=\"");
            writer.write(value);
            writer.write("\"");
        }

        public static final Traversal Shallow = new Traversal("Shallow");
        public static final Traversal Deep = new Traversal("Deep");
    }

    protected Traversal traversal = null;
    protected BaseShape baseShape;
    protected DistinguishedFolderId distinguishedFolderId = DistinguishedFolderId.msgfolderroot;

    /**
     * Build EWS method
     */
    public EWSMethod() {
        super("/ews/exchange.asmx");
        setRequestEntity(new RequestEntity() {
            byte[] content;

            public boolean isRepeatable() {
                return true;
            }

            public void writeRequest(OutputStream outputStream) throws IOException {
                if (content == null) {
                    content = generateSoapEnvelope();
                }
                outputStream.write(content);
            }

            public long getContentLength() {
                if (content == null) {
                    content = generateSoapEnvelope();
                }
                return content.length;
            }

            public String getContentType() {
                return "text/xml;charset=UTF-8";
            }
        });
    }


    @Override
    public String getName() {
        return "POST";
    }

    protected void setBaseShape(BaseShape baseShape) {
        this.baseShape = baseShape;
    }

    protected void setDistinguishedFolderId(DistinguishedFolderId distinguishedFolderId) {
        this.distinguishedFolderId = distinguishedFolderId;
    }

    protected void generateShape(Writer writer) throws IOException {
        if (baseShape != null) {
            writer.write("<m:");
            writer.write(getResponseItemName());
            writer.write("Shape>");
            baseShape.write(writer);
            writer.write("</m:");
            writer.write(getResponseItemName());
            writer.write("Shape>");
        }
    }

    protected byte[] generateSoapEnvelope() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            OutputStreamWriter writer = new OutputStreamWriter(baos, "UTF-8");
            writer.write("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                    "xmlns:t=\"http://schemas.microsoft.com/exchange/services/2006/types\" " +
                    "xmlns:m=\"http://schemas.microsoft.com/exchange/services/2006/messages\">" +
                    "<soap:Body>");
            writer.write("<m:");
            writer.write(getMethodName());
            if (traversal != null) {
                traversal.write(writer);
            }
            writer.write(">");
            generateSoapBody(writer);
            writer.write("</m:");
            writer.write(getMethodName());
            writer.write(">");
            writer.write("</soap:Body>" +
                    "</soap:Envelope>");
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    protected abstract void generateSoapBody(Writer writer) throws IOException;

    /**
     * Build a new XMLInputFactory.
     *
     * @return XML input factory
     */
    public static XMLInputFactory getXmlInputFactory() {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        inputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
        return inputFactory;
    }

    class Item {
        public String id;
        public String changeKey;
        public String displayName;

        @Override
        public String toString() {
            return "id: " + id + " changeKey:" + changeKey + " displayName:" + displayName;
        }
    }

    protected List<Item> responseItems;
    protected String errorDetail;

    protected abstract String getMethodName();

    protected abstract String getResponseItemName();

    protected abstract String getResponseItemId();

    public List<Item> getResponseItems() {
        return responseItems;
    }

    protected String handleTag(XMLStreamReader reader, String localName) throws XMLStreamException {
        String result = null;
        int event = reader.getEventType();
        if (event == XMLStreamConstants.START_ELEMENT && localName.equals(reader.getLocalName())) {
            while (reader.hasNext() &&
                    !((event == XMLStreamConstants.END_ELEMENT && localName.equals(reader.getLocalName())))) {
                event = reader.next();
                if (event == XMLStreamConstants.CHARACTERS) {
                    result = reader.getText();
                }
            }
        }
        return result;
    }

    protected void handleErrors(XMLStreamReader reader) throws XMLStreamException {
        String result = handleTag(reader, "ResponseCode");
        if (result != null && !"NoError".equals(result)) {
            errorDetail = result;
        }
        result = handleTag(reader, "faultstring");
        if (result != null) {
            errorDetail = result;
        }
    }

    protected Item handleItem(XMLStreamReader reader) throws XMLStreamException {
        Item result = null;
        int event = reader.getEventType();
        if (event == XMLStreamConstants.START_ELEMENT && getResponseItemName().equals(reader.getLocalName())) {
            result = new Item();
            while (reader.hasNext() &&
                    !((event == XMLStreamConstants.END_ELEMENT && getResponseItemName().equals(reader.getLocalName())))) {
                event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && getResponseItemId().equals(reader.getLocalName())) {
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        if ("Id".equals(reader.getAttributeLocalName(i))) {
                            result.id = reader.getAttributeValue(i);
                        } else if ("ChangeKey".equals(reader.getAttributeLocalName(i))) {
                            result.changeKey = reader.getAttributeValue(i);
                        }
                    }
                } else {
                    String displayName = handleTag(reader, "DisplayName");
                    if (displayName != null) {
                        result.displayName = displayName;
                    }
                }
            }
        }
        return result;
    }

    @Override
    protected void processResponseBody(HttpState httpState, HttpConnection httpConnection) {
        Header contentTypeHeader = getResponseHeader("Content-Type");
        if (contentTypeHeader != null && "text/xml; charset=utf-8".equals(contentTypeHeader.getValue())) {
            responseItems = new ArrayList<Item>();
            XMLStreamReader reader = null;
            try {
                XMLInputFactory xmlInputFactory = getXmlInputFactory();
                reader = xmlInputFactory.createXMLStreamReader(getResponseBodyAsStream());
                while (reader.hasNext()) {
                    int event = reader.next();
                    handleErrors(reader);
                    Item item = handleItem(reader);
                    if (item != null) {
                        responseItems.add(item);
                    }
                }

            } catch (IOException e) {
                logger.error("Error while parsing soap response: " + e);
            } catch (XMLStreamException e) {
                logger.error("Error while parsing soap response: " + e);
            }
            if (errorDetail != null) {
                logger.error(errorDetail);
            }
        }
    }

}