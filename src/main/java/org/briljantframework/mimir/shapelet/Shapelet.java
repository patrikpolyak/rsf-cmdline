/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Isak Karlsson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.briljantframework.mimir.shapelet;

import org.briljantframework.DoubleSequence;
import org.briljantframework.mimir.data.timeseries.Sax;
import org.briljantframework.mimir.data.timeseries.SaxOptions;
import org.briljantframework.mimir.data.timeseries.TimeSeries;

import java.util.Arrays;

/**
 * A Shapelet is a (short) view of a larger data series (i.e. a vector). The underlying vector
 * should not be mutated (as this will change the view).
 * <p>
 *
 * <pre>
 *     Shapelet shapelet = Shapelet.create(10, 10, frame.getEntry(10))
 * </pre>
 * <p>
 * creates a short view of the 10-th entry
 * <p>
 * <p>
 *
 * @author Isak Karlsson
 */
// TODO: override getAs... to support the changed indexing
public class Shapelet implements DoubleSequence {

  private final DoubleSequence timeSeries;
  private final int start, length;
  private int[] saxWord;

  public Shapelet(int start, int length, DoubleSequence timeSeries) {
    this.start = start;
    this.length = length;
    this.timeSeries = timeSeries;
    double[] tsArray = ((TimeSeries) timeSeries).getTimeSeries();
    double[] shapelet = Arrays.copyOfRange(tsArray, start, start + length);
    this.saxWord = Sax.convertSax(shapelet, SaxOptions.getShWordLength());
  }

  /**
   * Gets start.
   *
   * @return the start
   */
  public int start() {
    return start;
  }

  @Override
  public int size() {
    return length;
  }

  @Override
  public double getDouble(int index) {
    return timeSeries.getDouble(start + index);
  }

  // @Override
  // public Series reindex(Index index) {
  // Series n = parent.copy();
  // n.setIndex(index);
  // return new Shapelet(start, length, n);
  // }

  // @Override
  // protected double getDoubleElement(int i) {
  // return parent.loc().getDouble(start + i);
  // }

  // @Override
  // public String toString() {
  // List<String> r = new ArrayList<>();
  // for (int i = 0; i < size(); i++) {
  // r.add(getStringElement(i));
  // }
  // return String.format("Shapelet(%s, shape=(%d, 1))", r, size());
  // }

  public int[] getSaxWord() { return this.saxWord; }
}
