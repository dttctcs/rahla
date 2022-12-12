/*
 * MIT License
 *
 * Copyright © 2020 Matthias Leinweber datatactics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package rahla.extensions;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
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
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import static org.apache.camel.spi.RouteTemplateParameterSource.TEMPLATE_ID;
import static rahla.extensions.OsgiRouteTemplateParameterSource.*;

@Component(
    service = {ArtifactInstaller.class, ArtifactListener.class},
    property = {"TemplateFileInstaller=true"},
    immediate = true)
@Log4j2
public class TemplateFileInstaller implements ArtifactInstaller {
  public static final String TEMPLATEILE_INSTALL_PREFIX = "tmpltflnstllr::";
  private final ObjectMapper mapper;

  @Reference private ConfigurationAdmin admin;

  public TemplateFileInstaller() {
    mapper = new ObjectMapper(new YAMLFactory());
    mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
    mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
  }

  private Template fromYaml(File file) throws IOException {
    String encoded = Files.readString(Paths.get(file.getAbsolutePath()));
    return mapper.readValue(encoded, Template.class);
  }

  private void deleteConfig(File file) {
    String componentFilter =
        String.format("(%s=%s)", TEMPLATEILE_INSTALL_PREFIX, file.getAbsolutePath());
    try {
      Configuration[] config = admin.listConfigurations(componentFilter);
      if (config != null && config.length > 0) {
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
    Template template;
    try {
      template = fromYaml(file);
    } catch (IOException e) {
      log.error("BUG(): {}", e.getMessage(), e);
      return;
    }
    Dictionary<String, Object> config = new Hashtable<>();
    config.put(TEMPLATE_ID, template.getTemplateId());
    config.put(SHARED_CONFIG_PID, template.getSharedConfigPid());
    config.put(TEMPLATEILE_INSTALL_PREFIX, file.getAbsolutePath());
    template
        .getRoutes()
        .forEach(
            routeConfig ->
                routeConfig
                    .getParameters()
                    .forEach(
                        (key, value) ->
                            config.put(
                                TEMPLATE_PREFIX + routeConfig.getId() + "." + key, value)));

    try {
      Configuration factoryConfiguration = admin.createFactoryConfiguration("camel.route.template");
      factoryConfiguration.update(config);
    } catch (IOException e) {
      log.error("action=create config, reason={}", e.getMessage(), e);
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
        fromYaml(file);
        return true;
      } catch (Exception e) {
        log.warn("action=parsing compound {}: {}", file.getName(), e.getMessage());
      }
    return false;
  }

  @Getter
  public static class Template {
    private String templateId;
    private String sharedConfigPid;
    private List<RoutesConfig> routes;

    @Getter
    public static class RoutesConfig {
      private String id;
      private Map<String, Object> parameters;
    }
  }
}
