/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package extractor;

import indexer.PaperIndexer;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

/**
 *
 * @author Debasis
 * 
 * Extract information from unstructured text indexed as fixed length word
 * window based paragrpahs.
 * Uses queries to retrieve these paragraphs and 'look for' specific answers.
 * 
 */

class InformationUnit implements Comparable<InformationUnit> {
    String desc;
    float weight;

    public InformationUnit(String desc) {
        this.desc = desc;
    }

    @Override
    public int compareTo(InformationUnit that) {
        return -1*Float.compare(weight, that.weight);
    }
}

public class InformationExtractor {
    IndexReader reader, paraReader;
    IndexSearcher searcher;
    Properties prop;
    Analyzer analyzer;
    String contentFieldName;
    
    public InformationExtractor(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));
        
        String indexPath = prop.getProperty("index");        
        File indexDir = new File(indexPath + "/docs/");
        reader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        indexDir = new File(indexPath + "/para/");
        paraReader = DirectoryReader.open(FSDirectory.open(indexDir.toPath()));
        
        searcher = new IndexSearcher(paraReader);
        searcher.setSimilarity(new LMJelinekMercerSimilarity(0.9f));
        analyzer = PaperIndexer.constructAnalyzer(prop.getProperty("stopfile"));
        contentFieldName = prop.getProperty("content.field_name");
    }

    boolean isNumber(String word) {
        int len = word.length();
        for (int i=0; i < len; i++) {
            if (!Character.isDigit(word.charAt(i)))
                return false;
        }
        return true;
    }
    
    void extractAge() throws Exception {
        int numDocs = reader.numDocs();
        
        for (int i = 0; i < numDocs; i++) {
            String docName = reader.document(i).get(PaperIndexer.FIELD_NAME);
            
            TermQuery docNameConstraintQuery = new TermQuery(new Term(PaperIndexer.FIELD_NAME, docName));
            
            QueryParser parser = new QueryParser(contentFieldName, analyzer);
            Query q = parser.parse("participant AND age");
            
            BooleanQuery bq = new BooleanQuery();
            bq.add(docNameConstraintQuery, BooleanClause.Occur.MUST);
            bq.add(q, BooleanClause.Occur.MUST);
            
            TopDocs topDocs = searcher.search(bq, 5);
            HashMap<String, InformationUnit> evidenceMap = new HashMap<>();
            
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document d = paraReader.document(sd.doc);
                String content = d.get(contentFieldName);
                //System.out.println(content);
                
                String[] tokens = content.split("\\s+");
                for (String token : tokens) {
                    if (isNumber(token)) {
                        InformationUnit iu = evidenceMap.get(token);
                        if (iu == null)
                            iu = new InformationUnit(token);
                        iu.weight++;
                        evidenceMap.put(token, iu);
                    }
                }
            }
            
            int numEvidences = evidenceMap.size();
            if (numEvidences == 0)
                continue;
            
            ArrayList<InformationUnit> iuList = new ArrayList<>(numEvidences);
            for (Map.Entry<String, InformationUnit> e : evidenceMap.entrySet()) {
                iuList.add(e.getValue());
            }
            Collections.sort(iuList);
            System.out.println("Age of population for Doc: " + docName + " is: " + iuList.get(0).desc);
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java InformationExtractor <prop-file>");
            args[0] = "init.properties";
        }

        try {
            InformationExtractor ie = new InformationExtractor(args[0]);
            ie.extractAge();
        }
        catch (Exception ex) { ex.printStackTrace(); }        
    }
}
