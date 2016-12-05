/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package indexer;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;

/**
 *
 * @author Debasis
 */

class Paragraph {
    String id;
    String content;

    public Paragraph(String id, List<String> tokens) {
        this.id = id;
        StringBuffer buff = new StringBuffer();
        
        for (String token : tokens) {
            buff.append(token).append(" ");
        }
        content = buff.toString();
    }
}

public class SlindingWindowParagraphBuilder {
    
    int paraWindowSize;
    Analyzer analyzer;

    public SlindingWindowParagraphBuilder(int paraWindowSize, Analyzer analyzer) {
        this.paraWindowSize = paraWindowSize;
        this.analyzer = analyzer;                
    }
        
    // the window size in number of words
    List<Paragraph> constructParagraphs(int docId, String content) throws Exception {
        List<Paragraph> parList = new ArrayList<>();
        
        List<String> tokens = new ArrayList<>();
        TokenStream stream = analyzer.tokenStream("dummy", new StringReader(content));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
        stream.reset();

        int count = 0;
        int id = 0;
        while (stream.incrementToken()) {
            String term = termAtt.toString();
            tokens.add(term);
            count++;
            if (count == paraWindowSize) {
                // create a paragraph
                Paragraph p = new Paragraph(docId + "_" + String.valueOf(id++), tokens);
                tokens.clear();
                count = 0;
                parList.add(p);
            }
        }
        if (count > 0) {
            Paragraph p = new Paragraph(docId + "_" + String.valueOf(id++), tokens);            
            parList.add(p);
        }

        stream.end();
        stream.close();
        
        return parList;
    }
    
}
