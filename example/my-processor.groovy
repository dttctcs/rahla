import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.osgi.framework.BundleContext
import org.osgi.util.tracker.ServiceTracker

import javax.sql.DataSource
import java.util.logging.Logger

class MyProcessor implements Processor {
    Logger logger = Logger.getLogger("foo")
    def tracker;

    MyProcessor(BundleContext bundleContext) {
        logger.info("Hi" + bundleContext)
        tracker = new ServiceTracker<>(bundleContext, DataSource, null)
        tracker.open()

    }

    void process(Exchange exchange) throws Exception {
        logger.info("I am a test info log")
      exchange.getIn().setBody(Collections.singletonMap("Hallo", "Welt"))
    }
}
