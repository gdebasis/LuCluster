/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author Debasis
 * Reads in an index and evaluates the clustering...
 * Reads the cluster ids from the field cluster id...
 * The field domain_id is taken as the reference labels...
 */

public final class ClusterEvaluator {
    IndexReader reader;  // the combined index to search
    Properties prop;
    int numDocs;
    int[][] clusterClassMatrix;
    int K;
    int J;
    int classCardinalities[];
    int clusterCardinalities[]; 
    HashMap<Integer, Integer> clusterIdMap;
    
    public ClusterEvaluator(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));        
        init(prop);
    }
    
    public ClusterEvaluator(Properties prop) throws Exception  {
        this.prop = prop;
        init(prop);
    }
    
    void initClusterIdMap() throws Exception {
        FileReader fr = new FileReader(prop.getProperty("cluster.idfile"));
        BufferedReader br = new BufferedReader(fr);
        String line;
        
        clusterIdMap = new HashMap<>();
        
        while ((line = br.readLine()) != null) {
            String[] tokens = line.split("\t");
            clusterIdMap.put(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));            
        }
        
        br.close();
        fr.close();
    }
    
    void init(Properties prop) throws Exception  {
        File indexDir = new File(prop.getProperty("index"));        
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));        
        numDocs = reader.numDocs();        
        K = Integer.parseInt(prop.getProperty("numclusters", "200"));        
        J = Integer.parseInt(prop.getProperty("eval.numclasses"));
        clusterClassMatrix = new int[K][J];
        
        initClusterIdMap();
        initStats();
    }
    
    void initStats() throws Exception {
        
        // Compute freq of each cluster id
        for (int i=0; i < numDocs; i++) {
            int k = clusterIdMap.get(i);
            int j = Integer.parseInt(reader.document(i).get(WMTIndexer.FIELD_DOMAIN_ID));
            clusterClassMatrix[k][j]++;
        }
        
        clusterCardinalities = new int[K];
        for (int k=0; k<K; k++) {
            for (int j=0; j<J; j++) {
                clusterCardinalities[k] += clusterClassMatrix[k][j];
            }
        }
        
        classCardinalities = new int[J];
        for (int j=0; j<J; j++) {
            for (int k=0; k<K; k++) {
                classCardinalities[j] += clusterClassMatrix[k][j];
            }
        }
    }
    
    float computePurity() throws Exception {
        int sum = 0;
        int maxCount;
        for (int k=0; k < K; k++) {
            maxCount = 0;
            for (int j = 0; j<J; j++) {
                if (clusterClassMatrix[k][j] > maxCount) {
                    maxCount = clusterClassMatrix[k][j];
                }
            }
            sum += maxCount;
        }
        
        return sum/(float)numDocs;
    }
    
    float computeNMI() throws IOException {
        float i_w_c = 0, h_w = 0, h_c = 0;
        float p_cluster_class;
        float log_component;
        
        for (int k=0; k < K; k++) {            
            for (int j=0; j < J; j++) {
                if (clusterCardinalities[k]*classCardinalities[j] == 0)
                    continue;
                p_cluster_class = clusterClassMatrix[k][j]/(float)numDocs;
                if(clusterClassMatrix[k][j] == 0)
                    continue;
                log_component = (float)(Math.log((numDocs*clusterClassMatrix[k][j])/(float)(clusterCardinalities[k]*classCardinalities[j])));               
                i_w_c += p_cluster_class * log_component;
            }
        }
        
        for (int k=0; k < K; k++) {            
            float p_w_k = clusterCardinalities[k]/(float)numDocs;
            h_w += p_w_k * Math.log(p_w_k);
        }
        h_w = -h_w;
        
        
        for (int j=0; j < J; j++) {            
            float p_c_j = classCardinalities[j]/(float)numDocs;
            h_c += p_c_j * Math.log(p_c_j);
        }
        h_c = -h_c;
        
        return 2*i_w_c/(h_w + h_c);
    }
    
    int nC2(int n) {
        return ((n-1)*n)>>1;
    }
    
    int countTP() {
        int sum = 0;
        for (int k=0; k < K; k++) {
            for (int j=0; j < J; j++) {
                sum += nC2(clusterClassMatrix[k][j]);
            }
        }
        return sum;
    }
    
    int countTPPlusFP() {        
        int sum = 0;
        for (int k=0; k < K; k++) {          
            sum += nC2(clusterCardinalities[k]);            
        }
        return sum;
    }
    
    int countFN() {
        int sum = 0;
        for (int j=0; j < J; j++) {
            for (int k=0; k < K - 1; k++) {
                 for (int k1= k+1 ; k1 < K; k1++) {
                    sum += clusterClassMatrix[k][j] * clusterClassMatrix[k1][j];
                 }
            }
        }
        return sum;
    }
    
    int countTN() {                
        return countTNPlusFN() - countFN();
    }
    
    int countTNPlusFN() {
       int sum = 0;
        for (int k=0; k < K-1; k++) {   
             for (int k1 = k + 1; k1 < K; k1++) {
                sum += clusterCardinalities[k] * clusterCardinalities[k1];  
             }
        }
        return sum;
    }
    
    float computeRandIndex() {
        float tp_fp = countTP() + countTPPlusFP();
        float tn_fn = countTN() + countTNPlusFN();
        return tp_fp / tn_fn;
    }
    
    void showClusterClassMatrix() {
        for (int k=0; k < K; k++) {
            for (int j=0; j < J; j++) {
                System.out.print(clusterClassMatrix[k][j] + " ");
            }
            System.out.println();
        }
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java ClusterEvaluator <prop-file>");
            args[0] = "init.properties";
        }
        
        try {        
            ClusterEvaluator ceval = new ClusterEvaluator(args[0]);
            ceval.showClusterClassMatrix();
            System.out.println("Purity: " + ceval.computePurity());
            System.out.println("NMI: " + ceval.computeNMI());            
            System.out.println("RI: " + ceval.computeRandIndex());            
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
    }
    
}
