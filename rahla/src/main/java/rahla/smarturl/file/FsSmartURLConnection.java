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

package rahla.smarturl.file;

import lombok.extern.log4j.Log4j2;
import rahla.smarturl.SmartURLConnection;
import rahla.smarturl.SmartURLStreamHandlerService;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

@Log4j2
public class FsSmartURLConnection extends SmartURLConnection {

  private final Path path;
  private final Path basePath;

  public FsSmartURLConnection(
      URL u, Path basePath, SmartURLStreamHandlerService smartUrlStreamHandlerService) {
    super(u, smartUrlStreamHandlerService);
    this.basePath = basePath;
    path = basePath.resolve(url.getHost()).resolve(url.getPath().substring(1));
  }

  @Override
  public void connect() throws IOException {}

  @Override
  public InputStream getInputStream() throws IOException {
    return new BufferedInputStream(Files.newInputStream(path));
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    Files.createDirectories(path.getParent());
    return new BufferedOutputStream(Files.newOutputStream(path));
  }

  public void delete() {
    try {
      Files.delete(path);
    } catch (IOException e) {
      log.error("Error deleting {}", path, e);
    }
  }

  @Override
  public void duplicate(URL url) {
    Path newPath = basePath.resolve(url.getHost()).resolve(url.getPath().substring(1));
    try {
      Files.createDirectories(newPath.getParent());
      Files.copy(path, newPath);
    } catch (IOException e) {
      log.error("Error duplicating {}", path, e);
      throw new RuntimeException(e);
    }
  }
}
