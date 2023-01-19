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

@Component
@Log4j2
public class GroovyBeanFactoryImpl implements GroovyBeanFactory {


  private GroovyClassLoader groovyClassLoader;
  private BundleContext bundleContext;

  public static final String RESOURCE_FILE = "resource:file:";
  public static final String RESOURCE_DEPLOY = "resource:deploy:";
  private String deploy_path = System.getenv("RAHLA_DEPLOY_PATH");

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
  public Object createBean(String resource) {


    try {
      Class clazz;
      if (resource.startsWith(RESOURCE_FILE)) {
        String fileName = resource.substring(RESOURCE_FILE.length());
        File file = new File(fileName);
        clazz = groovyClassLoader.parseClass(file);
      } else if (resource.startsWith(RESOURCE_DEPLOY)) {
        String fileName = resource.substring(RESOURCE_DEPLOY.length());
        if(fileName.startsWith("/")){
          fileName = fileName.substring(1);
        }
        fileName = deploy_path + fileName;
        File file = new File(fileName);
        clazz = groovyClassLoader.parseClass(file);
      } else {
        clazz = groovyClassLoader.parseClass(resource);
      }

      Object bean = newBean(clazz);
      log.info("bean_created={}", resource);
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
