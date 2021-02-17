package baselines;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;


public class SpearmansCorrelation {
    
    UEFSingle uefs;
    UEFMultiCluster uefmc;

    public SpearmansCorrelation(UEFSingle uefs) {
        this.uefs = uefs;
    }
    
    public SpearmansCorrelation(UEFMultiCluster uefmc) {
        this.uefmc = uefmc;
    }
    
    /* Calculates Spearman's rank correlation coefficient, */
    public Float spearman(HashMap lm, HashMap rlm) {
        
        float [] lmArray = new float[lm.size()];
        float [] rlmArray = new float[rlm.size()];
        
//        System.out.println("$$$$ : " + lm);
        
        for(int i = 0; i < lm.size(); i++) {
            lmArray[i] = (float) lm.get(i);
//            System.out.println("#### : " + lmArray[i]);
        }
//        System.out.println("LMARRAY : " + lmArray);
        
        for(int i = 0; i < rlm.size(); i++) {
            rlmArray[i] = (float) rlm.get(i);
        }
//        System.out.println("RLMARRAY : " + rlmArray);
        
        /* Create Rank arrays */
        int [] rankX = getRanks(lmArray);
        int [] rankY = getRanks(rlmArray);

        /* Apply Spearman's formula */
        int n = lmArray.length;
        float numerator = 0;
        for (int i = 0; i < n; i++) {
            numerator += Math.pow((rankX[i] - rankY[i]), 2);
        }
        numerator *= 6;
//        System.out.println("spearman : " + (1 - numerator / (n * ((n * n) - 1))));
        
        return 1 - numerator / (n * ((n * n) - 1));
    }
    
    /* Returns a new array with ranks. Assumes unique array values. */
    public int[] getRanks(float [] array) {
        int n = array.length;
        
        /* Create Pair[] and sort by values */
        Pair [] pair = new Pair[n];
        for (int i = 0; i < n; i++) {
            pair[i] = new Pair(i, array[i]);
        }
        Arrays.sort(pair, new PairValueComparator());

        /* Create and return ranks[] */
        int [] ranks = new int[n];
        int rank = 1;
        for (Pair p : pair) {
            ranks[p.index] = rank++;
        }
        return ranks;
    }
    
    /* A class to store 2 variables */
    public class Pair {
        public final int index;
        public final float value;
 
        public Pair(int i, float v) {
            index = i;
            value = v;
        }
    }

    /* This lets us sort Pairs based on their value field */
    public class PairValueComparator implements Comparator<Pair> {
        @Override
        public int compare(Pair p1, Pair p2) {
            if (p1.value < p2.value) {
                return -1;
            } else if (p1.value > p2.value) {
                return 1;
            } else {
                return 0;
            }
        }
    }
}