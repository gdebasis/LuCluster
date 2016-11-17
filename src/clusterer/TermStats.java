/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author Debasis
 */
public class TermStats implements Comparable<TermStats> {
    String term;
    int tf;
    float ntf;
    float idf;
    float wt;
    
    static final int MAX_NUM_QRY_TERMS = 1024;
    
    TermStats(String term, float wt) {
        this.term = term;
        this.wt = wt;
    }
    
    TermStats(String term, int tf, IndexReader reader) throws Exception {
        this.term = term;
        this.tf = tf;
        idf = (float)(
                Math.log(reader.numDocs()/
                (float)(reader.docFreq(new Term(WMTIndexer.FIELD_ANALYZED_CONTENT, term)))));
    }
    
    void computeWeight(int docLen, float lambda) {
        ntf = tf/(float)docLen;
        wt = (float)Math.log(1+ lambda/(1-lambda)*ntf*idf);
    }

    @Override
    public int compareTo(TermStats that) {
        return -1*Float.compare(this.wt, that.wt); // descending
    }

}
