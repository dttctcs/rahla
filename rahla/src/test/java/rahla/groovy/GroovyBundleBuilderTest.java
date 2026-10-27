package rahla.groovy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroovyBundleBuilderTest {

  @TempDir Path dir;

  private static final String PROCESSOR_SOURCE =
      """
      package processors

      import org.apache.camel.Exchange
      import org.apache.camel.Processor

      class MyProcessor implements Processor {
        void process(Exchange exchange) throws Exception {
          exchange.message.setHeader('seen', true)
        }
      }
      """;

  @Test
  void compilesGroovySourceIntoOsgiBundleJar() throws Exception {
    Path source = Files.writeString(dir.resolve("MyProcessor.groovy"), PROCESSOR_SOURCE);

    byte[] jar = new GroovyBundleBuilder(getClass().getClassLoader()).build(source.toUri().toURL()).bytes();

    try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(jar))) {
      Attributes mf = in.getManifest().getMainAttributes();
      assertEquals("2", mf.getValue("Bundle-ManifestVersion"));
      assertEquals("rahla.groovy.MyProcessor", mf.getValue("Bundle-SymbolicName"));
      assertTrue(mf.getValue("Bundle-Version").startsWith("1.0.0."), mf.getValue("Bundle-Version"));
      assertEquals("processors", mf.getValue("Export-Package"));
      assertEquals("*", mf.getValue("DynamicImport-Package"));
      assertEquals(source.toUri().toURL().toString(), mf.getValue("Rahla-Groovy-Source"));
      assertTrue(entryNames(in).contains("processors/MyProcessor.class"));
    }
  }

  @Test
  void compiledClassIsLoadableAndRunsAsCamelProcessor() throws Exception {
    Path source = Files.writeString(dir.resolve("MyProcessor.groovy"), PROCESSOR_SOURCE);
    byte[] jar = new GroovyBundleBuilder(getClass().getClassLoader()).build(source.toUri().toURL()).bytes();
    Path jarFile = Files.write(dir.resolve("bundle.jar"), jar);

    try (URLClassLoader loader =
        new URLClassLoader(new URL[] {jarFile.toUri().toURL()}, getClass().getClassLoader())) {
      Processor processor =
          (Processor) loader.loadClass("processors.MyProcessor").getDeclaredConstructor().newInstance();
      Exchange exchange = new DefaultExchange(new DefaultCamelContext());

      processor.process(exchange);

      assertEquals(Boolean.TRUE, exchange.getMessage().getHeader("seen"));
    }
  }

  @Test
  void reportsCompileErrorsWithFileNameAndLine() throws Exception {
    Path source =
        Files.writeString(dir.resolve("Broken.groovy"), "package processors\nclass Broken {\n  void oops( {}\n}\n");

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> new GroovyBundleBuilder(getClass().getClassLoader()).build(source.toUri().toURL()));

    assertTrue(e.getMessage().contains("Broken.groovy"), e.getMessage());
    assertTrue(e.getMessage().contains("line 3"), e.getMessage());
  }

  @Test
  void derivesValidSymbolicNameFromArbitraryFileName() throws Exception {
    Path source = Files.writeString(dir.resolve("my processor (v2).groovy"), PROCESSOR_SOURCE);

    byte[] jar = new GroovyBundleBuilder(getClass().getClassLoader()).build(source.toUri().toURL()).bytes();

    try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(jar))) {
      assertEquals(
          "rahla.groovy.my_processor__v2_",
          in.getManifest().getMainAttributes().getValue("Bundle-SymbolicName"));
    }
  }

  @Test
  void versionQualifierFollowsSourceContent() throws Exception {
    Path source = Files.writeString(dir.resolve("MyProcessor.groovy"), PROCESSOR_SOURCE);
    GroovyBundleBuilder builder = new GroovyBundleBuilder(getClass().getClassLoader());
    String first = version(builder.build(source.toUri().toURL()).bytes());
    String again = version(builder.build(source.toUri().toURL()).bytes());
    Files.writeString(source, PROCESSOR_SOURCE.replace("'seen'", "'touched'"));

    String changed = version(builder.build(source.toUri().toURL()).bytes());

    assertEquals(first, again);
    assertNotEquals(first, changed);
  }

  @Test
  void producesPlaceholderBundleWhenImportedPackagesAreMissing() throws Exception {
    Path source =
        Files.writeString(
            dir.resolve("Later.groovy"),
            "package processors\nimport org.example.missing.Thing\nclass Later { Thing thing }\n");

    GroovyBundleBuilder.GroovyBundle bundle =
        new GroovyBundleBuilder(getClass().getClassLoader()).build(source.toUri().toURL());

    assertTrue(bundle.isPlaceholder());
    assertEquals(Set.of("org.example.missing"), bundle.waitingFor());
    try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(bundle.bytes()))) {
      Attributes mf = in.getManifest().getMainAttributes();
      assertEquals("rahla.groovy.Later", mf.getValue("Bundle-SymbolicName"));
      assertEquals("org.example.missing", mf.getValue("Import-Package"));
      assertEquals("unresolved", mf.getValue(GroovyBundleBuilder.STATUS_HEADER));
      assertTrue(entryNames(in).isEmpty(), "placeholder must not contain classes");
    }
  }

  @Test
  void placeholderCoversStarImportedPackagesWhenOnlySimpleNamesAreUnresolved() throws Exception {
    Path source =
        Files.writeString(
            dir.resolve("Star.groovy"),
            "package processors\nimport org.example.missing.*\nimport java.util.*\nclass Star implements Thing {}\n");

    GroovyBundleBuilder.GroovyBundle bundle =
        new GroovyBundleBuilder(getClass().getClassLoader()).build(source.toUri().toURL());

    assertEquals(Set.of("org.example.missing"), bundle.waitingFor(), "java.* is never imported in OSGi");
  }

  @Test
  void unresolvableSimpleNameWithoutAnyImportFailsHard() throws Exception {
    Path source =
        Files.writeString(dir.resolve("Typo.groovy"), "package processors\nclass Typo implements Nowhere {}\n");

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> new GroovyBundleBuilder(getClass().getClassLoader()).build(source.toUri().toURL()));

    assertTrue(e.getMessage().contains("Nowhere"), e.getMessage());
  }

  @Test
  void putsEveryClassOfAMultiClassFileIntoOneBundle() throws Exception {
    Path source =
        Files.writeString(
            dir.resolve("Toolbox.groovy"),
            """
            package processors

            import org.apache.camel.Exchange
            import org.apache.camel.Processor

            class Enricher implements Processor {
              void process(Exchange exchange) { exchange.message.setHeader('step', 'enrich') }
            }

            class Validator implements Processor {
              void process(Exchange exchange) { exchange.message.setHeader('step', 'validate') }
            }

            class Helper {
              static String greet() { 'hi' }
            }
            """);

    byte[] jar = new GroovyBundleBuilder(getClass().getClassLoader()).build(source.toUri().toURL()).bytes();

    try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(jar))) {
      Attributes mf = in.getManifest().getMainAttributes();
      assertEquals("rahla.groovy.Toolbox", mf.getValue("Bundle-SymbolicName"));
      assertEquals("processors", mf.getValue("Export-Package"));
      assertTrue(
          entryNames(in)
              .containsAll(
                  Set.of(
                      "processors/Enricher.class",
                      "processors/Validator.class",
                      "processors/Helper.class")));
    }
  }

  @Test
  void refusesClassesInTheDefaultPackageBecauseTheyCannotBeExported() throws Exception {
    Path source = Files.writeString(dir.resolve("NoPackage.groovy"), "class NoPackage {}\n");

    IllegalStateException e =
        assertThrows(
            IllegalStateException.class,
            () -> new GroovyBundleBuilder(getClass().getClassLoader()).build(source.toUri().toURL()));

    assertTrue(e.getMessage().contains("package"), e.getMessage());
  }

  private static String version(byte[] jar) throws IOException {
    try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(jar))) {
      return in.getManifest().getMainAttributes().getValue("Bundle-Version");
    }
  }

  private static Set<String> entryNames(JarInputStream in) throws IOException {
    Set<String> names = new HashSet<>();
    for (JarEntry e = in.getNextJarEntry(); e != null; e = in.getNextJarEntry()) {
      names.add(e.getName());
    }
    return names;
  }
}
