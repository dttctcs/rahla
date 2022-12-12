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

import org.apache.karaf.log.core.LogEventFormatter;
import org.apache.karaf.log.core.LogService;
import org.apache.karaf.shell.api.action.*;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.ops4j.pax.logging.spi.PaxLoggingEvent;

import java.io.PrintStream;


@Command(scope = "log", name = "logs", description = "Displays log entries.")
@Service
public class ShowCommand implements Action {


  public final static int ERROR_INT = 3;
  public final static int WARN_INT  = 4;
  public final static int INFO_INT  = 6;
  public final static int DEBUG_INT = 7;

  private final static String SSHD_LOGGER = "org.apache.sshd";

  @Option(name = "-n", aliases = {}, description="Number of entries to display", required = false, multiValued = false)
  int entries;

  @Option(name = "-p", aliases = {}, description="Pattern for formatting the output", required = false, multiValued = false)
  String overridenPattern;

  @Option(name = "--no-color", description="Disable syntax coloring of log events", required = false, multiValued = false)
  boolean noColor;

  @Option(name = "-l", aliases = { "--level" }, description = "The minimal log level to display", required = false, multiValued = false)
  @Completion(value = StringsCompleter.class, values = { "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "DEFAULT" })
  String level;

  @Argument(index = 0, name = "logger", description = "The name of the logger. This can be ROOT, ALL, or the name of a logger specified in the org.ops4j.pax.logger.cfg file.", required = false, multiValued = false)
  String logger;

  @Reference
  LogService logService;

  @Reference
   LogEventFormatter formatter;

  @Override
  public Object execute() throws Exception {
    final PrintStream out = System.out;
    int minLevel = getMinLevel(level);
    display(out, minLevel);
    out.println();
    return null;
  }

  protected void display(final PrintStream out, int minLevel) {
    Iterable<PaxLoggingEvent> le = logService.getEvents(entries == 0 ? Integer.MAX_VALUE : entries);
    for (PaxLoggingEvent event : le) {
      printEvent(out, event, minLevel);
    }
  }

  protected static int getMinLevel(String levelSt) {
    int minLevel = Integer.MAX_VALUE;
    if (levelSt != null) {
      switch (levelSt.toLowerCase()) {
        case "debug": minLevel = DEBUG_INT; break;
        case "info":  minLevel = INFO_INT; break;
        case "warn":  minLevel = WARN_INT; break;
        case "error": minLevel = ERROR_INT; break;
      }
    }
    return minLevel;
  }

  protected boolean checkIfFromRequestedLog(PaxLoggingEvent event) {
    return event.getLoggerName().contains(logger);
  }

  protected void printEvent(PrintStream out, PaxLoggingEvent event, int minLevel) {
    try {
      if (event != null) {
        int sl = event.getLevel().getSyslogEquivalent();
        if (sl <= minLevel) {
          printEvent(out, event);
        }
      }
    } catch (NoClassDefFoundError e) {
      // KARAF-3350: Ignore NoClassDefFoundError exceptions
      // Those exceptions may happen if the underlying pax-logging service
      // bundle has been refreshed somehow.
    }
  }

  protected void printEvent(final PrintStream out, PaxLoggingEvent event) {
    if ((logger != null) &&
            (event != null) &&
            (checkIfFromRequestedLog(event))) {
      out.append(formatter.format(event, overridenPattern, noColor));
    } else if ((event != null) && (logger == null)) {
      out.append(formatter.format(event, overridenPattern, noColor));
    }
    out.flush();
  }
}