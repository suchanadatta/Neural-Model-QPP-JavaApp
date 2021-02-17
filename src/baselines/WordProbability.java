/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package baselines;

/**
 *
 * @author suchana
 */

class WordProbability {
    String w;
    float expansionWeight;  // 
    float p_w_given_Q;      // this is the weight after incorporating the idf proba. of w given Q (withour idf)

    public WordProbability() {
    }

    public WordProbability(String w, float p_w_given_Q) {
        this.w = w;
        this.p_w_given_Q = p_w_given_Q;
    }

    public WordProbability(String w, float p_w_given_Q, float expansionWeight) {
        this.w = w;
        this.expansionWeight = expansionWeight;
        this.p_w_given_Q = p_w_given_Q;
    }

}
