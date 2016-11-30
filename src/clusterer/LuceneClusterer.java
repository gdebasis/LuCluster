/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author Debasis
 */
public abstract class LuceneClusterer {
    Properties prop;
    IndexReader reader;  // the combined index to search
    int numDocs;
    int K;
    String contentFieldName;
    HashMap<Integer, Integer> clusterIdMap;

    public LuceneClusterer(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));                
        
        File indexDir = new File(prop.getProperty("index"));
        
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        numDocs = reader.numDocs();
        K = Integer.parseInt(prop.getProperty("numclusters", "200"));
        contentFieldName = prop.getProperty("content.field_name", WMTIndexer.FIELD_ANALYZED_CONTENT);        
        clusterIdMap = new HashMap<>();
    }
    
    abstract void initCentroids() throws Exception;
    abstract void recomputeCentroids() throws Exception;
    abstract boolean isCentroid(int docId);
    abstract int getClosestCluster(int docId) throws Exception;    
    abstract void showCentroids() throws Exception;
    
    public void cluster() throws Exception {
        
        resetAllClusterIds();
        initCentroids();

       	int i;
		long currentTime;
        int maxIters = Integer.parseInt(prop.getProperty("maxiters", "100"));
        int stepIters = Integer.parseInt(prop.getProperty("stepiters", "25"));
		boolean stopOnThreshold = Boolean.parseBoolean(prop.getProperty("threshold.stop", "false"));
        float stopThreshold = Float.parseFloat(prop.getProperty("stopthreshold", "0.1"));
        float changeRatio;
      
		long startTime =  System.currentTimeMillis();
        for (i=1; i <= maxIters; i++) {
            System.out.println("Iteration : " + i);
            showCentroids();
            
            System.out.println("Reassigning cluster ids to non-centroid docs...");
            changeRatio = assignClusterIds();
            
            System.out.println(changeRatio + " fraction of the documents reassigned different clusters...");
            if (stopOnThreshold && (changeRatio < stopThreshold)) {
                System.out.println("Stopping after " + i + " iterations...");
                break;
            }
	    	saveClusterIds();    

            if (i % stepIters == 0) {
				currentTime = System.currentTimeMillis();

				System.out.println("Time to finish " + i + " iterations (s): " + (currentTime - startTime)/1000);
	      		ClusterEvaluator ceval = new ClusterEvaluator(prop);
	      		System.out.println("Clustering Evaluation after " + stepIters + " iterations...");
				System.out.println("Purity: " + ceval.computePurity());
				System.out.println("NMI: " + ceval.computeNMI());
				System.out.println("RI: " + ceval.computeRandIndex());
	      }
		
			recomputeCentroids();
        }

		currentTime = System.currentTimeMillis();
		System.out.println("Time to finish " + i + " iterations (s): " + (currentTime - startTime)/1000);

		reader.close();
    }
    
    void saveClusterIds() throws Exception {
        FileWriter fw = new FileWriter(prop.getProperty("cluster.idfile"));
        BufferedWriter bw = new BufferedWriter(fw);
        
        for (Map.Entry<Integer, Integer> e : clusterIdMap.entrySet()) {
            bw.write(e.getKey() + "\t" + e.getValue() + "\n");
        }
        
        bw.close();
        fw.close();
    }
    
    public Properties getProperties() { return prop; }
    
    int getClusterId(int docId) throws Exception {
        return clusterIdMap.get(docId);
    }
    
    boolean assignClusterId(int docId, int clusterId) throws Exception {

        int oldClusterId = clusterIdMap.get(docId);
        clusterIdMap.put(docId, clusterId);        
        return clusterId != oldClusterId;
    }
    
    // Call this before initializing the algorithm
    public void resetAllClusterIds() throws Exception {        
        for (int i=0; i < numDocs; i++) {
            clusterIdMap.put(i, 0);
        }        
    }
    
    // Assign cluster ids for non-centroid docs...
    // Normalize the ranked lists (in order to be able to compare against them)
    // For a document d that is not a centroid, let k = argmax_j sim(d, C_j) (computed after normalization)
    // Assign d to C_k.
    // Returns the ratio of #docs with changes in cluster id to the toal #docs
    float assignClusterIds() throws Exception {
        int numChanged = 0;
        
        for (int i=0; i < numDocs; i++) { // O(N.K)
            if (isCentroid(i))
                continue;
            
            Document d = reader.document(i);            
            String thisUrl = d.get(WMTIndexer.FIELD_URL);
            int clusterId = getClosestCluster(i);
            if (assignClusterId(i, clusterId))
                numChanged++;  // cluster id got changed
            
        }
        return numChanged/(float)numDocs;
    }
}
