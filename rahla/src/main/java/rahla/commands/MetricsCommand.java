/*
 * MIT License
 *
 * Copyright © 2020 Matthias Leinweber datatactics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package rahla.commands;

import io.prometheus.client.Collector;
import java.util.Enumeration;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import rahla.api.CollectorRegistryService;


@Service
@Command(scope = "rahla", name = "metrics", description = "Show the current prometheus metrics")
public class MetricsCommand implements Action {

  @Reference
  private CollectorRegistryService registryService;

  @Override
  public Object execute() throws Exception {
    Enumeration<Collector.MetricFamilySamples> samples = registryService.getRegistry().metricFamilySamples();
    while (samples.hasMoreElements()) {
      Collector.MetricFamilySamples metricFamilySamples = samples.nextElement();
      for (Collector.MetricFamilySamples.Sample sample : metricFamilySamples.samples) {
        StringBuilder labels = new StringBuilder();
        if (sample.labelNames.size() > 0) {
          labels.append("{");
        }
        for (int i = 0; i < sample.labelNames.size(); i++) {
          labels
              .append(sample.labelNames.get(i))
              .append("=\"")
              .append(sample.labelValues.get(i))
              .append("\"");
          if (i + 1 < sample.labelNames.size()) {
            labels.append(", ");
          }
        }
        if (sample.labelNames.size() > 0) {
          labels.append("}");
        }
        System.out.println(sample.name + labels + " " + sample.value);
      }
    }
    return null;
  }
}
