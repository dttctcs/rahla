package rahla;

import groovy.lang.GroovyClassLoader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Processor;
import org.apache.felix.fileinstall.ArtifactInstaller;
import org.apache.felix.fileinstall.ArtifactListener;
import org.apache.groovy.json.internal.FastStringUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    service = {ArtifactInstaller.class, ArtifactListener.class},
    property = {"GroovyProcessorInstaller=true"},
    immediate = true)
@Slf4j
public class GroovyProcessorCompiler implements ArtifactInstaller {

  private static final String SCRIPT_FILENAME = "file.groovy.processor.installer";
  private static final String CONFIG_NAME = "rahla.camel.processor";
  private static final String ARTIFACT_EXTENSION = "groovy";
  private Map<String, ServiceRegistration<Processor>> serviceRegistrations =
      Collections.synchronizedMap(new LinkedHashMap<>());
  private Map<String, ScheduledFuture<?>> asyncCompile =
      Collections.synchronizedMap(new LinkedHashMap<>());
  private ScheduledExecutorService executor =
      Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

  @Reference private ConfigurationAdmin admin;
  private BundleContext bundleContext;

  public GroovyProcessorCompiler() {}

  @Activate
  public void activate(ComponentContext cc) throws InvalidSyntaxException, IOException {
    bundleContext = cc.getBundleContext();
  }

  @Override
  public synchronized void install(File file) {
    processScript(file);
  }

  @Override
  public synchronized void update(File file) {
    processScript(file);
  }

  @Override
  public synchronized void uninstall(File file) {
    unregister(file);
  }

  @Override
  public boolean canHandle(File file) {
    return file.getName().toLowerCase().endsWith(ARTIFACT_EXTENSION.toLowerCase());
  }

  private synchronized void processScript(File file) {
    ScheduledFuture<?> scheduledFuture = asyncCompile.get(file.getAbsolutePath());
    if (scheduledFuture != null) {
      if (!scheduledFuture.isDone() || scheduledFuture.isCancelled()) {
        scheduledFuture.cancel(true);
      }
    }

    doAddProcessor(file);
  }

  private synchronized void doAddProcessor(File file) {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(FastStringUtils.class.getClassLoader());
    Object opaque = FastStringUtils.toCharArray("opaque");
    Thread.currentThread().setContextClassLoader(contextClassLoader);

    Bundle bundle = bundleContext.getBundle();
    BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
    ClassLoader classLoader = bundleWiring.getClassLoader();
    GroovyClassLoader groovyClassLoader = new GroovyClassLoader(classLoader);
    try {
      Class clazz = groovyClassLoader.parseClass(file);

      for (Class anInterface : clazz.getInterfaces()) {
        if (anInterface.getName().toLowerCase(Locale.ROOT).contains("processor")) {
          registerProcessor(file, clazz);
        }
      }
    } catch (FileNotFoundException fne) {
      log.warn("action=add processor, reason={}", fne.getMessage());
    } catch (Exception e) {
      log.info(
          "action=add compile retry, file={}, reason={}",
          file.getAbsolutePath(),
          e.getMessage(),
          e);
      asyncCompile.put(
          file.getAbsolutePath(), executor.schedule(() -> processScript(file), 5, TimeUnit.SECONDS));
    }
    log.info("action=compiled, file={}", file.getAbsolutePath());
  }

  private synchronized void registerProcessor(File file, Class clazz)
      throws InstantiationException, IllegalAccessException, InvocationTargetException {
    unregister(file);
    String absolutePath = file.getAbsolutePath();
    String fileName = file.getName();
    Dictionary dict = new Properties();
    dict.put(SCRIPT_FILENAME, absolutePath);
    dict.put(CONFIG_NAME, fileName.substring(0, fileName.lastIndexOf('.')));
    Processor processor = null;
    try {
      Constructor<Processor> declaredConstructor =
          clazz.getDeclaredConstructor(BundleContext.class);
      processor = declaredConstructor.newInstance(bundleContext);
    } catch (NoSuchMethodException ignored) {
    }
    if (processor == null) {
      try {
        Constructor<Processor> declaredConstructor = clazz.getDeclaredConstructor();
        processor = declaredConstructor.newInstance();
      } catch (NoSuchMethodException ignored) {
        log.error("action=create service, reason=no constructor found");
      }
    }
    ServiceRegistration serviceRegistration =
        bundleContext.registerService(Processor.class, processor, dict);
    serviceRegistrations.put(file.getAbsolutePath(), serviceRegistration);
    log.info("action=register processor, file={}", file.getAbsolutePath());
  }

  private synchronized void unregister(File file) {
    ServiceRegistration<Processor> serviceRegistration =
        serviceRegistrations.remove(file.getAbsolutePath());
    if (serviceRegistration != null) {
      log.info("action=unregister processor , file={}", file.getAbsolutePath());
      serviceRegistration.unregister();
    }
  }
}
