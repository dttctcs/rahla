package rahla.routetemplates;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
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
import java.util.*;

import static org.apache.camel.spi.RouteTemplateParameterSource.TEMPLATE_ID;
import static rahla.routetemplates.OsgiRouteTemplateParameterSource.TEMPLATE_PREFIX;

@Component(service = {ArtifactInstaller.class, ArtifactListener.class}, property = {"TemplateFileInstaller=true"}, immediate = true)
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

  public List<Template> templatesFromYaml(File file) throws IOException {
    YAMLFactory yamlFactory = new YAMLFactory();
    YAMLParser yamlParser = yamlFactory.createParser(file);
    return mapper.readValues(yamlParser, new TypeReference<Template>() {
    }).readAll();
  }

  private void deleteConfig(File file) {
    String componentFilter = String.format("(%s=%s)", TEMPLATEILE_INSTALL_PREFIX, file.getAbsolutePath());
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
    Set<String> sharedConfigPids = new HashSet<>();
    List<Dictionary<String, Object>> configs = new LinkedList<>();
    try {
      List<Template> templates = templatesFromYaml(file);
      for (Template template : templates) {
        if (template.sharedConfigPid != null) {
          sharedConfigPids.add(template.sharedConfigPid);
        }
        configs.add(createConfig(template.templateId, file.getAbsolutePath(), template.routes));
      }

      for (Dictionary<String, Object> config : configs) {
        Configuration factoryConfiguration = configurationAdmin.createFactoryConfiguration("camel.route.template");
        factoryConfiguration.update(config);
      }

    } catch (IOException ex) {
      log.error("BUG(): {}", ex.getMessage(), ex);
    }


    for (String sharedConfigPid : sharedConfigPids) {
      if (sharedConfigPid != null) {
        try {
          updateSharedConfig(sharedConfigPid);
        } catch (IOException ioException) {
          log.error("action=update shared config, reason={}", ioException.getMessage(), ioException);
        }
      }
    }

  }

  private Dictionary<String, Object> createConfig(String templateId, String id, List<Routes> routesConfigs) {
    Dictionary<String, Object> config = new Hashtable<>();
    config.put(TEMPLATE_ID, templateId);
    config.put(TEMPLATEILE_INSTALL_PREFIX, id);
    routesConfigs.forEach(routeConfig -> routeConfig.parameters.forEach((key, value) -> config.put(TEMPLATE_PREFIX + routeConfig.id + "." + key, value)));
    return config;
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
    if (file.getName().toLowerCase().endsWith("yaml")) try {
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
    public String sharedConfigPid;
    public String templateId;
    public List<Routes> routes;
  }

  public static class Routes {
    public String id;
    public Map<String, Object> parameters;
  }

}

