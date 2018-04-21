package org.briljantframework.mimir.distance;

import org.briljantframework.DoubleSequence;
import org.briljantframework.mimir.data.timeseries.SaxOptions;
import org.briljantframework.mimir.data.timeseries.TimeSeries;
import org.briljantframework.mimir.shapelet.Shapelet;

/**
 * Created by Patrik Polyak on 21/04/18.
 */
public class SlidingSAXDistance implements Distance<DoubleSequence> {

    @Override
    public double compute(DoubleSequence a, DoubleSequence b) {
        int[] ts = ((TimeSeries) a).getSaxWord();
        int[] shapelet = ((Shapelet) b).getSaxWord();
        double[][] distTable = SaxOptions.getDistTable();
        int iter = ts.length - shapelet.length + 1;
        double minDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < iter; i++) {
            double dist = 0.0;
            for (int j = 0; j < shapelet.length; j++) {
                dist += distTable[ts[i+j]][shapelet[j]];
            }
            if (dist == 0.0) {
                return dist;
            }
            if (dist < minDist) {
                minDist = dist;
            }
        }
        return minDist;
    }
}
