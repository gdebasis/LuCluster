/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import static clusterer.TermStats.MAX_NUM_QRY_TERMS;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author Debasis
 */
public final class TermVector {
    List<TermStats> termStatsList;
    float norm;

    public TermVector() {
        termStatsList = new ArrayList<>();
        norm = 0;
    }
    
    public TermVector(List<TermStats> termStats) {
        this.termStatsList = termStats;
        norm = computeNorm();
    }
    
    void add(TermStats stats) {
        termStatsList.add(stats);
    }
    
    public static TermVector extractAllDocTerms(IndexReader reader, int docId, String contentFieldName, float lambda) throws Exception {
        return extractDocTerms(reader, docId, contentFieldName, 1, lambda);
    }
    
    public static TermVector extractDocTerms(IndexReader reader, int docId, String contentFieldName, float queryToDocRatio, float lambda) throws Exception {
        String termText;
        BytesRef term;
        Terms tfvector;
        TermsEnum termsEnum;
        int tf;
        
        tfvector = reader.getTermVector(docId, contentFieldName);
        if (tfvector == null || tfvector.size() == 0)            
            return null;
        // Construct the normalized tf vector
        termsEnum = tfvector.iterator(); // access the terms for this field
        
        List<TermStats> termStats = new ArrayList<>();
        
        int docLen = 0;
    	while ((term = termsEnum.next()) != null) { // explore the terms for this field            
            tf = (int)termsEnum.totalTermFreq();
            termText = term.utf8ToString();
            
            termStats.add(new TermStats(termText, tf, reader));            
            docLen += tf;
        }
        
        for (TermStats ts : termStats) {
            ts.computeWeight(docLen, lambda);
        }
        
        Collections.sort(termStats);
        int numTopTerms = (int)(queryToDocRatio*termStats.size());
        numTopTerms = Math.min(numTopTerms, MAX_NUM_QRY_TERMS);
        
        return new TermVector(termStats.subList(0, numTopTerms));
    }
    
    public float cosineSim(TermVector that) {
        float sim = 0;
        int i, j;
        int alen = this.termStatsList.size(), blen = that.termStatsList.size();
        
        for (i=0, j=0; i < alen && j < blen; ) {
            TermStats a = this.termStatsList.get(i);
            TermStats b = that.termStatsList.get(j);
            
            int cmp = a.term.compareTo(b.term);
            if (cmp == 0) {
                sim += a.wt * b.wt;
                i++;
                j++;
            }
            else if (cmp < 0) {
                i++;
            }
            else {
                j++;
            }
        }
        
        return sim/(this.norm * that.norm);
    }
    
    float computeNorm() {
        float normval = 0;
        for (TermStats ts : termStatsList) {
            normval += ts.wt*ts.wt;
        }
        return (float)Math.sqrt(normval);
    }
    
    // Call this function for constructing the centroid vector during K-means
    static public TermVector add(TermVector avec, TermVector bvec) {
        TermVector sum = new TermVector();
        int i, j;
        int alen = sum.termStatsList.size(), blen = bvec.termStatsList.size();

        for (i=0, j=0; i < alen && j < blen; ) {
            TermStats a = avec.termStatsList.get(i);
            TermStats b = bvec.termStatsList.get(j);
            
            int cmp = a.term.compareTo(b.term);
            if (cmp == 0) {
                sum.add(new TermStats(a.term, a.wt+b.wt));
                i++;
                j++;
            }
            else if (cmp < 0) {
                sum.add(new TermStats(a.term, a.wt));
                i++;
            }
            else {
                sum.add(new TermStats(b.term, b.wt));
                j++;
            }
        }
        
        return sum;
    }
}
