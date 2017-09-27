/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import java.util.*;
import java.io.*;
import java.util.Properties;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

/**
 * Evaluates a clustering based on qrels files. Assumes that two documents
 * that are relevant to the same query should belong to the same cluster.
 * The evaluation metric considers pairs of documents from the clustering
 * output and calculates accuracy, precision, recall and F-score.
 * 
 * @author Debasis
 */

class ClusterGroup {
    List<Set<String>> equivLists;  // A list of equivalent docs set 

    public ClusterGroup(String qrelsFile) throws Exception {
        equivLists = new ArrayList<>();
        
        FileReader fr = new FileReader(qrelsFile);
        BufferedReader br = new BufferedReader(fr);
        
        String line;
        String prevQid = null, thisQid;
        Set<String> equivDocs = new HashSet<>();
        
        while ((line = br.readLine()) != null) {
            String[] tokens = line.split("\\s+");
            thisQid = tokens[0];

            if (prevQid != null && !prevQid.equals(thisQid)) {
                // change in qid
                equivLists.add(equivDocs);
                equivDocs = new HashSet<>();
            }
            if (Integer.parseInt(tokens[3]) > 0)
                equivDocs.add(tokens[2]); // add all rel docs for this query into one equiv class
            
            prevQid = thisQid;
        }
        
        // Enter the last batch
        equivLists.add(equivDocs);        
    }
    
    void printEquivClasses() {
        int i=1;
        for (Set<String> equivClass : equivLists) {
            System.out.println("Query: " + i);
            for (String s : equivClass)
                System.out.println(s);
            i++;
        }
    }

    boolean sameCluster(int qid, String i, String j) {
        // iterate through every query and check if two given docids
        // belong to the same hashset. if found return true
        Set<String> equivSet = equivLists.get(qid);
        if (equivSet.contains(i) && equivSet.contains(j)) 
            return true;
        else
            return false;
    }
    
    boolean sameCluster(String i, String j) {
        // iterate through every query and check if two given docids
        // belong to the same hashset. if found return true
        for (Set<String> equivSet : equivLists) {
            if (equivSet.contains(i) && equivSet.contains(j)) 
                return true;
        }
        return false;
    }

    // used to load a selected number of docids
    boolean isRelDoc(String docId) {
        for (Set<String> equivSet : equivLists) {
            if (equivSet.contains(docId))
                return true; 
        }
        return false;	
    } 
}

public class QrelClusterEvaluator {
    Properties prop;
    ClusterGroup refClusters;
    HashMap<String, Integer> clusterIdMap;  // key:<docid> value:<cluster-id>
    
    public QrelClusterEvaluator(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));
        
        String qrelsFile = prop.getProperty("qrels");
        System.out.println("Loading qrels file...");
        refClusters = new ClusterGroup(qrelsFile);
        
        //refClusters.printEquivClasses();

        System.out.println("Loading clustering results...");        
        initClusterIdMap();
    }
    
    // O/p clusters
    void initClusterIdMap() throws Exception {
        FileReader fr = new FileReader(prop.getProperty("cluster.idfile"));
        BufferedReader br = new BufferedReader(fr);
        String line;

        clusterIdMap = new HashMap<>();

        while ((line = br.readLine()) != null) {
            String[] tokens = line.split("\t");
            if (refClusters.isRelDoc(tokens[2])) {
                clusterIdMap.put(tokens[2], Integer.parseInt(tokens[1]));
            }
        }

        br.close();
        fr.close();
    }
    
    boolean predictedToSameCluster(String docA, String docB) {
        if (!(clusterIdMap.containsKey(docA) && clusterIdMap.containsKey(docB)))
            return false;
        return clusterIdMap.get(docA).intValue() == clusterIdMap.get(docB).intValue();
    }
    
    void evaluateAll() throws Exception {
        int numQ = refClusters.equivLists.size();
        float avg = 0;
        for (int qid=0; qid < numQ; qid++) {
            float ap = evaluate(qid);
            System.out.println("ap(" + qid + ") = " + ap);
            avg += ap;
        }
        System.out.println("Avg. " + avg/(float)numQ);
    }
    
    float evaluate(int qid) throws Exception {
        Set<String> refDocIdsSet = clusterIdMap.keySet();
        int numRefDocs = refDocIdsSet.size();
        String[] refDocIdsForEval = new String[numRefDocs];
        
        System.err.println("#docs to check: " + numRefDocs);
        int i = 0, j = 0;
        for (String docid : refDocIdsSet) {
            refDocIdsForEval[i++] = docid;
        }

        Arrays.sort(refDocIdsForEval);
        
        boolean knownSameCluster;
        boolean predictedToSameCluster;
        
        int tp = 0;
        
        for (i=0; i < numRefDocs; i++) {
            for (j=i+1; j < numRefDocs; j++) {
                
                knownSameCluster = refClusters.sameCluster(qid, refDocIdsForEval[i], refDocIdsForEval[j]);
                predictedToSameCluster = predictedToSameCluster(refDocIdsForEval[i], refDocIdsForEval[j]);
                    
                if (knownSameCluster && predictedToSameCluster)
                    tp++;
            }
        }
        return tp/(float)refClusters.equivLists.get(qid).size();
    }
    
    void evaluate() throws Exception {
        Set<String> refDocIdsSet = clusterIdMap.keySet();
        int numRefDocs = refDocIdsSet.size();
        String[] refDocIdsForEval = new String[numRefDocs];
        
        System.err.println("#docs to check: " + numRefDocs);
        int i = 0, j = 0;
        for (String docid : refDocIdsSet) {
            refDocIdsForEval[i++] = docid;
        }

        Arrays.sort(refDocIdsForEval);
        
        boolean knownSameCluster;
        boolean predictedToSameCluster;
        
        int tp = 0, fp = 0, fn = 0, tn = 0;
        
        for (i=0; i < numRefDocs; i++) {
            for (j=i+1; j < numRefDocs; j++) {
                
                knownSameCluster = refClusters.sameCluster(refDocIdsForEval[i], refDocIdsForEval[j]);
                predictedToSameCluster = predictedToSameCluster(refDocIdsForEval[i], refDocIdsForEval[j]);
                    
                //System.out.println(refDocIdsForEval[i] + ", " + refDocIdsForEval[j] + ": " + knownSameCluster + ", " + predictedToSameCluster);
                
                if (knownSameCluster && predictedToSameCluster)
                    tp++;
                else if (!knownSameCluster && predictedToSameCluster)
                    fp++;
                else if (knownSameCluster && !predictedToSameCluster)
                    fn++;
                else
                    tn++;
            }
        }
        System.out.println("Confusion matrix: ");        
        System.out.println(tp + "\t" + fp);
        System.out.println(fn + "\t" + tn);
        
        float accuracy = (tp + tn)/(float)(tp + fp + fn + tn);
        float prec = (tp)/(float)(tp + fp);
        float recall = (tp)/(float)(tp + fn);
        float fscore = 2*prec*recall/(prec+recall);
        
        System.out.println(accuracy + " " + prec + " " + recall + " " + fscore);
    }
    
    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                args = new String[1];
                System.out.println("Usage: java TrecMblogIndexer <prop-file>");
                args[0] = "tweets.properties";
            }
            
            QrelClusterEvaluator qrelclusterEval = new QrelClusterEvaluator(args[0]);
            qrelclusterEval.evaluate();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
