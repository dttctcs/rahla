package rahla.groovy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.log4j.Log4j2;
import org.apache.felix.fileinstall.ArtifactListener;
import org.apache.felix.fileinstall.ArtifactUrlTransformer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;
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

  /**
   * Placeholder location -> hash of the source revision whose recompile failed. Bundle events keep
   * coming, and a placeholder whose imports are satisfiable stays satisfiable; without this the
   * same broken revision would be recompiled (and logged) on every event. A changed file has a new
   * hash and is tried again.
   */
  private final Map<String, String> failedRevisions = new ConcurrentHashMap<>();

  /** A compiled Groovy bundle could not start yet; keep retrying on bundle events. */
  private volatile boolean startPending;

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

  /**
   * Placeholders whose imports can be satisfied now are recompiled and updated.
   *
   * <p>The source is compiled <em>before</em> {@code bundle.update(stream)}. A plain {@code
   * bundle.update()} would open the {@code rahla-groovy:} URL — and thus compile — while Felix
   * holds the bundle's lock; the Groovy compiler resolves classes through dynamic imports, which
   * needs Felix' global lock. A thread starting bundles at the same time (fileinstall, the
   * features service) holds that global lock and waits for the bundle: a deadlock that froze the
   * whole container during a feature install.
   */
  void compileResolvablePlaceholders() {
    FrameworkWiring wiring = context.getBundle(0).adapt(FrameworkWiring.class);
    boolean compiled = false;
    for (Bundle bundle : context.getBundles()) {
      if (!isPlaceholder(bundle)) {
        continue;
      }
      String location = bundle.getLocation();
      String revision = sourceRevision(location);
      if (revision != null && revision.equals(failedRevisions.get(location))) {
        continue; // this revision already failed to compile; wait for the file to change
      }
      InputStream recompiled;
      try {
        if (!wiring.resolveBundles(List.of(bundle))) {
          continue;
        }
        log.info(
            "Packages {} are available now, compiling {}",
            bundle.getHeaders("").get("Import-Package"),
            location);
        recompiled = openConnection(handlerUrl(location)).getInputStream();
      } catch (Exception e) {
        if (revision != null) {
          failedRevisions.put(location, revision);
        }
        log.warn(
            "Recompiling {} failed; not retrying until the file changes", location, e);
        continue;
      }
      try {
        bundle.update(recompiled);
        failedRevisions.remove(location);
        if (isPlaceholder(bundle)) {
          continue; // the packages went away again (refresh in progress); the next event retries
        }
        compiled = true;
        if (bundle.getState() != Bundle.ACTIVE) {
          bundle.start();
        }
      } catch (Exception e) {
        // not a compile error: typically the bundle cannot resolve while a feature install is
        // still refreshing its providers. Nothing to remember; start it once it resolves.
        startPending = true;
        log.info("{} is compiled but cannot start yet ({}); retrying", location, e.getMessage());
      }
    }
    if (compiled || startPending) {
      startPending = startWaitingBundles(wiring);
    }
  }

  /**
   * Starts bundles that failed to start earlier because they needed a package a placeholder now
   * provides — typically a Blueprint XML that landed in an earlier fileinstall scan than its Groovy
   * classes. fileinstall would retry them itself, but only on a later scan whose "state changed"
   * flag survived; a placeholder recompiled while that scan was running loses the wake-up, and the
   * XML bundle stayed INSTALLED until it was touched.
   *
   * @return true if a compiled Groovy bundle is still waiting to start (retry on the next event)
   */
  boolean startWaitingBundles(FrameworkWiring wiring) {
    int frameworkLevel = context.getBundle(0).adapt(FrameworkStartLevel.class).getStartLevel();
    List<Bundle> waiting = waitingToStart(context.getBundles(), frameworkLevel);
    if (waiting.isEmpty()) {
      return false;
    }
    wiring.resolveBundles(waiting);
    boolean groovyStillWaiting = false;
    for (Bundle bundle : waiting) {
      String location = bundle.getLocation();
      boolean groovy = location != null && location.startsWith(PROTOCOL + ":");
      if (bundle.getState() != Bundle.RESOLVED) {
        groovyStillWaiting |= groovy;
        continue; // still missing something; a Groovy bundle is retried, the rest is fileinstall's
      }
      try {
        bundle.start();
        log.info("Started {}, which was waiting for packages of a Groovy bundle", location);
      } catch (Exception e) {
        groovyStillWaiting |= groovy;
        log.warn("Starting {} failed", location, e);
      }
    }
    return groovyStillWaiting;
  }

  /**
   * Bundles that should run but do not: INSTALLED (never resolved), marked persistently started
   * (fileinstall starts with options 0), within the current start level, not a fragment and not
   * one of our own placeholders.
   */
  static List<Bundle> waitingToStart(Bundle[] bundles, int frameworkStartLevel) {
    List<Bundle> waiting = new ArrayList<>();
    for (Bundle bundle : bundles) {
      if (bundle.getBundleId() == 0
          || bundle.getState() != Bundle.INSTALLED
          || isPlaceholder(bundle)
          || bundle.getHeaders("").get(Constants.FRAGMENT_HOST) != null) {
        continue;
      }
      BundleStartLevel startLevel = bundle.adapt(BundleStartLevel.class);
      if (startLevel != null
          && startLevel.isPersistentlyStarted()
          && startLevel.getStartLevel() <= frameworkStartLevel) {
        waiting.add(bundle);
      }
    }
    return waiting;
  }

  /** Hash of the current source behind a {@code rahla-groovy:} location, null if unreadable. */
  static String sourceRevision(String location) {
    try {
      URL source = URI.create(location.substring(PROTOCOL.length() + 1)).toURL();
      return GroovyBundleBuilder.sha256(GroovyBundleBuilder.read(source));
    } catch (Exception e) {
      return null;
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
    return handlerUrl(PROTOCOL + ":" + artifact.toExternalForm());
  }

  @SuppressWarnings("deprecation") // no URI-based way to attach a handler to a URL
  private URL handlerUrl(String location) throws java.net.MalformedURLException {
    return new URL(null, location, new Handler());
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
