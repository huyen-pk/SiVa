package ee.openeid.siva.validation.service.bdoc.report.qualified.builder;

import ee.openeid.siva.validation.document.report.*;
import ee.openeid.siva.validation.document.report.Error;
import eu.europa.esig.dss.validation.report.Conclusion;
import org.apache.xml.security.signature.Reference;
import org.digidoc4j.*;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.digidoc4j.impl.bdoc.BDocSignature;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


public class QualifiedReportBuilder {

    private static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String FULL_SIGNATURE_SCOPE = "FullSignatureScope";
    private static final String FULL_DOCUMENT = "Full document";
    private static final String BDOC_SIGNATURE_WARNING = "BDOC_SIGNATURE_WARNING";
    private static final String BDOC_SIGNATURE_ERROR = "BDOC_SIGNATURE_ERROR";
    private static final String XADES_FORMAT_PREFIX = "XAdES_BASELINE_";
    private static final String DSS_BASIC_INFO_NAME_ID = "NameId";
    private static final String DSS_BASIC_INFO_CONTENT = "content";
    private static final String BDOC_SIGNATURE_INFO = "BDOC_SIGNATURE_INFO";
    private static final String REPORT_INDICATION_INDETERMINATE = "INDETERMINATE";

    private Container container;
    private String documentName;
    private Date validationTime;

    public QualifiedReportBuilder(Container container, String documentName, Date validationTime) {
        this.container = container;
        this.documentName = documentName;
        this.validationTime = validationTime;
    }

    public QualifiedReport build() {
        QualifiedReport qualifiedReport = new QualifiedReport();
        qualifiedReport.setPolicy(Policy.SIVA_DEFAULT);
        qualifiedReport.setValidationTime(new SimpleDateFormat(DEFAULT_DATE_TIME_FORMAT).format(validationTime));
        qualifiedReport.setDocumentName(documentName);
        qualifiedReport.setSignaturesCount(container.getSignatures().size());
        qualifiedReport.setSignatures(createSignaturesForReport(container));
        qualifiedReport.setValidSignaturesCount(
                qualifiedReport.getSignatures()
                        .stream()
                        .filter(vd -> vd.getIndication() == SignatureValidationData.Indication.TOTAL_PASSED)
                        .collect(Collectors.toList())
                        .size());

        return qualifiedReport;
    }

    private List<SignatureValidationData> createSignaturesForReport(Container container) {
        List<String> dataFileNames = container.getDataFiles().stream().map(DataFile::getName).collect(Collectors.toList());
        return container.getSignatures().stream().map(sig -> createSignatureValidationData(sig, dataFileNames)).collect(Collectors.toList());
    }

    private SignatureValidationData createSignatureValidationData(Signature signature, List<String> dataFileNames) {
        SignatureValidationData signatureValidationData = new SignatureValidationData();
        BDocSignature bDocSignature = (BDocSignature) signature;

        signatureValidationData.setId(bDocSignature.getId());
        signatureValidationData.setSignatureFormat(getSignatureFormat(bDocSignature.getProfile()));
        signatureValidationData.setSignatureLevel(getSignatureLevel(bDocSignature));
        //TODO: throws ArrayIndexOutOfBoundsException -> fixed in next d4j version
        //signatureValidationData.setSignedBy(bDocSignature.getSigningCertificate().getSubjectName(CN));
        signatureValidationData.setErrors(getErrors(bDocSignature));
        signatureValidationData.setSignatureScopes(getSignatureScopes(bDocSignature, dataFileNames));
        signatureValidationData.setClaimedSigningTime(new SimpleDateFormat(DEFAULT_DATE_TIME_FORMAT).format(bDocSignature.getClaimedSigningTime()));
        signatureValidationData.setWarnings(getWarnings(bDocSignature));
        signatureValidationData.setInfo(getInfo(bDocSignature));
        signatureValidationData.setIndication(getIndication(bDocSignature));

        //TODO: additional validation? d4j seems to add it's own exceptions to additional validation tag, We also add them in errors - so maybe not necessary?

        return signatureValidationData;

    }

    private String getSignatureLevel(BDocSignature bDocSignature) {
        return bDocSignature.getDssValidationReport().getSimpleReport().getSignatureLevel(bDocSignature.getId()).name();
    }

