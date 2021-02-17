/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

// commenting and writing to file is left (ref : Effective Pre-retrieval Query Performance Prediction
// Using Similarity and Variability Evidence)

package baselines;

import static neural.common.CommonVariables.FIELD_FULL_BOW;
import common.EnglishAnalyzerWithSmartStopword;
import common.TRECQuery;
import common.TRECQueryParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author suchana
 */

public class AverageVariance {
    
    Properties      prop;
    String          indexPath;
    String          queryPath;               // path of the query file
    File            queryFile;               // the query file
    String          stopFilePath;
    IndexReader     indexReader;
    IndexSearcher   indexSearcher;
    String          resPath;                 // path of the res file
    FileWriter      resFileWriter;           // the res file writer
    int             numHits;                 // number of document to retrieveWithExpansionTermsFromFile
    List<TRECQuery> queries;
    File            indexFile;               // place where the index is stored
    Analyzer        analyzer;                // the analyzer
    boolean         boolIndexExists;         // boolean flag to indicate whether the index exists or not
    String          fieldToSearch;           // the field in the index to be searched
    TRECQueryParser trecQueryparser;
    int             simFuncChoice;
    float           param1, param2;
    long            vocSize;                 // vocabulary size
    List<PerQueryStat> perQueryInfo;
    DecimalFormat df;

    
    public AverageVariance(Properties prop) throws IOException, Exception {

        this.prop = prop;
        /* property file loaded */

        /* setting the analyzer with English Analyzer with Smart stopword list */
        EnglishAnalyzerWithSmartStopword engAnalyzer;
        stopFilePath = prop.getProperty("stopFilePath");
        if (null == stopFilePath)
            engAnalyzer = new common.EnglishAnalyzerWithSmartStopword();
        else
            engAnalyzer = new common.EnglishAnalyzerWithSmartStopword(stopFilePath);
        analyzer = engAnalyzer.setAndGetEnglishAnalyzerWithSmartStopword();
        /* analyzer set: analyzer */

        /* index path setting */
        indexPath = prop.getProperty("indexPath");
        System.out.println("indexPath set to: " + indexPath);
        indexFile = new File(prop.getProperty("indexPath"));
        Directory indexDir = FSDirectory.open(indexFile.toPath());

        if (!DirectoryReader.indexExists(indexDir)) {
            System.err.println("Index doesn't exists in "+indexPath);
            boolIndexExists = false;
            System.exit(1);
        }
        fieldToSearch = prop.getProperty("fieldToSearch", FIELD_FULL_BOW);
        //System.out.println("Searching field for retrieval: " + fieldToSearch);
        /* index path set */

        simFuncChoice = Integer.parseInt(prop.getProperty("similarityFunction"));
        if (null != prop.getProperty("param1"))
            param1 = Float.parseFloat(prop.getProperty("param1"));
        if (null != prop.getProperty("param2"))
            param2 = Float.parseFloat(prop.getProperty("param2"));

        /* setting indexReader and indexSearcher */
        indexReader = DirectoryReader.open(FSDirectory.open(indexFile.toPath()));
        indexSearcher = new IndexSearcher(indexReader);
        setSimilarityFunction(simFuncChoice, param1, param2);
        /* indexReader and searcher set */

        /* setting query path */
        queryPath = prop.getProperty("queryPath");
        System.out.println("queryPath set to: " + queryPath);
        queryFile = new File(queryPath);
        /* query path set */

        /* constructing the query */
        trecQueryparser = new TRECQueryParser(queryPath, analyzer, fieldToSearch);
        queries = constructQueries();
//        System.out.println("queries : " + queries.get(0).qtitle);
        /* constructed the query */
        
        numHits = Integer.parseInt(prop.getProperty("numHits","1000"));

        /* setting res path */
//        resFileWriter = new FileWriter(resPath);
//        System.out.println("Result will be stored in: "+resPath);
        /* res path set */
        
        df = new DecimalFormat("#.####");
        df.setRoundingMode(RoundingMode.CEILING);
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

    /**
     * Sets indexSearcher.setSimilarity() with parameter(s)
     * @param choice similarity function selection flag
     * @param param1 similarity function parameter 1
     * @param param2 similarity function parameter 2
     */
    private void setSimilarityFunction(int choice, float param1, float param2) {

            switch(choice) {
            case 0:
                indexSearcher.setSimilarity(new DefaultSimilarity());
                System.out.println("Similarity function set to DefaultSimilarity");
                break;
            case 1:
                indexSearcher.setSimilarity(new BM25Similarity(param1, param2));
                System.out.println("Similarity function set to BM25Similarity"
                    + " with parameters: " + param1 + " " + param2);
                break;
            case 2:
                indexSearcher.setSimilarity(new LMJelinekMercerSimilarity(param1));
                System.out.println("Similarity function set to LMJelinekMercerSimilarity"
                    + " with parameter: " + param1);
                break;
            case 3:
                indexSearcher.setSimilarity(new LMDirichletSimilarity(param1));
                System.out.println("Similarity function set to LMDirichletSimilarity"
                    + " with parameter: " + param1);
                break;
        }
    } // ends setSimilarityFunction()
    
    public void calculateAvgVarEstimator() throws Exception {
        
        ScoreDoc[] hits;
        TopDocs topDocs;
        TopScoreDocCollector collector;
        List<Double> w_d_t_List;
        perQueryInfo = new ArrayList<PerQueryStat>();
        
        for (TRECQuery query : queries) { 
            
            PerQueryStat pqs = new PerQueryStat();
            pqs.qid = query.qid;
//            System.out.println("Qid : " + query.qid);
            
            String qTerm = analyzeQuery(analyzer, query.qtitle, fieldToSearch).toString();
            String qTerms[] = qTerm.trim().split(" ");
            pqs.qTerms = qTerms;
//            System.out.println("qterms : " + qTerms.length);
            
            pqs.w_d_t_list = new HashMap<>();
            pqs.w_t_bar = new HashMap<>();
                            
            for (String qterm : qTerms) {
                
                w_d_t_List = new ArrayList<>();
                double w_t_bar = 0;
//                System.out.println("\nsingle qterm : " + qterm);
                collector = TopScoreDocCollector.create(numHits);
                Query luceneQuery = trecQueryparser.getAnalyzedQuery(qterm.trim());
//                System.out.println(query.qid +": Initial query: " + luceneQuery.toString(fieldToSearch));
                
                /* PRF - initial retrieval performed */
                indexSearcher.search(luceneQuery, collector);
                topDocs = collector.topDocs();
//                System.out.println("docs retrieved : " + topDocs.totalHits);
                /* PRF */
                
                pqs.docFreq = topDocs.totalHits;
                
                hits = topDocs.scoreDocs;
                for (int i = 0; i < hits.length; i++) {
//            // for each feedback document
                    int luceneDocId = hits[i].doc;
//                    System.out.print("lucene docid : " + luceneDocId + "\t");
                    Document d = indexSearcher.doc(luceneDocId);
//                    System.out.println("Doc : " + d);
                    w_d_t_List.add(getTF(qterm, d) * getIDF(qterm));
                    w_t_bar += getTF(qterm, d) * getIDF(qterm);
                }
//                System.out.println("individual tf-idf list size : " + w_d_t_List.size());
                pqs.w_d_t_list.put(qterm, w_d_t_List);
                
                pqs.w_t_bar.put(qterm, w_t_bar/hits.length);
//                System.out.println("average tf-idf : " + w_t_bar/hits.length);
            }  
//            System.out.println("TF-idf list size : " + pqs.w_d_t_list.size());
            perQueryInfo.add(pqs);
        } 
//        System.out.println("$$$$$$$$$ : " + perQueryInfo.size());
        
        // calculate the estimator \sigma_1
        
        for (PerQueryStat entry : perQueryInfo) {
            
            String qid = entry.getQid();
            System.out.print(qid);
            
            String qterms[] = entry.getQtermList();
//            System.out.println("QTERMS : " + qterms.length);
            
            double sigma = 0;
            
            for (String term : qterms) {
                
                List<Double> tfIdfList = entry.getTfList().get(term);
                double variability = 0;
                for (Double value : tfIdfList) {                    
                    variability += Math.pow((value - entry.getWTBar().get(term)), 2);
                }
                variability = Math.sqrt(variability * (1/entry.getDocFreq()));
                sigma += variability;
            }
            System.out.println("\t" + df.format(sigma));
        }
    }
    
    public double getTF(String qTerm, Document doc) throws IOException {
        
        double count = 0;            
        // calculate term frequency
        String docTerms [] = doc.toString().split(" ");
        for (String s : docTerms) {
            if (s.trim().equalsIgnoreCase(qTerm.trim()))
                count++;
        }
        double tf = count / (double)docTerms.length;

        return tf;
    }
    
    public double getIDF(String term) throws IOException {
        
        int docCount = indexReader.maxDoc();      // total number of documents in the index
        Term termInstance = new Term(fieldToSearch, term);
        long df = indexReader.docFreq(termInstance);       // DF: Returns the number of documents containing the term

        double idf;
        idf = Math.log((float)(docCount)/(float)(df+1));
//        System.out.println("IDF value : " + idf);

        return idf;
    }
    
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
    
    public static void main(String[] args) throws IOException, Exception {
    
        String usage = "java AverageVariance <properties-file>\n"
                    + "Properties file must contain the following fields:\n"
                    + "1. indexPath: Path of the index\n"
                    + "2. queryPath: path of the query file (in proper xml format)\n"
                    + "3. resPath: path of the directory to store res file\n"
                    + "4. similarityFunction: 0.DefaultSimilarity, 1.BM25Similarity, 2.LMJelinekMercerSimilarity, 3.LMDirichletSimilarity\n";

            Properties prop = new Properties();

            if(1 != args.length) {
                System.out.println("Usage: " + usage);
//                args = new String[1];
//                args[0] = "foo.properties";
                System.exit(1);
            }
            prop.load(new FileReader(args[0]));
            AverageVariance avar = new AverageVariance(prop);

            avar.calculateAvgVarEstimator();
    } // ends main()
    
}
