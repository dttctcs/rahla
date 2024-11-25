package rahla.components.fradi;

import io.siddhi.core.util.error.handler.exception.ErrorStoreException;
import io.siddhi.core.util.error.handler.model.ErrorEntry;
import io.siddhi.core.util.error.handler.store.ErrorStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.Map;

class LogErrorStore extends ErrorStore {
  private static final Logger log = LogManager.getLogger();

  @Override
  public void setProperties(Map properties) {

  }

  @Override
  protected void saveEntry(long timestamp, String siddhiAppName, String streamName, byte[] eventAsBytes, String cause, byte[] stackTraceAsBytes, byte[] originalPayloadAsBytes, String errorOccurrence, String eventType, String errorType) throws ErrorStoreException {
    log.error("ErrorStore: timestamp={}, siddhiAppName={}, streamName={}, cause={}, errorOccurrence={}", new Date(timestamp), siddhiAppName, streamName, cause, errorOccurrence);
  }

  @Override
  public List<ErrorEntry> loadErrorEntries(String siddhiAppName, Map<String, String> queryParams) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ErrorEntry loadErrorEntry(int id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void discardErrorEntry(int id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void discardErrorEntries(String siddhiAppName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getTotalErrorEntriesCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getErrorEntriesCount(String siddhiAppName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void purge(Map retentionPolicyParams) {
    throw new UnsupportedOperationException();
  }
}
