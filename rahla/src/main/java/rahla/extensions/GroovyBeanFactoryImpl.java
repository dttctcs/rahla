package rahla.extensions;

import groovy.lang.GroovyClassLoader;
import lombok.extern.log4j.Log4j2;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import rahla.api.GroovyBeanFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component
@Log4j2
public class GroovyBeanFactoryImpl implements GroovyBeanFactory {

  /**
   * Legacy URL scheme accepted for backwards compatibility — pass real URLs instead.
   *
   * @deprecated will be removed in a future release; use {@code file://}, {@code http://}, ...
   */
  @Deprecated
  public static final String RESOURCE_FILE = "resource:file:";

  private GroovyClassLoader groovyClassLoader;
  private BundleContext bundleContext;

  @Activate
  public void activate(ComponentContext cc) {
    bundleContext = cc.getBundleContext();
    ClassLoader bundleClassLoader =
        bundleContext.getBundle().adapt(BundleWiring.class).getClassLoader();
    groovyClassLoader = new GroovyClassLoader(bundleClassLoader);
  }

  @Override
  public Object createBean(String urlSpec) {
    try {
      Class<?> clazz = parseClass(urlSpec);
      Object bean = newBean(clazz);
      log.info("Bean of class {} created from: {}", bean.getClass().getName(), urlSpec);
      return bean;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create Groovy bean from " + urlSpec, e);
    }
  }

  private Class<?> parseClass(String urlSpec) throws Exception {
    if (urlSpec.startsWith(RESOURCE_FILE)) {
      log.warn("resource:file: is deprecated and will be removed in a future release! Use URLs (file://, http://, ...)");
      File file = new File(urlSpec.substring(RESOURCE_FILE.length()));
      return groovyClassLoader.parseClass(file);
    }
    URL url = URI.create(urlSpec).toURL();
    try (InputStream in = url.openStream()) {
      String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return groovyClassLoader.parseClass(source);
    }
  }

  private Object newBean(Class<?> clazz) throws Exception {
    try {
      return clazz.getDeclaredConstructor(BundleContext.class).newInstance(bundleContext);
    } catch (NoSuchMethodException ignored) {
      return clazz.getDeclaredConstructor().newInstance();
    }
  }
}
