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
package org.briljantframework.mimir.data.timeseries;

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.briljantframework.DoubleSequence;
import org.briljantframework.data.Collectors;
import org.briljantframework.data.series.Convert;
import org.briljantframework.data.series.Series;

import java.util.*;

/**
 * Created by isak on 5/11/16.
 */
public class TimeSeries implements DoubleSequence {

  private double[] buffer;

  private int[] saxWord;

  private TimeSeries(double[] buffer) {
    this.buffer = buffer;
    this.saxWord = convertSax(buffer);
  }

  public static TimeSeries copyOf(Collection<?> data) {
    if (data instanceof Series) {
      return copyOf((Series) data);
    }
    double[] buffer = new double[data.size()];
    int i = 0;
    for (Object o : data) {
      buffer[i++] = Convert.to(Double.class, o);
    }
    return new TimeSeries(buffer);
  }

  public static TimeSeries copyOf(Series data) {
    double[] buffer = new double[data.size()];
    for (int i = 0; i < data.size(); i++) {
      buffer[i] = data.values().getDouble(i);
    }
    return new TimeSeries(buffer);
  }

  public static TimeSeries of(double... data) {
    return new TimeSeries(data);
  }

  public static TimeSeries normalizedCopyOf(Series data) {
    StatisticalSummary summary = data.collect(Double.class, Collectors.statisticalSummary());
    double[] buffer = new double[data.size()];
    for (int i = 0; i < data.size(); i++) {
      buffer[i] = (data.values().getDouble(i) - summary.getMean()) / summary.getStandardDeviation();
    }
    return new TimeSeries(buffer);
  }

  private int[] convertSax(double[] ts) {
    int wordLen = SaxOptions.getWordLength();
    int card = SaxOptions.getAlphabetSize();
    double rem = Math.IEEEremainder(ts.length, wordLen);
    double[] PAA = new double[wordLen];
    if (rem == 0) {
      PAA = getPAA(ts, wordLen);
    } else {
      // If the wordLen is not divisible by the length of time series, then
      // find their GCD (greatest common divisor) and duplicate the time
      // series by this much (one number at a time).
      int lcm = GetLCM(ts, wordLen);
      double[] ts_dup = DupArray(ts, lcm / ts.length);
      PAA = getPAA(ts_dup, wordLen);
    }
    return GetSymbol(PAA, wordLen, card);
  }

  private static double[] getPAA(double[] ts, int num_seg) {
    if (Math.IEEEremainder(ts.length, num_seg) != 0 ) {
      System.out.println("ERR: Data length not divisible by number of segments!");
      System.exit(0);
    }
    // Determine the segment size
    int segment_size = ts.length / num_seg;
    int offset = 0;
    double[] PAA = new double[num_seg];

    // if no dimensionality reduction, then just copy the data
    if (num_seg == ts.length) {
      PAA = ts;
    }

    for (int i = 0; i < num_seg; i++) {
      PAA[i] = getMean(ts, offset, offset + segment_size - 1);
      offset += segment_size;
    }
    return PAA;
  }

  public static double getMean(double[] data, int index1, int index2)
  {
    //try
    //{
    if (index1 < 0 || index2 < 0 || index1 >= data.length ||
            index2 >= data.length)
    {
      System.out.println("ERR: Invalid index!");
      System.exit(0);
    }
    //}

    if (index1 > index2)
    {
      int temp = index2;
      index2 = index1;
      index1 = temp;
    }

    double sum = 0;

    for (int i = index1; i <= index2; i++)
    {
      sum += data[i];
    }

    return sum / (index2 - index1 + 1);
  }

  private static int[] GetSymbol(double[] PAA, int num_seg, int alphabet_size)
  {
    boolean FOUND = false;
    int[] symbols = new int[num_seg];
    for (int i = 0; i < num_seg; i++)
    {
      for (int j = 0; j < alphabet_size - 1; j++)
      {
        if (PAA[i] <= SaxOptions.Breakpoint.get(alphabet_size)[j])
        {
          symbols[i] = j;// lowest val = 1, card 4 - 4th-j(3) = 1
          //		symbols[i] = (byte)(j + 1);
          FOUND = true;
          break;
        }
      }

      if (!FOUND)
      {
        //					symbols[i] = (byte)alphabet_size;
        symbols[i] = (alphabet_size - 1);
      }
      FOUND = false;
    }
    return symbols;
  }

  // Get the GCD (greatest common divisor) between the
  // length of the time series and the number of PAA
  // segments
  private static int GetGCD(double[] time_series, int num_seg)
  {
    int u = time_series.length;
    int v = num_seg;
    int div;
    int divisible_check;

    while (v > 0)
    {
      div = (int)Math.floor((double)u / (double)v);
      divisible_check = u - v * div;
      u = v;
      v = divisible_check;
    }
    return u;
  }

  // Get the least common multiple of the length of the time series and the
  // number of segments
  private static int GetLCM(double[] time_series, int num_seg)
  {
    int gcd = GetGCD(time_series, num_seg);
    int len = time_series.length;
    int n = num_seg;
    return (len * (n / gcd));
  }

  // Make dup copies of each array element (one at a time)
  public static double[] DupArray(double[] data, int dup)
  {
    int cur_index = 0;
    double[] dup_array = new double[data.length * dup];

    for (int i = 0; i < data.length; i++)
    {
      for (int j = 0; j < dup; j++)
      {
        dup_array[cur_index + j] = data[i];
      }

      cur_index += dup;
    }
    return dup_array;
  }


  @Override
  public int size() {
    return buffer.length;
  }

  @Override
  public double getDouble(int index) {
    return buffer[index];
  }

  @Override
  public String toString() {
    return Arrays.toString(buffer);
  }

  public int[] getSaxWord() { return this.saxWord; }
}
