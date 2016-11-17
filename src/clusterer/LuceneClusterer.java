/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;
import java.io.File;
import java.io.FileReader;
import java.util.Properties;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
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

    public LuceneClusterer(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));                
        
        File indexDir = new File(prop.getProperty("index"));
        
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        numDocs = reader.numDocs();
        K = Integer.parseInt(prop.getProperty("numclusters", "200"));
        contentFieldName = prop.getProperty("content.field_name", WMTIndexer.FIELD_ANALYZED_CONTENT);        
    }
    
    abstract void initCentroids() throws Exception;
    abstract void recomputeCentroids() throws Exception;
    abstract boolean isCentroid(int docId);
    abstract int getClosestCluster(int docId) throws Exception;    
    abstract void showCentroids() throws Exception;
    
    public void cluster() throws Exception {
        // The writer object is required to write into the cluster-id field.
        File indexDir = new File(prop.getProperty("index"));        
        IndexWriterConfig iwcfg = new IndexWriterConfig(new KeywordAnalyzer()); // no need for analyzer here...
        IndexWriter writer = new IndexWriter(FSDirectory.open(indexDir.toPath()), iwcfg);
        
        resetAllClusterIds(writer);
        initCentroids();
        
        int maxIters = Integer.parseInt(prop.getProperty("kmedoids.maxiters", "20"));
        float stopThreshold = Float.parseFloat(prop.getProperty("kmedoids.stopthreshold", "0.1"));
        float changeRatio;
        
        for (int i=1; i <= maxIters; i++) {
            System.out.println("Iteration : " + i);
            showCentroids();
            
            System.out.println("Reassigning cluster ids to non-centroid docs...");
            changeRatio = assignClusterIds(writer);
            
            System.out.println(changeRatio + " fraction of the documents reassigned different cllusters...");
            if (changeRatio < stopThreshold) {
                System.out.println("Stopping after " + i + " iterations...");
                break;
            }
            recomputeCentroids();
        }
        
        writer.close();        
        reader.close();
    }
    
    int getClusterId(int docId) throws Exception {
        return Integer.parseInt(reader.document(docId).get(WMTIndexer.FIELD_CLUSTER_ID));
    }
    
    boolean assignClusterId(IndexWriter writer, int docId, String url, int clusterId) throws Exception {

        int oldClusterId = Integer.parseInt(reader.document(docId).get(WMTIndexer.FIELD_CLUSTER_ID));
        Term docIdTerm = new Term(WMTIndexer.FIELD_URL, url);
        
        writer.updateNumericDocValue(docIdTerm, WMTIndexer.FIELD_CLUSTER_ID, clusterId);
        return clusterId != oldClusterId;
    }
    
    // Call this before initializing the algorithm
    public void resetAllClusterIds(IndexWriter writer) throws Exception {
        
        for (int i=0; i < numDocs; i++) {
            Document d = reader.document(i);
            Term docIdTerm = new Term(WMTIndexer.FIELD_URL, d.get(WMTIndexer.FIELD_URL));

            writer.updateNumericDocValue(docIdTerm, WMTIndexer.FIELD_CLUSTER_ID, 0);
        }        
    }
    
    // Assign cluster ids for non-centroid docs...
    // Normalize the ranked lists (in order to be able to compare against them)
    // For a document d that is not a centroid, let k = argmax_j sim(d, C_j) (computed after normalization)
    // Assign d to C_k.
    // Returns the ratio of #docs with changes in cluster id to the toal #docs
    float assignClusterIds(IndexWriter writer) throws Exception {
        int numChanged = 0;
        
        for (int i=0; i < numDocs; i++) { // O(N.K)
            if (isCentroid(i))
                continue;
            
            Document d = reader.document(i);            
            String thisUrl = d.get(WMTIndexer.FIELD_URL);
            int clusterId = getClosestCluster(i);
            if (assignClusterId(writer, i, thisUrl, clusterId))
                numChanged++;  // cluster id got changed
            
        }
        return numChanged/(float)numDocs;
    }
}
