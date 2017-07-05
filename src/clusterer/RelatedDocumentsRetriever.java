/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;
import static indexer.WMTIndexer.FIELD_ANALYZED_CONTENT;
import static indexer.WMTIndexer.FIELD_CLUSTER_ID;
import static indexer.WMTIndexer.FIELD_DOMAIN_ID;
import static indexer.WMTIndexer.FIELD_URL;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author Debasis
 */
public class RelatedDocumentsRetriever {
    IndexReader reader;
    int docId;
    int clusterId;
    Document queryDoc;
    String contentFieldName;
    String idFieldName;
    TopDocs relatedDocs;
    HashMap<Integer, ScoreDoc> docScoreMap;
    List<Integer> nonretrievedDocIds;  // doc-ids that were not retrieved but assigned to this cluster
    
    Properties prop;

    float qSelLambda;
    static final float queryToDocRatio = 0.2f;
        
    public RelatedDocumentsRetriever(IndexReader reader, int docId, Properties prop, int clusterId) throws IOException {
        this.reader = reader;
        this.docId = docId;
        this.prop = prop;
        this.clusterId = clusterId;
        this.contentFieldName = prop.getProperty("content.field_name", WMTIndexer.FIELD_ANALYZED_CONTENT);
        this.idFieldName = prop.getProperty("id.field_name", WMTIndexer.FIELD_URL);
        qSelLambda = Float.parseFloat(prop.getProperty("lm.termsel.lambda", "0.6f"));
        this.queryDoc = reader.document(docId);
        nonretrievedDocIds = new ArrayList<>();
    }

    TopDocs normalize(TopDocs topDocs) {
        if (topDocs.totalHits == 0)
            return topDocs;
        
        ScoreDoc[] sortedSD = normalize(topDocs.scoreDocs);
        return new TopDocs(topDocs.totalHits, sortedSD, sortedSD[0].score);
    }
    
    ScoreDoc[] normalize(ScoreDoc[] sd) {
        ScoreDoc[] normalizedScoreDocs = new ScoreDoc[sd.length];
        for (int i=0; i < sd.length; i++) {
            normalizedScoreDocs[i] = new ScoreDoc(sd[i].doc, sd[i].score);
        }
        
        float sumScore = 0;
        
        for (int i=0; i < sd.length; i++) {
            if (Float.isNaN(sd[i].score))
                continue;
            sumScore += sd[i].score;
        }

        for (int i=0; i < sd.length; i++) {
            if (Float.isNaN(sd[i].score)) {
                normalizedScoreDocs[i].score = 0;
            }
            else {    
                normalizedScoreDocs[i].score = sd[i].score/sumScore;
            }
        }
        return normalizedScoreDocs;
    }
        
    void addDocId(int docId) {
        nonretrievedDocIds.add(docId);
    }
    
    TopDocs getRelatedDocs(int numWanted) throws Exception {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new LMJelinekMercerSimilarity(0.4f));
        
        BooleanQuery queryDocument = new BooleanQuery();
        TermVector repTerms = TermVector.extractDocTerms(reader, docId, contentFieldName, queryToDocRatio, qSelLambda);
        if (repTerms == null)
            return null;
        for (TermStats ts : repTerms.termStatsList) {
            queryDocument.add(new TermQuery(new Term(contentFieldName, ts.term)), BooleanClause.Occur.SHOULD);
        }
        
        relatedDocs = searcher.search(queryDocument, numWanted);
        relatedDocs = normalize(relatedDocs);
        
        docScoreMap = new HashMap<>();
        for (ScoreDoc sd : relatedDocs.scoreDocs) {
            docScoreMap.put(sd.doc, sd);
        }
        
        //System.out.println("#related docs = " + docScoreMap.size());
        return relatedDocs;
    }
    
    int getUnrelatedDocument(HashMap<Integer, Byte> centroidDocIds) {
        int numDocs = reader.numDocs();
        int start = (int)(Math.random()*numDocs), i;
        int end = numDocs;

        for (i=start; i < end; i++) {
            if (docScoreMap!=null && !docScoreMap.containsKey(i) && !centroidDocIds.containsKey(i))
                break;
            else if (!centroidDocIds.containsKey(i))
                break;
                
            if (i==end-1) {
                end = start;
                i = 0;
            }
        }
        
        // if nothing found, return a random one... else this document
        return end==start? start : i;
    } 

    Document constructDoc(int docId, int clusterId) throws Exception {
        
        Document indexedDoc = reader.document(docId);
        String id = indexedDoc.get(idFieldName);
        String domainName = indexedDoc.get(FIELD_DOMAIN_ID);
        String content = indexedDoc.get(FIELD_ANALYZED_CONTENT);
        
        Document doc = new Document();
        doc.add(new Field(idFieldName, id, Field.Store.YES, Field.Index.NOT_ANALYZED));
        
        if (domainName != null)
            doc.add(new Field(FIELD_DOMAIN_ID, domainName, Field.Store.YES, Field.Index.NOT_ANALYZED));
        
        doc.add(new Field(FIELD_CLUSTER_ID, String.valueOf(clusterId), Field.Store.YES, Field.Index.NOT_ANALYZED));

        // For the 1st pass, use a standard analyzer to write out
        // the words (also store the term vector)
        doc.add(new Field(contentFieldName, content,
                Field.Store.NO, Field.Index.ANALYZED, Field.TermVector.YES));
        
        return doc;
    }
    
    int getNumberOfUniqueTerms(int docId) throws Exception {
        Terms tfvector;
        TermsEnum termsEnum;
        BytesRef term;
        
        tfvector = reader.getTermVector(docId, contentFieldName);
        if (tfvector == null || tfvector.size() == 0)
            return 0;
        
        // Construct the normalized tf vector
        termsEnum = tfvector.iterator(); // access the terms for this field
        int numTerms = 0;
    	while ((term = termsEnum.next()) != null) { // explore the terms for this field
            numTerms++;
        }
        return numTerms;
    }
    
    int recomputeCentroidDoc() throws Exception {
        int numNonRetrDocs = nonretrievedDocIds.size();
        int numRelatedDocs = relatedDocs==null? 0 : relatedDocs.scoreDocs.length;
        int[] docIds = new int[numRelatedDocs + numNonRetrDocs];
        int i;
        for (i=0; i < numRelatedDocs; i++) {
            docIds[i] = relatedDocs.scoreDocs[i].doc;
        }
        for (i=0; i < numNonRetrDocs; i++) {
            docIds[numRelatedDocs + i] = nonretrievedDocIds.get(i);
        }
        
        int mostCentralDocId = 0;
        int maxNumUniqueTermsSeen = getNumberOfUniqueTerms(docIds[0]);
        
        for (i=1; i < docIds.length; i++) {
            int numUniqueTerms = getNumberOfUniqueTerms(docIds[i]);
            if (numUniqueTerms > maxNumUniqueTermsSeen) {
                maxNumUniqueTermsSeen = numUniqueTerms;
                mostCentralDocId = i;
            }
        }
        
        return mostCentralDocId;
    }
}
