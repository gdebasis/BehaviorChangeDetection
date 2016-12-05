package indexer;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Debasis
 */

import java.io.*;
import java.util.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;

public final class PaperIndexer {
    Properties prop;
    IndexWriter writer, paraWriter;
    Analyzer analyzer;
    String indexDir;
    SlindingWindowParagraphBuilder builder;
    String fieldName;
        
    static int docId = 1;
    static final public String FIELD_ID = "id";
    static final public String FIELD_NAME = "name";
    
    public PaperIndexer(String propFile) throws Exception {
        prop = new Properties();
        prop.load(new FileReader(propFile));                
        indexDir = prop.getProperty("index");        
        analyzer = constructAnalyzer(prop.getProperty("stopfile"));        
        
        int windowSize = Integer.parseInt(prop.getProperty("window.size"));
        builder = new SlindingWindowParagraphBuilder(windowSize, analyzer);
        fieldName = prop.getProperty("content.field_name");
    }

    static protected List<String> buildStopwordList(String stopwordFileName) {
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
    
    public static Analyzer constructAnalyzer(String stopFileName) {
        Analyzer eanalyzer = new EnglishAnalyzer(
            StopFilter.makeStopSet(buildStopwordList(stopFileName))); // default analyzer
        return eanalyzer;        
    }
    
    void processAll() throws Exception {
        System.out.println("Indexing TREC collection...");
        
        IndexWriterConfig iwcfg = new IndexWriterConfig(analyzer);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        File indexDirDocs = new File(indexDir + "/docs/");
        File indexDirPara = new File(indexDir + "/para/");
        writer = new IndexWriter(FSDirectory.open(indexDirDocs.toPath()), iwcfg);

        iwcfg = new IndexWriterConfig(analyzer);
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);        
        paraWriter = new IndexWriter(FSDirectory.open(indexDirPara.toPath()), iwcfg);
        
        indexAll();
        
        writer.close();
        paraWriter.close();
    }

    void indexAll() throws Exception {
        if (writer == null) {
            System.err.println("Skipping indexing... Index already exists at " + indexDir + "!!");
            return;
        }
        
        File topDir = new File(prop.getProperty("coll"));
        indexDirectory(topDir);
    }

    private void indexDirectory(File dir) throws Exception {
        File[] files = dir.listFiles();
        for (int i=0; i < files.length; i++) {
            File f = files[i];
            if (f.isDirectory()) {
                System.out.println("Indexing directory " + f.getName());
                indexDirectory(f);  // recurse
            }
            else
                indexFile(f);
        }
    }
    
    void indexFile(File file) throws Exception {
        Document doc;

        System.out.println("Indexing file: " + file.getName());
        
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        FileInputStream inputstream = new FileInputStream(file);
        ParseContext pcontext = new ParseContext();
        
        //parsing the document using PDF parser
        PDFParser pdfparser = new PDFParser(); 
        pdfparser.parse(inputstream, handler, metadata, pcontext);
        
        String content = handler.toString();
        constructDoc(file.getName(), content);
    }

    void constructDoc(String fileName, String content) throws Exception {
        
        // Write out paragraphs...
        List<Paragraph> paragraphs = builder.constructParagraphs(docId, content);
        for (Paragraph p : paragraphs) {
            Document doc = new Document();
            doc.add(new Field(FIELD_ID, p.id, Field.Store.YES, Field.Index.NOT_ANALYZED));
            doc.add(new Field(FIELD_NAME, fileName, Field.Store.YES, Field.Index.NOT_ANALYZED));
            doc.add(new Field(fieldName, p.content,
                    Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
            paraWriter.addDocument(doc);        
        }
        
        // Write out the document
        Document doc = new Document();
        doc.add(new Field(FIELD_ID, String.valueOf(docId), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(FIELD_NAME, fileName, Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field(fieldName, content,
                Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
        writer.addDocument(doc);
        docId++;
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java PaperIndexer <prop-file>");
            args[0] = "init.properties";
        }

        try {
            PaperIndexer indexer = new PaperIndexer(args[0]);
            indexer.processAll();            
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
    
}
