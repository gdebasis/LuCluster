/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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

class ClusterStat implements Comparable<ClusterStat> {
    int refId;
    int count;

    public ClusterStat(int refId) {
        this.refId = refId;
    }

    @Override
    public int compareTo(ClusterStat that) {
        return -1 * Integer.compare(count, that.count); // descending
    }
}

class ClusterStats {
    ClusterStat[] freq;
    int numClusters;

    public ClusterStats(int numClusters) {
        this.numClusters = numClusters;
        freq = new ClusterStat[numClusters];
    }
    
    void addObs(int refClusterId) {
        if (freq[refClusterId] == null) {
            freq[refClusterId] = new ClusterStat(refClusterId);
        }
        freq[refClusterId].count++;
    }
    
    int getMaxFreq() {
        Arrays.sort(freq);
        return freq[0].count;
    }
}

public class ClusterEvaluator {
    IndexReader reader;  // the combined index to search
    Properties prop;
    int numDocs;
    int K;
    
    public ClusterEvaluator(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));        
        init(prop);
    }
    
    public ClusterEvaluator(Properties prop) throws Exception  {
        this.prop = prop;
        init(prop);
    }
    
    void init(Properties prop) throws Exception  {
        File indexDir = new File(prop.getProperty("index"));        
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));        
        numDocs = reader.numDocs();        
        K = Integer.parseInt(prop.getProperty("numclusters", "200"));        
    }
    
    float computePurity() throws Exception {
        HashMap<Integer, ClusterStats> clusterIdCount = new HashMap<>();
        
        // Compute freq of each cluster id
        for (int i=0; i < numDocs; i++) {
            int k = Integer.parseInt(reader.document(i).get(WMTIndexer.FIELD_CLUSTER_ID));
            int domainId = Integer.parseInt(reader.document(i).get(WMTIndexer.FIELD_DOMAIN_ID));
            
            ClusterStats clusterStats = clusterIdCount.get(k);
            if (clusterStats == null) {
                clusterStats = new ClusterStats(K);
            }
            
            clusterStats.addObs(domainId);            
            clusterIdCount.put(k, clusterStats);
        }
        
        int sum = 0;
        for (Map.Entry<Integer, ClusterStats> clusterIds : clusterIdCount.entrySet()) {
            ClusterStats stats = clusterIds.getValue();
            sum += stats.getMaxFreq();
        }
        
        return sum/(float)numDocs;
    }
    
    float computeNMI() {
        return 0;
    }
    
}
