package janussource.api;

import java.util.List;

public interface JanusSource<T, V> {

  List<String> getHosts();

  String getUser();

  V getClient();

  T getGraphTraversalSource();
}