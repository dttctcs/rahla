import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import groovy.xml.*;
import groovy.sql.*;
import groovy.json.*;
import groovy.util.*;

public class MyProcessor implements Processor {
  void process(Exchange exchange) throws Exception {
    println("WootWoot")
  }
}