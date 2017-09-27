/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import indexer.WMTIndexer;
import java.io.*;
import java.util.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.en.*;
import org.apache.lucene.analysis.core.*;

/**
 *
 * @author Debasis
 */
public class IndexDumper {
    Properties prop;
    IndexReader reader;  // the combined index to search
    int numDocs;
    String idFieldName;
    String contentFieldName;
    Analyzer analyzer;

    public IndexDumper(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));                
        
        File indexDir = new File(prop.getProperty("index"));
        
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        numDocs = reader.numDocs();
        contentFieldName = prop.getProperty("content.field_name", WMTIndexer.FIELD_ANALYZED_CONTENT);        
        idFieldName = prop.getProperty("id.field_name", WMTIndexer.FIELD_URL);        
        analyzer = constructAnalyzer();
    }

    protected List<String> buildStopwordList(String stopwordFileName) {
        List<String> stopwords = new ArrayList<>();
        String stopFile = prop.getProperty(stopwordFileName);        
        String line;

        try (FileReader fr = new FileReader(stopFile);
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

    Analyzer constructAnalyzer() {
        Analyzer eanalyzer = new EnglishAnalyzer(
            StopFilter.makeStopSet(buildStopwordList("stopfile"))); // default analyzer
        return eanalyzer;
    }
 
	String analyzeText(String txt, Analyzer analyzer) throws Exception {
		StringBuffer tokenizedContentBuff = new StringBuffer();
        TokenStream stream = analyzer.tokenStream(contentFieldName, new StringReader(txt));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
        stream.reset();
       	
		int numwords = 0; 
        while (stream.incrementToken()) {
            String term = termAtt.toString();
            tokenizedContentBuff.append(term).append(" ");
			numwords++;
        }

		stream.end();
		stream.close();

		return numwords==0? null : tokenizedContentBuff.toString();
	}
    
    public void dumpIndex() throws Exception {
		String ofileName = prop.getProperty("index.dump.file");
       	FileWriter fw = new FileWriter(ofileName); 
		BufferedWriter bw = new BufferedWriter(fw);
	
		for (int i=0; i < numDocs; i++) {
			Document doc = reader.document(i);
			String id = doc.get(idFieldName);
			String text = doc.get(contentFieldName);
			String analyzedText = analyzeText(text, analyzer);
			if (analyzedText != null) {
				bw.write(id);
				bw.write("\t");
				bw.write(analyzedText);
				bw.newLine();
			}
		}
	
		bw.close();
		fw.close();
    }

	public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java IndexDumper <prop-file>");
            args[0] = "tweets.properties";
        }


		try {
			IndexDumper dumper = new IndexDumper(args[0]);
			dumper.dumpIndex();
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
