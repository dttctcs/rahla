package rahla.smarturl.minio;

import io.minio.MinioClient;
import lombok.extern.log4j.Log4j2;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.url.AbstractURLStreamHandlerService;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import rahla.smarturl.SmartURLConnection;
import rahla.smarturl.SmartURLStreamHandlerService;

import java.io.IOException;
import java.net.URL;

@Component(
        configurationPid =
                SmartURLStreamHandlerService.BASE_PID + MinioURLStreamHandlerService.CONFIG_PID,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true,
        service = {SmartURLStreamHandlerService.class, URLStreamHandlerService.class})
@Log4j2
public final class MinioURLStreamHandlerService extends AbstractURLStreamHandlerService
        implements SmartURLStreamHandlerService, URLStreamHandlerService {

    public static final String CONFIG_PID = "minio";
    public static final String ENDPOINT_PROP = "endpoint";
    public static final String ACCESS_KEY_PROP = "access.key";
    public static final String SECRET_KEY_PROP = "secret.key";

    private MinioClient minioClient;
    private String protocol;

    private String endpoint;

    @Activate
    public void activate(ComponentContext cc) {
        protocol = (String) cc.getProperties().get(URLConstants.URL_HANDLER_PROTOCOL);
        endpoint = (String) cc.getProperties().get(ENDPOINT_PROP);

        String accessKey = (String) cc.getProperties().get(ACCESS_KEY_PROP);
        String secretKey = (String) cc.getProperties().get(SECRET_KEY_PROP);

        minioClient =
                MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        log.info("action=activated url handler for {}", protocol);
    }

    @Deactivate
    public void deactivate(ComponentContext cc) {
        log.info("action=deactivated url handler for  {}", protocol);
    }

    @Override
    public SmartURLConnection openConnection(URL u) throws IOException {
        return new MinioSmartURLConnection(u, minioClient, this);
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

}
