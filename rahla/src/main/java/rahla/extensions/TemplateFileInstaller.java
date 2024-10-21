package rahla.extensions;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.log4j.Log4j2;
import org.apache.felix.fileinstall.ArtifactInstaller;
import org.apache.felix.fileinstall.ArtifactListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.apache.camel.spi.RouteTemplateParameterSource.TEMPLATE_ID;

@Component(
    service = {ArtifactInstaller.class, ArtifactListener.class},
    property = {"TemplateFileInstaller=true"},
    immediate = true)
@Log4j2
public class TemplateFileInstaller implements ArtifactInstaller {
  public static final String TEMPLATEILE_INSTALL_PREFIX = "rahla.extensions.TemplateFileInstaller";
  public static final String SHARED_CONFIG_COUNTER = "shared.config.trigger.count";
  private final ObjectMapper mapper;
  @Reference
  private ConfigurationAdmin configurationAdmin;


  public TemplateFileInstaller() {
    mapper = new ObjectMapper(new YAMLFactory());
    mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
    mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
  }

  private Templates templatesFromYaml(File file) throws IOException {
    String encoded = Files.readString(Paths.get(file.getAbsolutePath()));
    return mapper.readValue(encoded, Templates.class);
  }

  private Templates templateFromYaml(File file) throws IOException {
    String encoded = Files.readString(Paths.get(file.getAbsolutePath()));
    return mapper.readValue(encoded, Templates.class);
  }

  private void deleteConfig(File file) {
    String componentFilter =
        String.format("(%s=%s)", TEMPLATEILE_INSTALL_PREFIX, file.getAbsolutePath());
    try {
      Configuration[] config = configurationAdmin.listConfigurations(componentFilter);
      if (config != null) {
        for (Configuration configuration : config) {
          configuration.delete();
        }
      }
    } catch (IOException | InvalidSyntaxException e) {
      log.error("BUG():", e.getMessage(), e);
    }
  }

  private void upsertConfig(File file) {
    deleteConfig(file);
    String sharedConfigPid = null;
    try {
      Templates templatesFile = templatesFromYaml(file);
      templateBuilder(templatesFile, file.getAbsolutePath());
      sharedConfigPid = templatesFile.sharedConfigPid;

    } catch (IOException ex) {
      log.error("BUG(): {}", ex.getMessage(), ex);
    }

    if (sharedConfigPid != null) {
      try {
        updateSharedConfig(sharedConfigPid);
      } catch (IOException ioException) {
        log.error("action=update shared config, reason={}", ioException.getMessage(), ioException);
      }
    }
  }

  private Dictionary<String, Object> createConfig(String templateId, String id, List<Templates.Routes> routesConfigs) {
    Dictionary<String, Object> config = new Hashtable<>();
    config.put(TEMPLATE_ID, templateId);
    config.put(TEMPLATEILE_INSTALL_PREFIX, id);
    return config;
  }


  private void templateBuilder(Templates templatesFile, String id) throws IOException {
    List<Dictionary<String, Object>> configs = new LinkedList<>();

    if (templatesFile.templateId != null) {
      configs.add(createConfig(templatesFile.templateId, id, templatesFile.routes));
    }

    for (Template template : templatesFile.routeTemplates) {
      configs.add(createConfig(template.templateId, id, template.routes));
    }

    for (Dictionary<String, Object> config : configs) {
      Configuration factoryConfiguration = configurationAdmin.createFactoryConfiguration("camel.route.template");
      factoryConfiguration.update(config);
    }
  }

  @Override
  public synchronized void install(File file) {
    log.info("action=install template {}", file.getAbsolutePath());
    upsertConfig(file);
  }

  @Override
  public synchronized void update(File file) {
    log.info("action=update template {}", file.getAbsolutePath());
    upsertConfig(file);
  }

  @Override
  public synchronized void uninstall(File file) {
    log.info("action=uninstall template{}", file.getAbsolutePath());
    deleteConfig(file);
  }

  @Override
  public boolean canHandle(File file) {
    if (file.getName().toLowerCase().endsWith("yaml"))
      try {
        templatesFromYaml(file);
        return true;
      } catch (Exception e) {
        log.warn("action=parsing compound {}: {}", file.getName(), e.getMessage());
      }
    return false;
  }

  private void updateSharedConfig(String sharedConfigPid) throws IOException {
    Configuration configuration;

    configuration = configurationAdmin.getConfiguration(sharedConfigPid, null);
    Dictionary<String, Object> props = configuration.getProperties();
    if (props == null) {
      props = new Hashtable<>();
    }
    Object o = props.get(SHARED_CONFIG_COUNTER);
    int newCnt = 0;
    if (o != null) {
      newCnt = ((int) o) + 1;
    }
    props.put(SHARED_CONFIG_COUNTER, newCnt);
    configuration.update(props);
  }

  public static class Template {
    String templateId;
    List<Templates.Routes> routes;
  }

  public static class Templates extends Template{
    String sharedConfigPid;
    List<Template> routeTemplates;

    public static class Routes {
      String sharedConfigPid;
      String id;
      Map<String, Object> parameters;
    }
  }
}

