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
public class ScalableKMeans extends FastKMedoidsClusterer {
    
    public ScalableKMeans(String propFile) throws Exception {
        super(propFile);
    }

 	// compute true centroid instead of the heuristics 
    TermVector computeCentroid(int centroidId) throws Exception {
        TermVector centroidVec = TermVector.extractAllDocTerms(reader, centroidId, contentFieldName, lambda);
		TermVector newCentroidVec = null;

		if (centroidVec==null || centroidVec.termStatsList == null)
			newCentroidVec = new TermVector();
		else
        	newCentroidVec = new TermVector(centroidVec.termStatsList);

        for (int i=0; i < numDocs; i++) {
            if (i == centroidId)
                continue;

            int clusterId = getClusterId(i);
            if (clusterId != centroidId)
                continue;
            
            TermVector docVec = TermVector.extractAllDocTerms(reader, i, contentFieldName, lambda);
			if (docVec!=null)
            	newCentroidVec = TermVector.add(newCentroidVec, docVec);
        }
        return newCentroidVec;
    }
        
    @Override
    void recomputeCentroids() throws Exception {        
        int k = 0;
		System.out.println("#keys = " + centroidDocIds.size());
		
        for (int centroidId : centroidDocIds.keySet()) {
            TermVector newCentroidVec = computeCentroid(centroidId);            
            centroidVecs[k++] = newCentroidVec;            
        }
    }
 
  
	// Init centroids at random instead of the heuristics 
    @Override
    void initCentroids() throws Exception {
        int numClustersAssigned = 0;
        
        while (numClustersAssigned < K) {
            int selectedDoc = (int)(Math.random()*numDocs);
            if (centroidDocIds.containsKey(selectedDoc))
                continue;
           
			TermVector centroid = TermVector.extractAllDocTerms(reader, selectedDoc, contentFieldName, lambda);
			if (centroid != null) {
				System.out.println("Len of selected centroid " + numClustersAssigned + " = " + centroid.termStatsList.size());
            	centroidVecs[numClustersAssigned] = centroid;            
            	RelatedDocumentsRetriever rde = new RelatedDocumentsRetriever(reader, selectedDoc, prop, numClustersAssigned);
				rdes[numClustersAssigned] = rde;
				numClustersAssigned++;
            	centroidDocIds.put(selectedDoc, null);
			}
        }

		System.out.println("Size of initial cluster centres....");
		for (int i=0; i < numClustersAssigned; i++) {
			TermVector cv = centroidVecs[i];
			System.out.println("Len of center " + i + " = " + cv.termStatsList.size());
		}
    }
 
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java ScalableKMeans <prop-file>");
            args[0] = "tweets.properties";
        }
        
        try {
            LuceneClusterer fkmc = new ScalableKMeans(args[0]);
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
