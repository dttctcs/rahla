package rahla.graphsource;

import java.util.List;

public interface GraphSource<T, V> {

  List<String> getHosts();

  String getUser();

  V getClient();

  T getGraphTraversalSource();
}