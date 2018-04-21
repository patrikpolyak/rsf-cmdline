package org.briljantframework.mimir.distance;

import org.briljantframework.DoubleSequence;

/**
 * Created by Patrik Polyak on 21/04/18.
 */
public class SlidingSAXDistance implements Distance<DoubleSequence> {

    // TODO: implement Sliding SAX distance calculation
    @Override
    public double compute(DoubleSequence a, DoubleSequence b) {
        System.out.println(a);
        System.out.println(b);
        return 0;
    }
}
