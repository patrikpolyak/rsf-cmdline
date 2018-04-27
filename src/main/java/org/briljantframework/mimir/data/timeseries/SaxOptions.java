package org.briljantframework.mimir.data.timeseries;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SaxOptions {
    private static Map<Integer, double[]> breakpoints = new HashMap<Integer, double[]>();
    private static double[][] distTable;
    private static int alphabetSize;
    private static int tsWordLength;
    private static int lowerWordLength;
    private static int upperWordLength;


    public static int getAlphabetSize() {
        return alphabetSize;
    }

    public static void setAlphabetSize(int alphabet_size) {
        SaxOptions.alphabetSize = alphabet_size;
    }

    public static int getTsWordLength() {
        return tsWordLength;
    }

    public static void setTsWordLength(int word_length) {
        SaxOptions.tsWordLength = word_length;
    }

    public static int getLowerWordLength() {
        return lowerWordLength;
    }

    public static void setLowerWordLength(int lowerWordLength) {
        SaxOptions.lowerWordLength = lowerWordLength;
    }

    public static double[][] getDistTable() {
        return distTable;
    }

    public static void generateDistTable(int alphabetSize) {
        distTable = new double[alphabetSize][alphabetSize];
        double[] breakpointTable = SaxOptions.Breakpoint.get(alphabetSize);
        for (int r = 0; r < alphabetSize; r++) {
            for (int c = 0; c < alphabetSize; c++) {
                if (Math.abs(r-c) <= 1) {
                    distTable[r][c] = 0;
                } else {
                    distTable[r][c] = breakpointTable[(Math.max(r, c) - 1)] - breakpointTable[(Math.min(r, c) - 0)];
                }
            }
        }
    }

    public static int getUpperWordLength() {
        return upperWordLength;
    }

    public static void setUpperWordLength(int upperWordLength) {
        SaxOptions.upperWordLength = upperWordLength;
    }

    /*
    Could be replaced by the following as suggested by http://www.cs.ucr.edu/~eamonn/SAX.htm
    startRange = 2;
    stdc= 1;
    endRange = 512;

    table = cell(endRange-startRange,1);
    for r=startRange:endRange
        table{r-startRange+1} = norminv((1:r-1)/r,0,stdc);
    end
     */
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
