/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

// proper commenting required

package baselines;

import java.util.HashMap;
import java.util.List;

/**
 *
 * @author suchana
 */

public class PerQueryStat {
    
    /**
     * 
     */
    public String  qid;

    /**
     * 
     */
    public String[] qTerms;
    
    public double docFreq;

    /**
     * 
     */
    public HashMap<String, Double> w_t_bar;
    
    /**
     * 
     *  
     */
    public HashMap<String, List<Double>> w_d_t_list;

    public String getQid() {return qid;}
    public String[] getQtermList() {return qTerms;}
    public double getDocFreq() {return docFreq;}
    public HashMap<String, Double> getWTBar() {return w_t_bar;}
    public HashMap<String, List<Double>> getTfList() {return w_d_t_list;}
    
    public void setQid(String qid) {this.qid = qid;}
    public void setQtermList(String[] qTerms) {this.qTerms = qTerms;}
    public void setDocFreq() {this.docFreq = docFreq;}
    public void setWTBar(HashMap<String, Double> w_t_bar) {this.w_t_bar = w_t_bar;}
    public void setTfList(HashMap<String, List<Double>> w_d_t_list) {this.w_d_t_list = w_d_t_list;}

    public PerQueryStat() {  
        
    }
}
