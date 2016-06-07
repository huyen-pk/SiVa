package ee.openeid.validation.service.bdoc;

import ee.openeid.siva.validation.document.ValidationDocument;
import ee.openeid.siva.validation.document.report.QualifiedReport;
import ee.openeid.siva.validation.exception.MalformedDocumentException;
import ee.openeid.siva.validation.exception.ValidationServiceException;
import ee.openeid.siva.validation.service.ValidationService;
import ee.openeid.validation.service.bdoc.report.BDOCQualifiedReportBuilder;
import eu.europa.esig.dss.DSSException;
import eu.europa.esig.dss.tsl.TrustedListsCertificateSource;
import org.apache.commons.lang.StringUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.digidoc4j.impl.bdoc.tsl.TSLCertificateSourceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;

@Service
public class BDOCValidationService implements ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(BDOCValidationService.class);

    private static final String CONTAINER_TYPE_DDOC = "DDOC";

    private TrustedListsCertificateSource trustedListSource;
    private Configuration configuration;

    @Override
    public QualifiedReport validateDocument(ValidationDocument validationDocument) {
        initConfiguration();
        Container container;
        try {
            container = createContainer(validationDocument);
        } catch (DigiDoc4JException | DSSException e) {
            logger.error("Unable to create container from validation document", e);
            throw new MalformedDocumentException(e);
        }
        verifyContainerTypeNotDDOC(container.getType());

        try {
            container.validate();
            Date validationTime = new Date();
            BDOCQualifiedReportBuilder reportBuilder = new BDOCQualifiedReportBuilder(container, validationDocument.getName(), validationTime);
            return reportBuilder.build();
        } catch (Exception e) {
            logger.error("Error occured during validation", e);
            throw new ValidationServiceException(getClass().getSimpleName(), e);
        }
    }

    public void initConfiguration() {
        if (configuration == null) {
            configuration = new Configuration();

            TSLCertificateSourceImpl tslCertificateSource = new TSLCertificateSourceImpl();
            trustedListSource.getCertificates().stream().forEach(certToken -> tslCertificateSource.addTSLCertificate(certToken.getCertificate()));
            configuration.setTSL(tslCertificateSource);
        }
    }

    private Container createContainer(ValidationDocument validationDocument) {
        InputStream containerInputStream = new ByteArrayInputStream(validationDocument.getBytes());
        return ContainerBuilder.
                aContainer().
                withConfiguration(configuration).
                fromStream(containerInputStream).
                build();
    }

    private void verifyContainerTypeNotDDOC(String containerType) {
        if (StringUtils.equalsIgnoreCase(containerType, CONTAINER_TYPE_DDOC)) {
            logger.error("DDOC container passed to BDOC validator");
            throw new MalformedDocumentException();
        }
    }
    /**
     * allow setting the configuration manually for testing purposes
     *
     * @param configuration
     */
    void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    @Autowired
    public void setTrustedListSource(TrustedListsCertificateSource trustedListSource) {
        this.trustedListSource = trustedListSource;
    }

}