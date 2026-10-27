package rahla.groovy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.log4j.Log4j2;
import org.apache.felix.fileinstall.ArtifactListener;
import org.apache.felix.fileinstall.ArtifactUrlTransformer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.url.AbstractURLStreamHandlerService;
import org.osgi.service.url.URLStreamHandlerService;

/**
 * Felix fileinstall deployer that turns every {@code *.groovy} file in a watched directory into an
 * OSGi bundle.
 * <p>
 * Mirrors Karaf's own deployers (blueprint, features): {@link #transform(URL)} rewrites the file
 * URL to {@code rahla-groovy:file:/...}, fileinstall installs that location as a bundle, and this
 * class doubles as the {@code rahla-groovy:} URL handler that compiles the source on
 * {@link URL#openStream()}. A file change triggers {@code bundle.update()}, a delete uninstalls.
 * <p>
 * Runs alongside {@link rahla.api.GroovyBeanFactory}: sources referenced from a Blueprint XML via
 * the factory keep working unchanged, bundle-deployed classes are additionally addressable as
 * plain {@code <bean class="..."/>}.
 * <p>
 * Deploy order does not matter: a source whose imports are not available yet (say, Camel is still
 * being installed) is installed as a placeholder bundle that waits on those packages (see
 * {@link GroovyBundleBuilder#build}). This class listens for bundle events and, once a placeholder
 * has become resolvable, updates it, which recompiles the source.
 */
@Component(
    service = {URLStreamHandlerService.class, ArtifactListener.class, ArtifactUrlTransformer.class},
    property = {"url.handler.protocol=" + GroovyBundleDeployer.PROTOCOL},
    immediate = true)
@Log4j2
public class GroovyBundleDeployer extends AbstractURLStreamHandlerService
    implements ArtifactUrlTransformer, BundleListener {

  public static final String PROTOCOL = "rahla-groovy";

  private GroovyBundleBuilder builder;
  private BundleContext context;
  private ExecutorService retries;

  /** Used by Declarative Services; the builder is created in {@link #activate}. */
  public GroovyBundleDeployer() {}

  GroovyBundleDeployer(GroovyBundleBuilder builder) {
    this.builder = builder;
  }

  @Activate
  public void activate(ComponentContext cc) {
    context = cc.getBundleContext();
    builder = new GroovyBundleBuilder(context.getBundle().adapt(BundleWiring.class).getClassLoader());
    retries =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "rahla-groovy-deployer");
              t.setDaemon(true);
              return t;
            });
    context.addBundleListener(this);
    log.info("Groovy bundle deployer active: *.groovy files in deploy directories become bundles");
  }

  @Deactivate
  public void deactivate() {
    context.removeBundleListener(this);
    retries.shutdownNow();
  }

  @Override
  public void bundleChanged(BundleEvent event) {
    if (event.getType() == BundleEvent.RESOLVED || event.getType() == BundleEvent.STARTED) {
      retries.execute(this::compileResolvablePlaceholders);
    }
  }

  /** Placeholders whose imports can be satisfied now are updated, which recompiles the source. */
  void compileResolvablePlaceholders() {
    FrameworkWiring wiring = context.getBundle(0).adapt(FrameworkWiring.class);
    for (Bundle bundle : context.getBundles()) {
      try {
        if (!isPlaceholder(bundle) || !wiring.resolveBundles(List.of(bundle))) {
          continue;
        }
        log.info(
            "Packages {} are available now, compiling {}",
            bundle.getHeaders("").get("Import-Package"),
            bundle.getLocation());
        bundle.update();
        if (bundle.getState() != Bundle.ACTIVE) {
          bundle.start();
        }
      } catch (Exception e) {
        log.warn("Recompiling {} failed", bundle.getLocation(), e);
      }
    }
  }

  private static boolean isPlaceholder(Bundle bundle) {
    String location = bundle.getLocation();
    return location != null
        && location.startsWith(PROTOCOL + ":")
        && GroovyBundleBuilder.STATUS_UNRESOLVED.equals(
            bundle.getHeaders("").get(GroovyBundleBuilder.STATUS_HEADER));
  }

  @Override
  public boolean canHandle(File artifact) {
    return artifact.getName().endsWith(GroovyBundleBuilder.GROOVY_SUFFIX);
  }

  /**
   * The returned URL carries its own stream handler, so fileinstall can open it directly and the
   * transformer also works outside a framework (tests). Inside Karaf the same location string
   * resolves through the registered {@link URLStreamHandlerService}, e.g. on {@code bundle:update}.
   */
  @Override
  @SuppressWarnings("deprecation") // no URI-based way to attach a handler to a URL
  public URL transform(URL artifact) throws Exception {
    return new URL(null, PROTOCOL + ":" + artifact.toExternalForm(), new Handler());
  }

  private final class Handler extends java.net.URLStreamHandler {
    @Override
    protected URLConnection openConnection(URL url) throws IOException {
      return GroovyBundleDeployer.this.openConnection(url);
    }
  }

  @Override
  public URLConnection openConnection(URL url) throws IOException {
    return new URLConnection(url) {
      @Override
      public void connect() {}

      @Override
      public InputStream getInputStream() throws IOException {
        URL source = URI.create(url.getPath()).toURL();
        GroovyBundleBuilder.GroovyBundle bundle = builder.build(source);
        if (bundle.isPlaceholder()) {
          log.warn(
              "{} needs packages {} that are not available yet; installed a placeholder bundle"
                  + " that gets compiled once they are",
              source,
              bundle.waitingFor());
        } else {
          log.info("Compiled {} into an OSGi bundle", source);
        }
        return new ByteArrayInputStream(bundle.bytes());
      }
    };
  }
}
