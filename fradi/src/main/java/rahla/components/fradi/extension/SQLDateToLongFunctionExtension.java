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

import java.sql.Timestamp;

@Extension(
    name = "sqlDateToLong",
    namespace = "extra",
    description = "Returns the ms in long of the input Date.",
    parameters = {
      @Parameter(
          name = "input.timestamp",
          description = "The input date to convert the the ms.",
          type = {DataType.OBJECT},
          dynamic = true)
    },
    parameterOverloads = {@ParameterOverload(parameterNames = {"input.timestamp"})},
    returnAttributes =
        @ReturnAttribute(
            description = "Outputs the length of the input string provided.",
            type = {DataType.LONG}),
    examples =
        @Example(syntax = "sqlDateToLong(0)", description = "This outputs the date 1-1-70 ."))
public class SQLDateToLongFunctionExtension extends FunctionExecutor {

  Attribute.Type returnType = Attribute.Type.LONG;

  @Override
  protected StateFactory<State> init(
      ExpressionExecutor[] expressionExecutors,
      ConfigReader configReader,
      SiddhiQueryContext siddhiQueryContext) {
    int executorsCount = expressionExecutors.length;

    if (executorsCount != 1) {
      throw new SiddhiAppValidationException(
          "Invalid no of arguments passed to str:length() function. "
              + "Required 1. Found "
              + executorsCount);
    }
    Attribute.Type type = expressionExecutors[0].getReturnType();
    if (type != Attribute.Type.OBJECT) {
      throw new SiddhiAppValidationException(
          "Invalid parameter type found for extra:sqlDateToLong() function, required "
              + Attribute.Type.OBJECT
              + ", but found "
              + type.toString());
    }
    return null;
  }

  @Override
  protected Object execute(Object[] objects, State state) {
    return null;
  }

  @Override
  protected Object execute(Object o, State state) {
    if (o == null) {
      return null;
    }
    return ((Timestamp) o).getTime();
  }

  @Override
  public Attribute.Type getReturnType() {
    return returnType;
  }
}
