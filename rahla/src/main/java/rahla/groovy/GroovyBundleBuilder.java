package rahla.groovy;

import groovy.lang.GroovyClassLoader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.tools.GroovyClass;

/**
 * Compiles a Groovy source file into an OSGi bundle (jar bytes).
 * <p>
 * The bundle exports every package the source declares and dynamically imports everything else,
 * so the compiled classes see the same packages the Rahla bundle sees. The bundle version carries a
 * hash of the source as qualifier, so {@code bundle:list} shows which revision is deployed.
 */
public class GroovyBundleBuilder {

  public static final String SYMBOLIC_NAME_PREFIX = "rahla.groovy.";
  public static final String SOURCE_HEADER = "Rahla-Groovy-Source";
  /** {@value #STATUS_COMPILED} for a real bundle, {@value #STATUS_UNRESOLVED} for a placeholder. */
  public static final String STATUS_HEADER = "Rahla-Groovy-Status";
  public static final String STATUS_COMPILED = "compiled";
  public static final String STATUS_UNRESOLVED = "unresolved";
  static final String GROOVY_SUFFIX = ".groovy";

  private final ClassLoader compileClassLoader;

  /**
   * @param compileClassLoader class loader the compiler resolves referenced classes against
   *                           (in OSGi: the Rahla bundle's class loader)
   */
  public GroovyBundleBuilder(ClassLoader compileClassLoader) {
    this.compileClassLoader = compileClassLoader;
  }

  /**
   * Result of {@link #build(URL)}: the bundle jar plus, for a placeholder, the packages the source
   * needs but the runtime does not provide yet.
   */
  public record GroovyBundle(byte[] bytes, Set<String> waitingFor) {
    public boolean isPlaceholder() {
      return !waitingFor.isEmpty();
    }
  }

  /**
   * Compiles the source into a bundle. If compilation fails only because classes from packages the
   * runtime does not provide (yet) cannot be resolved, a class-less <em>placeholder</em> bundle
   * with an {@code Import-Package} on those packages is returned instead: it stays unresolved in the
   * framework until the packages appear ({@code bundle:diag} names them), and can then be
   * {@code update()}d to obtain the real thing.
   *
   * @param source URL of a Groovy source file
   * @return an OSGi bundle jar containing the compiled classes, or a placeholder
   * @throws IOException if the source cannot be read
   * @throws IllegalStateException if the source does not compile for any other reason
   */
  public GroovyBundle build(URL source) throws IOException {
    String fileName = fileName(source);
    String text = read(source);
    String hash = sha256(text);
    try {
      List<GroovyClass> classes = compile(fileName, text);
      requirePackage(fileName, classes);
      Manifest manifest = manifest(source, fileName, hash, packagesOf(classes), Set.of());
      return new GroovyBundle(jar(manifest, classes), Set.of());
    } catch (CompilationFailedException e) {
      Set<String> missing = missingPackages(e, text);
      if (missing.isEmpty()) {
        throw new IllegalStateException("Compilation of " + fileName + " failed: " + e.getMessage(), e);
      }
      Manifest manifest = manifest(source, fileName, hash, Set.of(), missing);
      return new GroovyBundle(jar(manifest, List.of()), missing);
    }
  }

  private List<GroovyClass> compile(String fileName, String text) throws IOException {
    CompilerConfiguration config = new CompilerConfiguration();
    try (GroovyClassLoader loader = new GroovyClassLoader(compileClassLoader, config)) {
      CompilationUnit unit = new CompilationUnit(config, null, loader);
      unit.addSource(fileName, text);
      unit.compile(Phases.CLASS_GENERATION);
      return unit.getClasses();
    }
  }

  private static final Pattern UNRESOLVED_CLASS = Pattern.compile("unable to resolve class ([\\w.$]+)");
  private static final Pattern STAR_IMPORT = Pattern.compile("^\\s*import\\s+([\\w.]+)\\.\\*", Pattern.MULTILINE);

