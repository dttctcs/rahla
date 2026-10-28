package rahla.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Hashtable;
import java.util.List;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.startlevel.BundleStartLevel;

class GroovyBundleDeployerTest {

  @TempDir Path dir;

  private final GroovyBundleDeployer deployer =
      new GroovyBundleDeployer(new GroovyBundleBuilder(getClass().getClassLoader()));

  @Test
  void handlesOnlyGroovyFiles() {
    assertTrue(deployer.canHandle(new File("MyProcessor.groovy")));
    assertFalse(deployer.canHandle(new File("routes.xml")));
    assertFalse(deployer.canHandle(new File("notes.groovy.txt")));
  }

  @Test
  void transformsFileUrlIntoSelfResolvingBundleUrl() throws Exception {
    Path source =
        Files.writeString(
            dir.resolve("MyProcessor.groovy"), "package processors\nclass MyProcessor {}\n");
    URL fileUrl = source.toUri().toURL();

    URL bundleUrl = deployer.transform(fileUrl);

    assertEquals(GroovyBundleDeployer.PROTOCOL + ":" + fileUrl, bundleUrl.toString());
    try (JarInputStream in = new JarInputStream(bundleUrl.openStream())) {
      assertEquals(
          "rahla.groovy.MyProcessor",
          in.getManifest().getMainAttributes().getValue("Bundle-SymbolicName"));
    }
  }

  @Test
  void sourceRevisionFollowsTheFileContent() throws Exception {
    Path source = Files.writeString(dir.resolve("Broken.groovy"), "package p\nclass Broken { void x( }\n");
    String location = GroovyBundleDeployer.PROTOCOL + ":" + source.toUri().toURL();

    String first = GroovyBundleDeployer.sourceRevision(location);
    assertEquals(first, GroovyBundleDeployer.sourceRevision(location));

    Files.writeString(source, "package p\nclass Broken { void x() {} }\n");
    assertFalse(first.equals(GroovyBundleDeployer.sourceRevision(location)));
    assertEquals(null, GroovyBundleDeployer.sourceRevision(GroovyBundleDeployer.PROTOCOL + ":file:/does/not/exist.groovy"));
  }

  @Test
  void waitingToStartPicksOnlyPersistentlyStartedInstalledNonPlaceholderBundles() {
    Bundle xml = bundle(1, Bundle.INSTALLED, "blueprint:file:/deploy/routes.xml", null, true, 80);
    Bundle active = bundle(2, Bundle.ACTIVE, "blueprint:file:/deploy/other.xml", null, true, 80);
    Bundle stopped = bundle(3, Bundle.INSTALLED, "mvn:x/y/1", null, false, 80);
    Bundle fragment = bundle(4, Bundle.INSTALLED, "wrap:mvn:x/frag/1", "host", true, 80);
    Bundle placeholder =
        bundle(5, Bundle.INSTALLED, GroovyBundleDeployer.PROTOCOL + ":file:/deploy/A.groovy", null, true, 80);
    Bundle tooHigh = bundle(6, Bundle.INSTALLED, "blueprint:file:/deploy/late.xml", null, true, 90);
    Bundle system = bundle(0, Bundle.INSTALLED, "System Bundle", null, true, 0);

    List<Bundle> waiting =
        GroovyBundleDeployer.waitingToStart(
            new Bundle[] {system, xml, active, stopped, fragment, placeholder, tooHigh}, 80);

    assertEquals(List.of(xml), waiting);
  }

  /** Minimal fake: only what waitingToStart and isPlaceholder look at. */
  private static Bundle bundle(
      long id, int state, String location, String fragmentHost, boolean persistent, int level) {
    Hashtable<String, String> headers = new Hashtable<>();
    if (fragmentHost != null) {
      headers.put("Fragment-Host", fragmentHost);
    }
    if (location.startsWith(GroovyBundleDeployer.PROTOCOL + ":")) {
      headers.put(GroovyBundleBuilder.STATUS_HEADER, GroovyBundleBuilder.STATUS_UNRESOLVED);
    }
    BundleStartLevel startLevel =
        (BundleStartLevel)
            Proxy.newProxyInstance(
                BundleStartLevel.class.getClassLoader(),
                new Class<?>[] {BundleStartLevel.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "isPersistentlyStarted" -> persistent;
                      case "getStartLevel" -> level;
                      default -> throw new UnsupportedOperationException(method.getName());
                    });
    return (Bundle)
        Proxy.newProxyInstance(
            Bundle.class.getClassLoader(),
            new Class<?>[] {Bundle.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getBundleId" -> id;
                  case "getState" -> state;
                  case "getLocation" -> location;
                  case "getHeaders" -> headers;
                  case "adapt" -> args[0] == BundleStartLevel.class ? startLevel : null;
                  case "toString" -> location;
                  case "hashCode" -> (int) id;
                  case "equals" -> proxy == args[0];
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }
}
