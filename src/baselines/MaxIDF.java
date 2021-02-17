/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package baselines;

import neural.common.EnglishAnalyzerSmartStopWords;
import neural.common.TRECQuery;
import neural.common.TRECQueryParse;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import static java.lang.System.exit;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.xml.sax.SAXException;

/**
 *
 * @author suchana
 */

public class MaxIDF {
    
    static IndexReader indexReader;
    static int totalDocs;
    String indexPath;
    String queryPath;
    String avgIdfPath;
    File indexFile;
    File queryFile;
    boolean boolIndexExists;
    IndexSearcher indexSearcher;
    String stopFilePath;
    Analyzer analyzer;
    static FileWriter fileWriter;
    TRECQueryParse trecQueryparser;
    List<TRECQuery> queries;

    
    public MaxIDF(String indexPath, String queryPath, String avgIdfPath) throws IOException {
        
        this.indexPath = indexPath;
        this.queryPath = queryPath;
        this.avgIdfPath = avgIdfPath;
        
        /* setting the analyzer with English Analyzer with Smart stopword list */
        EnglishAnalyzerSmartStopWords engAnalyzer;
        stopFilePath = "/home/suchana/smart-stopwords";
        if (null == stopFilePath)
            engAnalyzer = new neural.common.EnglishAnalyzerSmartStopWords();
        else
            engAnalyzer = new neural.common.EnglishAnalyzerSmartStopWords(stopFilePath);
        analyzer = engAnalyzer.setAndGetEnglishAnalyzerWithSmartStopword();
        /* analyzer set: analyzer */
        
        /* index path setting */
        System.out.println("indexPath set to: " + indexPath);
        indexFile = new File(indexPath);
        Directory indexDir = FSDirectory.open(indexFile.toPath());

        if (!DirectoryReader.indexExists(indexDir)) {
            System.err.println("Index doesn't exists in "+indexPath);
            boolIndexExists = false;
            System.exit(1);
        }
        /* index path set */
        
        /* setting indexReader */
        indexReader = DirectoryReader.open(FSDirectory.open(indexFile.toPath()));
        /* indexReader set */
        
        /* setting query path */
        System.out.println("queryPath set to: " + queryPath);
        queryFile = new File(queryPath);
        /* query path set */
        
        fileWriter = new FileWriter(avgIdfPath + "maxIDF_trec");
    }
    
    /**
     * Parses the query from the file and makes a List<TRECQuery> 
     *  containing all the queries (RAW query read)
     * @return A list with the all the queries
     * @throws Exception 
     */
    private List<TRECQuery> constructQueries() throws Exception {

        trecQueryparser.queryFileParse();
        return trecQueryparser.queries;
    } // ends constructQueries()
    
    public StringBuffer analyzeQuery(Analyzer analyzer, String text, String fieldName) throws IOException {

        StringBuffer tokenizedContentBuff = new StringBuffer();

        TokenStream stream = analyzer.tokenStream(fieldName, new StringReader(text));
        CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);

        stream.reset();

        while (stream.incrementToken()) {
            String term = termAtt.toString();
            tokenizedContentBuff.append(term).append(" ");
        }

        stream.end();
        stream.close();

        return tokenizedContentBuff;
    }
    
    public double getIdf(String term) throws IOException {
        
        Fields fields = MultiFields.getFields(indexReader);
        Term termInstance = new Term("content", term);
        long df = indexReader.docFreq(termInstance);       // DF: Returns the number of documents containing the term
        System.out.println("DF : " + df);
        double idf = Math.log((float)(totalDocs)/(float)(df+1));
        System.out.println("IDF value : " + idf);
        return idf;
    }
    
    public void calculateQueryAvgIdf() throws SAXException, Exception {
        
        String qTitle;
        double maxIdf = 0;
        
        DecimalFormat df = new DecimalFormat("#.####");
        df.setRoundingMode(RoundingMode.CEILING);
        
        /* constructing TREC queries */
        trecQueryparser = new TRECQueryParse(queryPath, analyzer);
        queries = constructQueries();
        /* constructed TREC query */
        
        for (TRECQuery query : queries) {
            
            qTitle = query.qtitle.replaceAll(";", "").replaceAll("\\d", "").trim();
            System.out.println("query : " + qTitle);
            qTitle = analyzeQuery(analyzer, qTitle, "content").toString();
            System.out.println("query analyzed: " + qTitle);
            
            String qTerms[] = qTitle.split(" ");
            System.out.println("total terms : " + qTerms.length);
            for (String term : qTerms) {
                if (getIdf(term) > maxIdf) {
                    maxIdf = getIdf(term);
                }
            }
            System.out.println("Max idf : " + maxIdf);
            maxIdf = maxIdf/(double)qTerms.length;
            System.out.println("Average idf of the query : " + maxIdf);
            fileWriter.write(query.qid + "\t" + df.format(maxIdf) + "\n");
        }
    }
    
    public static void main(String[] args) throws IOException, Exception {
        
        String indexPath, queryPath, avgIdfPath;

        String usage = "java MaxIDF <arguments in order> :\n"
                + "1. Path of the index.\n"
                + "2. Path of the query file.\n"
                + "3. Path of the o/p file [<qid>\t<average-idf of qterms>]"; 
        
        if(args.length != 3) {
            System.out.println("Usage: " + usage);
            args = new String[3];
            exit(0);
        }
        
        indexPath = args[0];
        queryPath = args[1];
        avgIdfPath = args[2];
        
        MaxIDF midf = new MaxIDF(indexPath, queryPath, avgIdfPath);
        
        totalDocs = indexReader.maxDoc();
        System.out.println("total no. of docs in the collection : " + totalDocs);
        midf.calculateQueryAvgIdf(); 
        fileWriter.close();
    }
    
}