  /**
   * Packages to wait for, or empty if the failure is not (only) about unresolvable classes.
   * Fully qualified names yield their package; bare names fall back to the source's star imports.
   */
  static Set<String> missingPackages(CompilationFailedException e, String text) {
    if (!(e instanceof MultipleCompilationErrorsException mce)) {
      return Set.of();
    }
    Set<String> packages = new TreeSet<>();
    boolean bareNames = false;
    for (Message message : mce.getErrorCollector().getErrors()) {
      String description =
          message instanceof SyntaxErrorMessage sem ? sem.getCause().getMessage() : String.valueOf(message);
      Matcher m = UNRESOLVED_CLASS.matcher(description);
      if (!m.find()) {
        return Set.of();
      }
      String name = m.group(1);
      int dot = name.lastIndexOf('.');
      if (dot > 0) {
        packages.add(name.substring(0, dot));
      } else {
        bareNames = true;
      }
    }
    if (bareNames) {
      Matcher m = STAR_IMPORT.matcher(text);
      while (m.find()) {
        packages.add(m.group(1));
      }
    }
    packages.removeIf(p -> p.startsWith("java."));
    return packages;
  }

  private static Manifest manifest(
      URL source, String fileName, String hash, Set<String> packages, Set<String> waitingFor) {
    String baseName =
        fileName.endsWith(GROOVY_SUFFIX)
            ? fileName.substring(0, fileName.length() - GROOVY_SUFFIX.length())
            : fileName;
    Manifest manifest = new Manifest();
    Attributes a = manifest.getMainAttributes();
    a.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    a.putValue("Bundle-ManifestVersion", "2");
    a.putValue("Bundle-SymbolicName", SYMBOLIC_NAME_PREFIX + symbolicNameToken(baseName));
    a.putValue("Bundle-Name", "Groovy: " + fileName);
    a.putValue("Bundle-Version", "1.0.0." + hash.substring(0, 12));
    if (!packages.isEmpty()) {
      a.putValue("Export-Package", String.join(",", packages));
    }
    if (waitingFor.isEmpty()) {
      a.putValue(STATUS_HEADER, STATUS_COMPILED);
    } else {
      a.putValue("Import-Package", String.join(",", waitingFor));
      a.putValue(STATUS_HEADER, STATUS_UNRESOLVED);
      a.putValue("Bundle-Name", "Groovy: " + fileName + " (waiting for " + String.join(", ", waitingFor) + ")");
    }
    a.putValue("DynamicImport-Package", "*");
    a.putValue(SOURCE_HEADER, source.toString());
    return manifest;
  }

  private static byte[] jar(Manifest manifest, List<GroovyClass> classes) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (JarOutputStream jar = new JarOutputStream(out, manifest)) {
      for (GroovyClass clazz : classes) {
        jar.putNextEntry(new JarEntry(clazz.getName().replace('.', '/') + ".class"));
        jar.write(clazz.getBytes());
        jar.closeEntry();
      }
    }
    return out.toByteArray();
  }

  /** OSGi symbolic names allow only {@code [A-Za-z0-9_.-]}; everything else becomes {@code _}. */
  static String symbolicNameToken(String name) {
    return name.replaceAll("[^A-Za-z0-9_.-]", "_");
  }

  /**
   * A bundle can only export named packages, so classes in the default package would be invisible
   * to every other bundle — including the Blueprint context that wants to instantiate them. Failing
   * here turns a confusing "class not found" at route start-up into a clear message at deploy time.
   */
  private static void requirePackage(String fileName, List<GroovyClass> classes) {
    List<String> unnamed =
        classes.stream().map(GroovyClass::getName).filter(n -> n.indexOf('.') < 0).toList();
    if (!unnamed.isEmpty()) {
      throw new IllegalStateException(
          "Compilation of "
              + fileName
              + " failed: "
              + unnamed
              + " declare no package. Classes in the default package cannot be exported by a"
              + " bundle, so no other bundle could use them. Add a 'package ...' declaration.");
    }
  }

  private static Set<String> packagesOf(List<GroovyClass> classes) {
    Set<String> packages = new TreeSet<>();
    for (GroovyClass clazz : classes) {
      int dot = clazz.getName().lastIndexOf('.');
      if (dot > 0) {
        packages.add(clazz.getName().substring(0, dot));
      }
    }
    return packages;
  }

  private static String fileName(URL source) {
    String path = URLDecoder.decode(source.getPath(), StandardCharsets.UTF_8);
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private static String read(URL source) throws IOException {
    try (InputStream in = source.openStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String sha256(String text) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
