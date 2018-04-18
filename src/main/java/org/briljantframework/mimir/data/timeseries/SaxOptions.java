package org.briljantframework.mimir.data.timeseries;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SaxOptions {
    private static Map<Integer, double[]> breakpoints = new HashMap<Integer, double[]>();
    private static int alphabetSize;
    private static int wordLength;

    public static int getAlphabetSize() {
        return alphabetSize;
    }

    public static void setAlphabetSize(int alphabet_size) {
        SaxOptions.alphabetSize = alphabet_size;
    }

    public static int getWordLength() {
        return wordLength;
    }

    public static void setWordLength(int word_length) {
        SaxOptions.wordLength = word_length;
    }

    public static class Breakpoint {
        static {
            Integer index;
            double[] values;
            try {
                String breakpointsFilename = "breakpoints.csv";
                FileReader csvReader = null;
                csvReader = new FileReader(breakpointsFilename);
                BufferedReader bufferedReader = new BufferedReader(csvReader);
                String line = null;
                while ((line = bufferedReader.readLine()) != null){
                    String[] lineData = line.split(",");
                    index = Integer.valueOf(lineData[0]);
                    values = new double[lineData.length-1];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = Double.parseDouble(lineData[i+1]);
                    }
                    breakpoints.put(index, values);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public static double[] get(int alphabet_size) {
            return breakpoints.get(alphabet_size);
        }
    }
}
