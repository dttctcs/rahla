package rahla.components.fradi;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class FradiComponentTest extends CamelTestSupport {


    public void testfradi() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(5);

        // Trigger events to subscribers
        simulateEventTrigger();
        //TODO implement unit tests
        mock.await();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("fradi://foo")
                  .to("fradi://bar")
                  .to("mock:result");
            }
        };
    }

    private void simulateEventTrigger() {

    }
}
