package digital.slovensko.autogram.core;

import digital.slovensko.autogram.ui.SaveFileResponder;
import eu.europa.esig.dss.asic.cades.signature.ASiCWithCAdESService;
import eu.europa.esig.dss.asic.xades.signature.ASiCWithXAdESService;
import eu.europa.esig.dss.model.CommonDocument;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.MimeType;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xades.signature.XAdESService;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


public class SigningJob {
    private final Responder responder;
    private final CommonDocument document;
    private final SigningParameters parameters;

    public SigningJob(CommonDocument document, SigningParameters parameters, Responder responder) {
        this.document = document;
        this.parameters = parameters;
        this.responder = responder;
    }

    public DSSDocument getDocument() {
        return this.document;
    }

    public SigningParameters getParameters() {
        return parameters;
    }

    public boolean isPlainText() {
        if (parameters.getTransformationOutputMimeType() != null)
            return parameters.getTransformationOutputMimeType().equals(MimeType.TEXT);

        return document.getMimeType().equals(MimeType.TEXT);
    }

    public boolean isHTML() {
        if (parameters.getTransformationOutputMimeType() != null)
            return parameters.getTransformationOutputMimeType().equals(MimeType.HTML);

        return false;
    }

    public boolean isPDF() {
        return parameters.getSignatureType() == SigningParameters.SignatureType.PADES;
    }

    public boolean isImage() {
        return document.getMimeType().equals(MimeType.JPEG) || document.getMimeType().equals(MimeType.PNG);
    }

    public String getDocumentAsPlainText() {
        if (document.getMimeType().equals(MimeType.TEXT)) {
            try {
                return new String(document.openStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return transform();
        }
    }

    private String transform() {
        // TODO probably move this logic into signing job creation
        try {
            var builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            var document = builderFactory.newDocumentBuilder().parse(new InputSource(this.document.openStream()));
            document.setXmlStandalone(true);
            var xmlSource = new DOMSource(document);
            var outputTarget = new StreamResult(new StringWriter());

            var transformer = TransformerFactory.newInstance().newTransformer(
                    new StreamSource(new ByteArrayInputStream(parameters.getTransformation().getBytes())));

            transformer.transform(xmlSource, outputTarget);

            var result = outputTarget.getWriter().toString().trim();
            return result;
        } catch (Exception e) {
            return null; // TODO
        }
    }

    public String getDocumentAsHTML() {
        return transform();
    }

    public String getDocumentAsBase64Encoded() {
        try {
            return new String(Base64.getEncoder().encode(document.openStream().readAllBytes()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SignedDocument signAndRespond(SigningKey key) {
        SignedDocument signed = switch (getParameters().getSignatureType()) {
            case ASIC_XADES ->
                    new SignedDocument(signDocumentAsAsiCWithXAdeS(key), key.getCertificate());
            case XADES -> new SignedDocument(signDocumentAsXAdeS(key), key.getCertificate());
            case ASIC_CADES -> new SignedDocument(signDocumentAsASiCWithCAdeS(key), key.getCertificate());
            case PADES -> new SignedDocument(signDocumentAsPAdeS(key), key.getCertificate());
            default -> throw new RuntimeException("Unsupported signature type: " + getParameters().getSignatureType());
        };

        responder.onDocumentSigned(signed);

        return signed;
    }

    public void onDocumentSignFailed(SigningError e) {
        responder.onDocumentSignFailed(this, e);
    }

    private DSSDocument signDocumentAsAsiCWithXAdeS(SigningKey key) {
        DSSDocument doc = getDocument();
        if (getParameters().shouldCreateDatacontainer()) {
            var transformer = XDCTransformer.newInstance(getParameters());
            doc = transformer.transform(getDocument(), XDCTransformer.Mode.IDEMPOTENT);
            doc.setMimeType(MimeType.fromMimeTypeString("application/vnd.gov.sk.xmldatacontainer+xml"));
        }

        var commonCertificateVerifier = new CommonCertificateVerifier();
        var service = new ASiCWithXAdESService(commonCertificateVerifier);
        var signatureParameters = getParameters().getASiCWithXAdESSignatureParameters();

        signatureParameters.setSigningCertificate(key.getCertificate());
        signatureParameters.setCertificateChain(key.getCertificateChain());

        var dataToSign = service.getDataToSign(doc, signatureParameters);
        var signatureValue = key.sign(dataToSign, getParameters().getDigestAlgorithm());

        return service.signDocument(doc, signatureParameters, signatureValue);
    }

    private DSSDocument signDocumentAsXAdeS(SigningKey key) {
        var commonCertificateVerifier = new CommonCertificateVerifier();
        var service = new XAdESService(commonCertificateVerifier);
        var jobParameters = getParameters();
        var signatureParameters = getParameters().getXAdESSignatureParameters();

        signatureParameters.setSigningCertificate(key.getCertificate());
        signatureParameters.setCertificateChain(key.getCertificateChain());

        var dataToSign = service.getDataToSign(getDocument(), signatureParameters);
        var signatureValue = key.sign(dataToSign, jobParameters.getDigestAlgorithm());

        return service.signDocument(getDocument(), signatureParameters, signatureValue);
    }

    private DSSDocument signDocumentAsASiCWithCAdeS(SigningKey key) {
        var commonCertificateVerifier = new CommonCertificateVerifier();
        var service = new ASiCWithCAdESService(commonCertificateVerifier);
        var jobParameters = getParameters();
        var signatureParameters = getParameters().getASiCWithCAdESSignatureParameters();

        signatureParameters.setSigningCertificate(key.getCertificate());
        signatureParameters.setCertificateChain(key.getCertificateChain());

        var dataToSign = service.getDataToSign(getDocument(), signatureParameters);
        var signatureValue = key.sign(dataToSign, jobParameters.getDigestAlgorithm());

        return service.signDocument(getDocument(), signatureParameters, signatureValue);
    }

    private DSSDocument signDocumentAsPAdeS(SigningKey key) {
        var commonCertificateVerifier = new CommonCertificateVerifier();
        var service = new PAdESService(commonCertificateVerifier);
        var jobParameters = getParameters();
        var signatureParameters = getParameters().getPAdESSignatureParameters();

        signatureParameters.setSigningCertificate(key.getCertificate());
        signatureParameters.setCertificateChain(key.getCertificateChain());

        var dataToSign = service.getDataToSign(getDocument(), signatureParameters);
        var signatureValue = key.sign(dataToSign, jobParameters.getDigestAlgorithm());

        return service.signDocument(getDocument(), signatureParameters, signatureValue);
    }


    public static SigningJob buildFromFile(File file) {
        var document = new FileDocument(file);

        SigningParameters parameters;
        var filename = file.getName();

        if (filename.endsWith(".pdf")) {
            parameters = SigningParameters.buildForPDF(filename);
        } else {
            parameters = SigningParameters.buildForASiCWithXAdES(filename);
        }

        var responder = new SaveFileResponder(file);
        return new SigningJob(document, parameters, responder);
    }
}
