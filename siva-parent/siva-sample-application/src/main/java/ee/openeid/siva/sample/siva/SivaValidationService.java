package ee.openeid.siva.sample.siva;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@Service
public class SivaValidationService {
    private static final String FILENAME_EXTENSION_SEPARATOR = ".";
    private static final int GENERIC_ERROR_CODE = 101;

    private String sivaBaseUrl;
    private RestTemplate restTemplate;
    private SivaValidationServiceErrorHandler errorHandler;

    public String validateDocument(final File file) throws IOException {
        if (file == null) {
            throw new IOException("Invalid file object given");
        }

        final String base64EncodedFile = Base64.encodeBase64String(FileUtils.readFileToByteArray(file));

        final ValidationRequest validationRequest = new ValidationRequest();
        validationRequest.setDocument(base64EncodedFile);

        final String filename = file.getName();
        validationRequest.setFilename(filename);
        setValidationDocumentType(validationRequest, filename);

        try {
            restTemplate.setErrorHandler(errorHandler);
            return restTemplate.postForObject(sivaBaseUrl, validationRequest, String.class);
        } catch (ResourceAccessException ce) {
            String errorMessage = "Connection to web service failed. Make sure You have configured SiVa web service correctly";
            return new ObjectMapper().writer().writeValueAsString(new ServiceError(GENERIC_ERROR_CODE, errorMessage));
        }

    }

    private static void setValidationDocumentType(final ValidationRequest validationRequest, final String filename) {
        final String uploadedFileExtension = filename.substring(filename.lastIndexOf(FILENAME_EXTENSION_SEPARATOR) + 1);
        validationRequest.setDocumentType(parseFileExtension(uploadedFileExtension));
    }

    private static FileType parseFileExtension(final String fileExtension) {
        return Arrays.stream(FileType.values())
                .filter(fileType -> fileType.name().equalsIgnoreCase(fileExtension))
                .findFirst()
                .orElse(null);

    }

    @Value("${siva.serviceUrl}")
    public void setSivaBaseUrl(final String sivaBaseUrl) {
        this.sivaBaseUrl = sivaBaseUrl;
    }

    @Autowired
    public void setRestTemplate(final RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Autowired
    public void setErrorHandler(final SivaValidationServiceErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }
}