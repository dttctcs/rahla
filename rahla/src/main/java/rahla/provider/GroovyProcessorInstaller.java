package rahla.provider;

import groovy.lang.GroovyClassLoader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Dictionary;
import java.util.Locale;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Processor;
import org.apache.felix.fileinstall.ArtifactInstaller;
import org.apache.felix.fileinstall.ArtifactListener;
import org.apache.groovy.json.internal.FastStringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
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
public class GroovyProcessorInstaller implements ArtifactInstaller {

  private static final String CONFIG_FILENAME = "file.groovy.processor.installer";
  private static final String CONFIG_NAME = "rahla.camel.processor";
  private static final String ARTIFACT_EXTENSION = "groovy";

  private ServiceRegistration<Processor> serviceRegistration;

  @Reference private ConfigurationAdmin admin;
  private BundleContext bundleContext;

  public GroovyProcessorInstaller() {}

  @Activate
  public void activate(ComponentContext cc) throws InvalidSyntaxException, IOException {
    bundleContext = cc.getBundleContext();
  }

  @Override
  public synchronized void install(File file)
      throws IOException, InvalidSyntaxException, InvocationTargetException, NoSuchMethodException,
          InstantiationException, IllegalAccessException {

    String absolutePath = file.getAbsolutePath();
    addProcessor(file);
  }

  @Override
  public synchronized void update(File file)
      throws IOException, InvalidSyntaxException, InvocationTargetException, NoSuchMethodException,
          InstantiationException, IllegalAccessException {
    removeProcessor(file);
    addProcessor(file);
  }

  @Override
  public synchronized void uninstall(File file) throws IOException, InvalidSyntaxException {
    removeProcessor(file);
  }

  @Override
  public boolean canHandle(File file) {
    return file.getName().toLowerCase().endsWith(ARTIFACT_EXTENSION.toLowerCase());
  }

  private void addProcessor(File file)
      throws IOException, NoSuchMethodException, InvocationTargetException, InstantiationException,
          IllegalAccessException {

    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(FastStringUtils.class.getClassLoader());
    Object opaque = FastStringUtils.toCharArray("opaque");
    Thread.currentThread().setContextClassLoader(contextClassLoader);
    ClassLoader classLoader = GroovyProcessorInstaller.class.getClassLoader();
    GroovyClassLoader groovyClassLoader = new GroovyClassLoader(classLoader);
    String absolutePath = file.getAbsolutePath();
    String fileName = file.getName();

    Class clazz = groovyClassLoader.parseClass(file);
    Class[] interfaces = clazz.getInterfaces();

    for (Class anInterface : interfaces) {
      if (anInterface.getName().toLowerCase(Locale.ROOT).contains("processor")) {
        registerProcessor(file, clazz);
      }
    }
  }

  private void registerProcessor(File file, Class clazz)
      throws InstantiationException, IllegalAccessException, InvocationTargetException,
          NoSuchMethodException {
    String absolutePath = file.getAbsolutePath();
    String fileName = file.getName();
    Dictionary dict = new Properties();
    dict.put(CONFIG_NAME, absolutePath);
    dict.put("rahla.camel.processor", fileName.substring(0, fileName.lastIndexOf('.')));
    Processor o = (Processor) clazz.getDeclaredConstructor().newInstance();
    serviceRegistration = bundleContext.registerService(Processor.class, o, dict);
  }

  private void removeProcessor(File file) throws InvalidSyntaxException, IOException {
    if (serviceRegistration != null) serviceRegistration.unregister();
  }
}
