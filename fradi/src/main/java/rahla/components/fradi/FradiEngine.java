package rahla.components.fradi;

import io.siddhi.core.SiddhiAppRuntime;
import io.siddhi.core.SiddhiManager;
import io.siddhi.core.stream.input.InputHandler;
import io.siddhi.core.stream.output.StreamCallback;
import io.siddhi.core.table.Table;
import io.siddhi.query.api.definition.StreamDefinition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class FradiEngine {
  private SiddhiAppRuntime siddhiAppRuntime = null;
  private SiddhiManager siddhiManager;
  private static final Logger log = LogManager.getLogger();

  public FradiEngine() {
    initShiddhiManager();
  }

  public void init(String plan) {
    siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(plan);
    siddhiAppRuntime.handleExceptionWith(new LogExceptionHandler(siddhiAppRuntime.getName()));
    siddhiAppRuntime.handleRuntimeExceptionWith(e -> {
      log.error("app={}, action=fradi process, reason={}", siddhiAppRuntime.getName(), e.getMessage(), e);
      throw new FradiException(e);
    });
    log.info("siddhiAppRuntime_created={}", siddhiAppRuntime.getName());
  }

  public void start() {
    siddhiAppRuntime.start();
    for (Table table : siddhiAppRuntime.getTables()) {
      if (!table.getIsConnected()) {
        siddhiAppRuntime.shutdown();
        siddhiManager.shutdown();
        throw new RuntimeException(siddhiAppRuntime.getName() + ": Table not connected: " + table.getTableDefinition().getId());
      }
    }
    log.info("siddhiAppRuntime_started={}", siddhiAppRuntime.getName());
  }

  private void initShiddhiManager() {
    siddhiManager = new SiddhiManager();
    siddhiManager.setErrorStore(new LogErrorStore());
    if (!siddhiManager.getExtensions().containsKey("store:rdbms"))
      throw new RuntimeException("Extension store:rdbms is missing!");
  }


  public void shutdown() {
    if (siddhiAppRuntime != null) {
      siddhiAppRuntime.shutdown();
      log.info("siddhiAppRuntime_shutdown={}", siddhiAppRuntime.getName());
    }
    if (siddhiManager != null) {
      siddhiManager.shutdown();
    }
  }

  public InputHandler getInputHandler(String streamId) {
    if (!siddhiAppRuntime.getStreamDefinitionMap().containsKey(streamId)) {
      throw new RuntimeException(siddhiAppRuntime.getName() + ": stream with id " + streamId + " does not exist.");
    }
    return siddhiAppRuntime.getInputHandler(streamId);
  }


  public StreamDefinition getStreamDefinition(String streamId) {
    if (!siddhiAppRuntime.getStreamDefinitionMap().containsKey(streamId)) {
      throw new RuntimeException(siddhiAppRuntime.getName() + ": stream with id " + streamId + " does not exist.");
    }
    return siddhiAppRuntime.getStreamDefinitionMap().get(streamId);
  }

  public void addCallback(String streamId, StreamCallback cb) {
    if (!siddhiAppRuntime.getStreamDefinitionMap().containsKey(streamId)) {
      throw new RuntimeException(siddhiAppRuntime.getName() + ": stream with id " + streamId + " does not exist.");
    }
    siddhiAppRuntime.addCallback(streamId, cb);
  }

  public void removeCallback(StreamCallback cb) {
    siddhiAppRuntime.removeCallback(cb);
  }

}
