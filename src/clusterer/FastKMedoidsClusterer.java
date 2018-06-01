/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopDocsCollector;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.ScoreDoc;

/**
 *
 * @author dganguly
 */
public class FastKMedoidsClusterer extends LuceneClusterer {
    IndexSearcher searcher;
    RelatedDocumentsRetriever[] rdes;
    
    public FastKMedoidsClusterer(String propFile) throws Exception {
        super(propFile);
        
        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());        
        rdes = new RelatedDocumentsRetriever[K];
    }
    
    int selectDoc(HashSet<String> queryTerms) throws IOException {
        BooleanQuery.Builder b = new BooleanQuery.Builder();
        for (String qterm : queryTerms) {
            TermQuery tq = new TermQuery(new Term(contentFieldName, qterm));
            b.add(new BooleanClause(tq, BooleanClause.Occur.MUST_NOT));
        }
        
        TopDocsCollector collector = TopScoreDocCollector.create(1);
        searcher.search(b.build(), collector);
        TopDocs topDocs = collector.topDocs();
        return topDocs.scoreDocs == null || topDocs.scoreDocs.length == 0? -1 :
                topDocs.scoreDocs[0].doc;
    }
    
    // Initialize centroids
    // The idea is to select a random document. Grow a region around it and choose
    // as the next candidate centroid a document that does not belong to this region.
    @Override
    void initCentroids() throws Exception {
        int selectedDoc = (int)(Math.random()*numDocs);
        int numClusterCentresAssigned = 1;
        centroidDocIds = new HashMap<>();
        
        do {
            RelatedDocumentsRetriever rde = new RelatedDocumentsRetriever(reader, selectedDoc, prop, numClusterCentresAssigned);
            System.out.println("Chosen doc " + selectedDoc + " as centroid number " + numClusterCentresAssigned);
            TopDocs topDocs = rde.getRelatedDocs(numDocs/K);
            if (topDocs == null) {
                selectedDoc = rde.getUnrelatedDocument(centroidDocIds);
                continue;
            }
            centroidDocIds.put(selectedDoc, null);
            selectedDoc = rde.getUnrelatedDocument(centroidDocIds);
            rdes[numClusterCentresAssigned-1] = rde;
            numClusterCentresAssigned++;
        }
        while (numClusterCentresAssigned <= K);        
    }
    
    void showCentroids() throws Exception {
        int i = 0;
        for (RelatedDocumentsRetriever rde: rdes) {
            Document doc = rde.queryDoc;
            System.out.println("Centroid " + (i++) + ": " + doc.get(WMTIndexer.FIELD_DOMAIN_ID) + ", " + doc.get(idFieldName));
        }
    }
    
    @Override
    boolean isCentroid(int docId) {
        for (int i=0; i < K; i++) {
            if (rdes[i].docId == docId)
                return true;
        }
        return false;
    }
    
    @Override
    int getClosestCluster(int docId) throws Exception { // O(K) computation...
        float maxScore = 0;
        int clusterId = 0;
        for (int i=0; i < K; i++) {
            if (rdes[i].docScoreMap == null)
                continue;
            ScoreDoc sd = rdes[i].docScoreMap.get(docId);
            if (sd != null) {
                if (sd.score > maxScore) {
                    maxScore = sd.score;
                    clusterId = i;
                }
            }
        }
        if (maxScore == 0) {
            // Retrieved in none... Assign to a random cluster id
            clusterId = (int)(Math.random()*K);
        }
        return clusterId;
    }
    
    // Returns true if the cluster id is changed...
    @Override
    boolean assignClusterId(int docId, int clusterId) throws Exception {
        rdes[clusterId].addDocId(docId);        
        return super.assignClusterId(docId, clusterId);
    }
        
    @Override
    void recomputeCentroids() throws Exception {
        int newCentroidDocId;
        for (int i=0; i < K; i++) {
            newCentroidDocId = rdes[i].recomputeCentroidDoc();
            if (rdes[i].docId != newCentroidDocId) {
                String oldCentroidURL = rdes[i].queryDoc.get(idFieldName);
                rdes[i] = new RelatedDocumentsRetriever(reader, newCentroidDocId, prop, i);
                String newCentroidURL = rdes[i].queryDoc.get(idFieldName);
                System.out.println("Changed centroid document " + oldCentroidURL + " to " + newCentroidURL);
                
                rdes[i].getRelatedDocs(numDocs/K);
            }
        }
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java FastKMedoidsClusterer <prop-file>");
            args[0] = "tweets.properties";
        }
        
        try {
            LuceneClusterer fkmc = new FastKMedoidsClusterer(args[0]);
            fkmc.cluster();
            
            boolean eval = Boolean.parseBoolean(fkmc.getProperties().getProperty("eval", "false"));
            if (eval) {
                ClusterEvaluator ceval = new ClusterEvaluator(args[0]);
                System.out.println("Purity: " + ceval.computePurity());
                System.out.println("NMI: " + ceval.computeNMI());            
                System.out.println("RI: " + ceval.computeRandIndex());            
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
