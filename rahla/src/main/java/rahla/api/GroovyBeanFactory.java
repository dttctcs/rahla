package rahla.api;

/**
 * Compiles a Groovy class loaded from a URL and instantiates it as a bean.
 * <p>
 * The bean class may declare either a no-arg constructor or a single-arg constructor
 * accepting an {@link org.osgi.framework.BundleContext}; the latter is preferred when
 * present so the bean can interact with the OSGi runtime.
 */
public interface GroovyBeanFactory {

    /**
     * Loads, compiles and instantiates a Groovy class.
     *
     * @param urlSpec a URL pointing to the Groovy source (e.g. {@code file:///...},
     *                {@code http://...}). The legacy {@code resource:file:} prefix is
     *                still accepted but deprecated.
     * @return a new bean instance
     * @throws IllegalStateException if the source cannot be read, compiled or instantiated
     */
    Object createBean(String urlSpec);
}
