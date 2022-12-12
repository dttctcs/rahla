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

package rahla.components.fradi.extension;

import io.siddhi.annotation.*;
import io.siddhi.annotation.util.DataType;
import io.siddhi.core.config.SiddhiQueryContext;
import io.siddhi.core.executor.ExpressionExecutor;
import io.siddhi.core.executor.function.FunctionExecutor;
import io.siddhi.core.util.config.ConfigReader;
import io.siddhi.core.util.snapshot.state.State;
import io.siddhi.core.util.snapshot.state.StateFactory;
import io.siddhi.query.api.definition.Attribute;
import io.siddhi.query.api.exception.SiddhiAppValidationException;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

@Extension(
        name = "hexsha256",
        namespace = "extra",
        description =
                "This function returns a base64 hex value that the hexsha256 result of the inputs.",
        parameters = {
                @Parameter(
                        name = "arg",
                        description = "This can have one or more input parameters.",
                        type = {DataType.INT, DataType.LONG, DataType.DOUBLE, DataType.FLOAT, DataType.STRING},
                        dynamic = true)
        },
        parameterOverloads = {@ParameterOverload(parameterNames = {"arg", "..."})},
        returnAttributes =
        @ReturnAttribute(
                description = "This is the string that is returned on hasing the input base64 encoded.",
                type = {DataType.STRING}),
        examples =
        @Example(
                syntax = "concat(\"D533\", \"8JU^\", \"XYZ\")",
                description =
                        "This returns a string value by concatenating two or more given arguments. "
                                + "In the example shown above, it returns \"D5338JU^XYZ\"."))
public class SHA256FunctionExtension extends FunctionExecutor {

  private final ByteBuffer buffer4 = ByteBuffer.allocate(4);
  private final ByteBuffer buffer8 = ByteBuffer.allocate(8);
  private MessageDigest md;

  @Override
  protected StateFactory<State> init(
          ExpressionExecutor[] expressionExecutors,
          ConfigReader configReader,
          SiddhiQueryContext siddhiQueryContext) {
    int executorsCount = expressionExecutors.length;

    if (executorsCount < 1) {
      throw new SiddhiAppValidationException(
              "extra:hexsha256() function requires at least two arguments, but found only "
                      + executorsCount);
    }

    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new SiddhiAppValidationException(e);
    }
    return null;
  }

  @Override
  protected Object execute(Object[] objects, State state) {
    md.reset();
    for (Object o : objects) {
      if (o == null) {
        continue;
      }
      switch (o.getClass().getSimpleName()) {
        case "Integer":
          md.update(toBytes((Integer) o));
          break;
        case "Long":
          md.update(toBytes((Long) o));
          break;
        case "Double":
          md.update(toBytes((Double) o));
          break;
        case "Float":
          md.update(toBytes((Float) o));
          break;
        case "String":
          md.update(((String) o).getBytes());
          break;
        default:
          throw new IllegalStateException("Unexpected value: " + o.getClass().getSimpleName());
      }
    }

    return toHex(md.digest()).toUpperCase(Locale.ROOT);
  }

  private static String toHex(byte[] data) {
    StringBuilder sb = new StringBuilder(64);
    for (byte b : data) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  @Override
  protected Object execute(Object o, State state) {
    return execute(new Object[]{o}, state);
  }

  @Override
  public Attribute.Type getReturnType() {
    return Attribute.Type.STRING;
  }

  private final String toHex2(byte[] data) {
    StringBuilder sb = new StringBuilder(64);
    for (byte b : data) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }


  public final byte[] toBytes(long l) {
    final int magic = Long.SIZE / Byte.SIZE;
    byte[] result = new byte[magic];
    for (int i = magic - 1; i >= 0; i--) {
      result[i] = (byte) (l & 0xFF);
      l >>= magic;
    }
    return result;
  }

  public final byte[] toBytes(int l) {
    final int magic = Integer.SIZE / Byte.SIZE;
    byte[] result = new byte[magic];
    for (int i = magic - 1; i >= 0; i--) {
      result[i] = (byte) (l & 0xFF);
      l >>= magic;
    }
    return result;
  }

  public byte[] toBytes(float l) {
    return buffer4.putFloat(0, l).array();
  }

  public byte[] toBytes(double d) {
    return buffer8.putDouble(0, d).array();
  }
}
