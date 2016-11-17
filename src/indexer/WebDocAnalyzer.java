/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import java.io.IOException;
import java.io.Reader;
import java.util.Properties;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.UAX29URLEmailTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.FilteringTokenFilter;
import org.apache.lucene.util.Version;

/**
 *
 * @author Debasis
 */
public class WebDocAnalyzer extends Analyzer {

    CharArraySet stopSet;
    
    public WebDocAnalyzer(CharArraySet stopSet) {
        this.stopSet = stopSet;
    }
    
    @Override
    protected TokenStreamComponents createComponents(String string) {
        final Tokenizer tokenizer = new UAX29URLEmailTokenizer();
        
        TokenStream tokenStream = new StandardFilter(tokenizer);
        tokenStream = new LowerCaseFilter(tokenStream);
        tokenStream = new StopFilter(tokenStream, stopSet);
        tokenStream = new URLFilter(tokenStream); // remove URLs
        tokenStream = new ValidWordFilter(tokenStream); // remove words with digits
        tokenStream = new PorterStemFilter(tokenStream);
        
        return new Analyzer.TokenStreamComponents(tokenizer, tokenStream);
    }
}

// Removes tokens with any digit
class ValidWordFilter extends FilteringTokenFilter {

    CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);

    public ValidWordFilter(TokenStream in) {
        super(in);
    }
    
    @Override
    protected boolean accept() throws IOException {
        String token = termAttr.toString();
        int len = token.length();
        for (int i=0; i < len; i++) {
            char ch = token.charAt(i);
            if (Character.isDigit(ch))
                return false;
            if (ch == '.')
                return false;
        }
        return true;
    }    
}

class URLFilter extends FilteringTokenFilter {

    TypeAttribute typeAttr = addAttribute(TypeAttribute.class);

    public URLFilter(TokenStream in) {
        super(in);
    }
    
    @Override
    protected boolean accept() throws IOException {
        boolean isURL = typeAttr.type().equals(UAX29URLEmailTokenizer.TOKEN_TYPES[UAX29URLEmailTokenizer.URL]);
        return !isURL;
    }    
}
