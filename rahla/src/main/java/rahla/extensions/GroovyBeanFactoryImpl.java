package rahla.extensions;

import groovy.lang.GroovyClassLoader;
import lombok.extern.log4j.Log4j2;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import rahla.api.GroovyBeanFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component
@Log4j2
public class GroovyBeanFactoryImpl implements GroovyBeanFactory {


  private GroovyClassLoader groovyClassLoader;
  private BundleContext bundleContext;

  public static final String RESOURCE_FILE = "resource:file:";
  public static final String RESOURCE_DEPLOY = "resource:deploy:";
  private String deploy_path = System.getenv().getOrDefault("RAHLA_DEPLOY_PATH" , "/deploy");

  public GroovyBeanFactoryImpl() {
    if (!deploy_path.endsWith("/"))
      deploy_path += "/";
  }

  @Activate
  public void activate(ComponentContext cc) throws InvalidSyntaxException, IOException {
    bundleContext = cc.getBundleContext();
    groovyClassLoader = new GroovyClassLoader(bundleContext.getBundle().adapt(BundleWiring.class).getClassLoader());
  }


  @Override
  public Object createBean(String urlSpec) {
    try {
      Class clazz;
      if (urlSpec.startsWith(RESOURCE_FILE)) {
        log.warn("resource:file: is deprecated and will be removed in a future release! Use createBean(String urlSpec) instead");
        String fileName = urlSpec.substring(RESOURCE_FILE.length());
        File file = new File(fileName);
        clazz = groovyClassLoader.parseClass(file);
      } else if (urlSpec.startsWith(RESOURCE_DEPLOY)) {
        log.warn("resource:deploy: is deprecated and will be removed in a future release! Use createBean(String urlSpec) instead");
        String fileName = urlSpec.substring(RESOURCE_DEPLOY.length());
        if(fileName.startsWith("/")){
          fileName = fileName.substring(1);
        }
        fileName = deploy_path + fileName;
        File file = new File(fileName);
        clazz = groovyClassLoader.parseClass(file);
      } else {
        URL url = new URL(urlSpec);
        InputStream inputStream = url.openStream();
        String clazzString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        inputStream.close();
        clazz = groovyClassLoader.parseClass(clazzString);
      }
      Object bean = newBean(clazz);
      log.info("Bean of class {} created from: {}", bean.getClass().getName(), urlSpec);
      return bean;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

  private Object newBean(Class<Object> clazz) throws Exception {
    try {
      return clazz.getDeclaredConstructor(BundleContext.class).newInstance(bundleContext);
    } catch (NoSuchMethodException ignored) {
    }
    return clazz.getDeclaredConstructor().newInstance();
  }


}
