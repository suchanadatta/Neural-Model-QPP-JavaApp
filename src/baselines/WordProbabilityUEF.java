/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package baselines;

/**
 *
 * @author dwaipayan
 */

class WordProbabilityUEF {
    String w;
    float expansionWeight;  // 
    float p_w_given_R;      // this is the weight after incorporating the idf proba. of w given R (withour idf)

    public WordProbabilityUEF() {
    }

    public WordProbabilityUEF(String w, float p_w_given_R) {
        this.w = w;
        this.p_w_given_R = p_w_given_R;
    }

    public WordProbabilityUEF(String w, float p_w_given_R, float expansionWeight) {
        this.w = w;
        this.expansionWeight = expansionWeight;
        this.p_w_given_R = p_w_given_R;
    }

}
