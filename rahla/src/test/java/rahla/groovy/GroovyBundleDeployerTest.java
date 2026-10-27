package rahla.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
