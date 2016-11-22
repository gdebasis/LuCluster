/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author Debasis
 */
public class WMTIndexer {
    Properties prop;
    File indexDir;    
    Analyzer analyzer;
    IndexWriter writer;
    List<String> stopwords;

    static final public String FIELD_URL = "url";  // ref domain id
    static final public String FIELD_DOMAIN_ID = "domain_id";  // ref domain id
    static final public String FIELD_ANALYZED_CONTENT = "words";  // Standard analyzer w/o stopwords.
    static final public String FIELD_CLUSTER_ID = "cluster_id"; // to be used during processing

    public WMTIndexer(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));                
        analyzer = constructAnalyzer(prop.getProperty("stopfile"));            
        String indexPath = prop.getProperty("index");        
        indexDir = new File(indexPath);        
    }
    
    static public List<String> buildStopwordList(String stopwordFileName) {
        List<String> stopwords = new ArrayList<>();
        String line;

        try (FileReader fr = new FileReader(stopwordFileName);
            BufferedReader br = new BufferedReader(fr)) {
            while ( (line = br.readLine()) != null ) {
                stopwords.add(line.trim());
            }
            br.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return stopwords;
    }

    static public Analyzer constructAnalyzer(String stopwordFileName) {
        Analyzer eanalyzer = new WebDocAnalyzer(
                StopFilter.makeStopSet(buildStopwordList(stopwordFileName))); // default analyzer
        return eanalyzer;        
    }

    void indexAll() throws Exception {
        if (writer == null) {
            System.err.println("Skipping indexing... Index already exists at " + indexDir.getName() + "!!");
            return;
        }
        
        File topDir = new File(prop.getProperty("coll"));
        indexDirectory(topDir);
    }

    private void indexDirectory(File dir) throws Exception {
        File[] files = dir.listFiles();  
        Arrays.sort(files);
        
        for (int i=0; i < files.length; i++) {
            File f = files[i];
            indexFile(i, f, dir);
        }
    }
    
    void processAll() throws Exception {
        System.out.println("Indexing WMT collection...");
        
        IndexWriterConfig iwcfg = new IndexWriterConfig(analyzer);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        writer = new IndexWriter(FSDirectory.open(indexDir.toPath()), iwcfg);
        
        indexAll();
        
        writer.close();
    }

    void indexFile(int domainId, File file, File dir) throws Exception {
        
        System.out.println("Indexing file: " + file.getName());
        
        InputStream fileStream = new FileInputStream(file);
        InputStream gzipStream = new GZIPInputStream(fileStream);
        Reader decoder = new InputStreamReader(gzipStream, "UTF-8");
        BufferedReader br = new BufferedReader(decoder);
        String line;
        Document doc;
        
        while ((line = br.readLine()) != null) {
            String[] tokens = line.split("\\t");
            if (tokens.length < 6)
                continue;
            if (!tokens[0].equals("en"))
                continue; // index only English documents
                        
            String decodedContent = decodeBase64(tokens[5]);
            doc = constructDoc(tokens[3], String.valueOf(domainId), decodedContent, 0);
            writer.addDocument(doc);
        }
        
        
        br.close();
        gzipStream.close();
        fileStream.close();
    }

    String decodeBase64(String content) {
        byte[] decoded = Base64.getDecoder().decode(content);    
        String decodedStr = new String(decoded);
        decodedStr = decodedStr.replaceAll("'", " ");
        return decodedStr;
    }
    
    static public Document constructDoc(String id, String domainName, String content, int clusterId) throws Exception {
        Document doc = new Document();
        doc.add(new Field(FIELD_URL, id, Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(FIELD_DOMAIN_ID, domainName, Field.Store.YES, Field.Index.NOT_ANALYZED));
        
        // For the 1st pass, use a standard analyzer to write out
        // the words (also store the term vector)
        doc.add(new Field(FIELD_ANALYZED_CONTENT, content,
                Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
        
        return doc;
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java WMTIndexer <prop-file>");
            args[0] = "init.properties";
        }

        try {
            WMTIndexer indexer = new WMTIndexer(args[0]);
            indexer.processAll();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }    
}