    private SignatureValidationData.Indication getIndication(BDocSignature bDocSignature) {
        SignatureValidationResult validationResult = bDocSignature.validateSignature();
        if (validationResult.isValid()) {
            return SignatureValidationData.Indication.TOTAL_PASSED;
        } else if (REPORT_INDICATION_INDETERMINATE.equals(bDocSignature.getDssValidationReport().getSimpleReport().getIndication(bDocSignature.getId()))) {
            return SignatureValidationData.Indication.INDETERMINATE;
        } else {
            return SignatureValidationData.Indication.TOTAL_FAILED;
        }
    }

    private Info getInfo(BDocSignature bDocSignature) {
        Info info = new Info();
        info.setNameId(BDOC_SIGNATURE_INFO); //TODO: what's actually meant here? is it necessary?
        info.setBestSignatureTime(new SimpleDateFormat(DEFAULT_DATE_TIME_FORMAT).format(bDocSignature.getTrustedSigningTime()));
        return info;
    }

    private List<Warning> getWarnings(BDocSignature bDocSignature) {
        List<Warning> warnings = bDocSignature.getDssValidationReport().getSimpleReport().getWarnings(bDocSignature.getId())
                .stream()
                .map(this::mapDssWarning)
                .collect(Collectors.toList());

        List<String> dssWarningMessages = warnings.stream().map(Warning::getDescription).collect(Collectors.toList());

        bDocSignature.validateSignature().getWarnings()
                .stream()
                .filter(e -> dssWarningMessages.contains(e.getMessage()))
                .map(this::mapDigidoc4JWarning)
                .forEach(warnings::add);

        return warnings;
    }

    private Warning mapDssWarning(Conclusion.BasicInfo dssWarning) {
        Warning warning = new Warning();
        warning.setNameId(dssWarning.getAttributeValue(DSS_BASIC_INFO_NAME_ID));
        warning.setDescription(dssWarning.getAttributeValue(DSS_BASIC_INFO_CONTENT));
        return warning;
    }

    private Warning mapDigidoc4JWarning(DigiDoc4JException digiDoc4JException) {
        Warning warning = new Warning();
        warning.setNameId(BDOC_SIGNATURE_WARNING); //TODO: what's the actual code to use here?
        warning.setDescription(digiDoc4JException.getMessage());
        return warning;
    }

    private List<SignatureScope> getSignatureScopes(BDocSignature bDocSignature, List<String> dataFileNames) {
        return bDocSignature.getOrigin().getReferences()
                .stream()
                .filter(r -> dataFileNames.contains(r.getURI())) //filters out Signed Properties
                .map(this::mapDssReference)
                .collect(Collectors.toList());
    }

    private SignatureScope mapDssReference(Reference reference) {
        SignatureScope signatureScope = new SignatureScope();
        signatureScope.setName(reference.getURI());
        signatureScope.setScope(FULL_SIGNATURE_SCOPE);
        signatureScope.setContent(FULL_DOCUMENT);
        return signatureScope;
    }

    private List<Error> getErrors(BDocSignature bDocSignature) {
        //First get DSS errors as they have error codes
        List<Error> errors = bDocSignature.getDssValidationReport().getSimpleReport().getErrors(bDocSignature.getId())
                .stream()
                .map(this::mapDssError)
                .collect(Collectors.toList());

        List<String> dssErrorMessages = errors
                .stream()
                .map(Error::getContent)
                .collect(Collectors.toList());

        //Add additional digidoc4j errors
        bDocSignature.validateSignature().getErrors()
                .stream()
                .filter(e -> dssErrorMessages.contains(e.getMessage()))
                .map(this::mapDigidoc4JException)
                .forEach(errors::add);

        return errors;
    }

    private Error mapDssError(Conclusion.BasicInfo dssError) {
        Error error = new Error();
        error.setNameId(dssError.getAttributeValue(DSS_BASIC_INFO_NAME_ID));
        error.setContent(dssError.getAttributeValue(DSS_BASIC_INFO_CONTENT));
        return error;
    }

    private Error mapDigidoc4JException(DigiDoc4JException digiDoc4JException) {
        Error error = new Error();
        error.setNameId(BDOC_SIGNATURE_ERROR); //TODO: what's the actual code to use here?
        error.setContent(digiDoc4JException.getMessage());
        return error;
    }

    private String getSignatureFormat(SignatureProfile profile) {
        return XADES_FORMAT_PREFIX + profile.name();
    }
}
