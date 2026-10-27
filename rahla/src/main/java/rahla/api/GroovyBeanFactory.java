package rahla.api;

/**
 * Compiles a Groovy class loaded from a URL and instantiates it as a bean.
 * <p>
 * The bean class may declare either a no-arg constructor or a single-arg constructor
 * accepting an {@link org.osgi.framework.BundleContext}; the latter is preferred when
 * present so the bean can interact with the OSGi runtime.
 *
 * @deprecated since 1.3.6. Drop the {@code *.groovy} file directly into the deploy directory
 *     instead: {@code rahla.groovy.GroovyBundleDeployer} compiles it into an OSGi bundle and the
 *     class is wired as a plain {@code <bean class="my.pkg.MyProcessor"/>} (the source must
 *     declare a package). A bean that needs the {@code BundleContext} gets it via
 *     {@code FrameworkUtil.getBundle(getClass()).getBundleContext()}. Every {@link #createBean}
 *     call logs a deprecation warning. Will be removed in a future release.
 */
@Deprecated
public interface GroovyBeanFactory {

    /**
     * Loads, compiles and instantiates a Groovy class.
     *
     * @param urlSpec a URL pointing to the Groovy source (e.g. {@code file:///...},
     *                {@code http://...}). The legacy {@code resource:file:} prefix is
     *                still accepted but deprecated.
     * @return a new bean instance
     * @throws IllegalStateException if the source cannot be read, compiled or instantiated
     * @deprecated see the interface: use a {@code *.groovy} in the deploy directory and
     *     {@code <bean class="..."/>}
     */
    @Deprecated
    Object createBean(String urlSpec);
}
