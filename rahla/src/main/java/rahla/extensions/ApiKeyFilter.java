package rahla.extensions;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsApplicationSelect;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsExtension;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsName;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@ApiKeySecured
@Provider
@JaxrsApplicationSelect("(osgi.jaxrs.name=.default)")
@JaxrsExtension
@JaxrsName("ApiKeyFilter")
@Component
public class ApiKeyFilter implements ContainerRequestFilter {
  private static final String headerName = "x-api-key";
  private String xApiKey;

  public ApiKeyFilter() {
    this(System.getenv().getOrDefault(headerName, "changeme"));
  }

  public ApiKeyFilter(String xApiKey) {
    this.xApiKey = xApiKey;
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) throws IOException {
    if (!xApiKey.equals(containerRequestContext.getHeaderString(headerName)))
      containerRequestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
  }
}
