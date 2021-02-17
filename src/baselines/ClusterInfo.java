/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package baselines;

import java.util.List;

/**
 *
 * @author suchana
 */

public class ClusterInfo {
    
    String qid;
    List<Float[]> lmClustInfo;
    List<Float[]> rlmClustInfo;
    
    public ClusterInfo() {  
        
    }
    
    public String getQid() {return qid;}
    public List getLmClustList() {return lmClustInfo;}
    public List getRlmClustList() {return rlmClustInfo;}
        
    public void setQid(String qid) {this.qid = qid;}
    public void setLmClustList() {this.lmClustInfo = lmClustInfo;}
    public void setRlmClustList() {this.rlmClustInfo = rlmClustInfo;}        
}
