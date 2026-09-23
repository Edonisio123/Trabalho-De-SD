package br.ufc.hotelaria.extra;

import java.io.StringReader;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Representação externa XML, sem bibliotecas de terceiros (questão extra). */
public final class XmlVotacao {
    private XmlVotacao() {}

    public static String escapar(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("'", "&apos;");
    }

    public static String requisicao(String op, Map<String, String> atributos) {
        StringBuilder b = new StringBuilder("<requisicao op=\"").append(escapar(op)).append('"');
        for (var item : atributos.entrySet())
            b.append(' ').append(item.getKey()).append("=\"")
                    .append(escapar(item.getValue())).append('"');
        return b.append("/>").toString();
    }

    public static String resposta(boolean ok, String mensagem, String conteudoXml) {
        return "<resposta sucesso=\"" + ok + "\" mensagem=\"" + escapar(mensagem) + "\">"
                + conteudoXml + "</resposta>";
    }

    public static Element analisar(String xml) throws Exception {
        if (xml.length() > 60_000) throw new IllegalArgumentException("XML acima do limite");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var leitor = factory.newDocumentBuilder();
        leitor.setErrorHandler(new DefaultHandler() {
            @Override public void fatalError(SAXParseException e) throws SAXParseException { throw e; }
        });
        Document doc = leitor.parse(new InputSource(new StringReader(xml)));
        return doc.getDocumentElement();
    }
}
