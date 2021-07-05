import org.apache.camel.Exchange
import org.apache.camel.Processor
import groovy.xml.*
import groovy.sql.*
import groovy.json.*
import groovy.util.*

import java.util.logging.Logger

class MyProcessor implements Processor {
  Logger logger = Logger.getLogger("foo")

  void process(Exchange exchange) throws Exception {
    logger.info ("I am a test info log")
  }
}
