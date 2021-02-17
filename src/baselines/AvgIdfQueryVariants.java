/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package baselines;

import neural.common.EnglishAnalyzerSmartStopWords;
import common.TRECQuery;
import common.TRECQueryParser;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import static java.lang.System.exit;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neuralqpp.DSDQueryParse;
import neuralqpp.QueryVariant;
import neuralqpp.UQVQueryParse;
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

public class AvgIdfQueryVariants {
    
    static IndexReader indexReader;
    static int totalDocs;
    String indexPath;
    String trecQueryPath;
    String queryVariantsPath;
    String outFilePath;
    File indexFile;
    File trecQueryFile;
    File queryVariantsFile;
    boolean boolIndexExists;
    IndexSearcher indexSearcher;
    String stopFilePath;
    Analyzer analyzer;
    DecimalFormat df;
    static FileWriter fileWriter;
    TRECQueryParser trecQueryparser;
    UQVQueryParse uqvQUeryParser;
    DSDQueryParse dsdVariantParser;
    List<TRECQuery> trecQueries;
    List<QueryVariant> queryVariantsUQV;
    List<QueryVariant> queryVariantDSD;
    Map<String, Double> trecQueryIdfList;
    Map<String, List<Double>> queryVariantIdfList;    
    
    
    public AvgIdfQueryVariants(String indexPath, String trecQueryPath, String queryVariantsPath, String outFilePath) 
            throws IOException, SAXException, Exception {
        
        this.indexPath = indexPath;
        this.trecQueryPath = trecQueryPath;
        this.queryVariantsPath = queryVariantsPath;
        this.outFilePath = outFilePath;
        
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
        
        /* setting trec query path */
        System.out.println("queryPath set to: " + trecQueryPath);
        trecQueryFile = new File(trecQueryPath);
        /* query path set */
        
        /* constructing trec query */
        trecQueryparser = new TRECQueryParser(trecQueryPath, analyzer);
        trecQueries = constructTrecQueries();
        /* constructed trec query */
        
        /* setting query variants path */
        System.out.println("queryVariantPath set to: " + queryVariantsPath);
        queryVariantsFile = new File(queryVariantsPath);
        /* query variants path set */
        
        queryVariantIdfList = new LinkedHashMap<>();
        
        df = new DecimalFormat("#.####");
        df.setRoundingMode(RoundingMode.CEILING);
        
        fileWriter = new FileWriter(outFilePath + "UQV_query_avg_idf.txt");
    }
    
    /**
     * Parses the query from the file and makes a List<TRECQuery> 
     *  containing all the queries (RAW query read)
     * @return A list with the all the queries
     * @throws Exception 
     */
    private List<TRECQuery> constructTrecQueries() throws Exception {

        trecQueryparser.queryFileParse();
        return trecQueryparser.queries;
    } // ends constructQueries()
    
    public void calculateQueryAvgIdf() throws SAXException, Exception {
        
        String qTitle;
        trecQueryIdfList = new LinkedHashMap<>();
        
        for (TRECQuery query : trecQueries) {
            
            qTitle = query.qtitle.replaceAll(";", "").replaceAll("\\d", "").trim();
//            System.out.println("query : " + qTitle);
            qTitle = analyzeQuery(analyzer, qTitle, "content").toString();
//            System.out.println("query analyzed: " + qTitle);
            
            String qTerms[] = qTitle.split(" ");
//            System.out.println("total terms : " + qTerms.length);
            double termIdf = 0;
            for (String term : qTerms) {
                termIdf = termIdf + getIdf(term);
            }
            
            termIdf = termIdf/qTerms.length;
//            System.out.println("Average idf of the query : " + termIdf);
            trecQueryIdfList.put(query.qid, termIdf);
//            System.out.println("$$$$$$$ : " + trecQueryIdfList);
        }
    }
    
    public double getIdf(String term) throws IOException {
        
        totalDocs = indexReader.maxDoc();
//        System.out.println("total no. of docs in the collection : " + totalDocs);
        
        Fields fields = MultiFields.getFields(indexReader);
        Term termInstance = new Term("content", term);
        long df = indexReader.docFreq(termInstance);       // DF: Returns the number of documents containing the term
        double idf = Math.log((float)(totalDocs)/(float)(df+1));
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
    
    public void additiveSmoothing(HashMap<String, List<Double>> variantList) {
        
        List<Double> avgIdfList;
        Set<Double> distinct;
                
        for (Map.Entry<String, List<Double>> entry : queryVariantIdfList.entrySet()) {
            
            avgIdfList = entry.getValue();
            distinct = new HashSet<>(avgIdfList);
//            System.out.println("DISTINCT : " + distinct);
            
            double addSmoothIdf = 0;
            for (Double idf : avgIdfList) {
                addSmoothIdf += (idf + 1) / (Collections.frequency(distinct, idf) + 1*avgIdfList.size());
//                System.out.println("%%%%%%%% : " + addSmoothIdf);
            }
            System.out.println(entry.getKey() + "\t" + df.format(addSmoothIdf / avgIdfList.size()));
        }
    }
    
    public void relativeDifference(HashMap<String, List<Double>> variantList) {
        
        List<Double> avgIdfList;
        Set<Double> distinct;
                
        for (Map.Entry<String, List<Double>> entry : queryVariantIdfList.entrySet()) {
            
            double relDiffIdf = 0;
            avgIdfList = entry.getValue();
                       
            for (Double idf : avgIdfList) {
                relDiffIdf += idf;
            }
            relDiffIdf = relDiffIdf / avgIdfList.size();
//            System.out.println("%%%%%%%% : " + relDiffIdf);
            relDiffIdf = (trecQueryIdfList.get(entry.getKey()) - relDiffIdf) / trecQueryIdfList.get(entry.getKey());
            System.out.println(entry.getKey() + "\t" + df.format(relDiffIdf));
        }
    }
    
    public void calculateAvgIdfUQV() throws FileNotFoundException, IOException, Exception {
        
        String queryTerms[];
        
        /* constructing UQV queries */
        uqvQUeryParser = new UQVQueryParse(queryVariantsPath, analyzer);
        queryVariantsUQV = constructUQVVariants();
        /* constructed TREC query */
        
        for (QueryVariant query : queryVariantsUQV) {
            String qtitle = query.qtitle.replaceAll(";", "").replaceAll("\\d", "").trim();
            qtitle = analyzeQuery(analyzer, qtitle, "content").toString();
            
            String qTerms[] = qtitle.split(" ");
            double termIdf = 0;
            for (String term : qTerms) {
                termIdf = termIdf + getIdf(term);
            }            
            termIdf = termIdf/qTerms.length;
            if (queryVariantIdfList.containsKey(query.qid)) {
                queryVariantIdfList.get(query.qid).add(termIdf);
            }
            else {
                List<Double> variIdfList = new ArrayList<>();
                variIdfList.add(termIdf);
                queryVariantIdfList.put(query.qid, variIdfList);
            }            
        }
//        additiveSmoothing(queryVariantIdfList);
        relativeDifference((HashMap<String, List<Double>>) queryVariantIdfList);
    }
    
    /**
     * Parses the query from the UQV file and makes a list of query variants
     * @return A list with the all the query variants
     * @throws Exception 
     */
    private List<QueryVariant> constructUQVVariants() throws Exception {

        uqvQUeryParser.queryFileParse();
        return uqvQUeryParser.queries;
    } // ends constructUQVVariants()
    
    public void calculateAvgIdfDSD() throws FileNotFoundException, IOException, Exception {
        
        /* constructing TREC DSD query variants */
        dsdVariantParser = new DSDQueryParse(queryVariantsPath, analyzer);
        queryVariantDSD = constructDSDVariants();
        /* constructed TREC DSD query variants */
        
        for (QueryVariant query : queryVariantDSD) {
            String qtitle = query.qtitle.replaceAll(";", "").replaceAll("\\d", "").trim();
            qtitle = analyzeQuery(analyzer, qtitle, "content").toString();
            
            String qTerms[] = qtitle.split(" ");
            double termIdf = 0;
            for (String term : qTerms) {
                termIdf = termIdf + getIdf(term);
            }            
            termIdf = termIdf/qTerms.length;
            if (queryVariantIdfList.containsKey(query.qid)) {
                queryVariantIdfList.get(query.qid).add(termIdf);
            }
            else {
                List<Double> variIdfList = new ArrayList<>();
                variIdfList.add(termIdf);
                queryVariantIdfList.put(query.qid, variIdfList);
            }  
        }
//        additiveSmoothing((HashMap<String, List<Double>>) queryVariantIdfList);
        relativeDifference((HashMap<String, List<Double>>) queryVariantIdfList);
    }
    
    /**
     * Parses the query from the DSD file and makes a list of query variants
     * @return A list with the all the query variants
     * @throws Exception 
     */
    private List<QueryVariant> constructDSDVariants() throws Exception {

        dsdVariantParser.queryFileParse();
        return dsdVariantParser.queries;
    } // ends constructDSDVariants()
    
    public static void main(String[] args) throws IOException, Exception {
        
        String indexPath, trecQueryPath, queryVariantsPath, outFilePath;

        String usage = "java AvgIdfQueryVariantsAddSmooth <arguments in order> :\n"
                + "1. Path of the index.\n"
                + "2. Trec query file path (required if estimator is relative difference, else provide 0 (unused)\n"
                + "3. Path of the query variant file.\n"
                + "4. Path of the o/p file [<qid>\t<query>\t<average-idf of qterms with additive smoothing>]"; 
        
        if(args.length != 4) {
            System.out.println("Usage: " + usage);
            args = new String[4];
            exit(0);
        }
        
        indexPath = args[0];
        trecQueryPath = args[1];
        queryVariantsPath = args[2];
        outFilePath = args[3];
        
        AvgIdfQueryVariants aiqv = new AvgIdfQueryVariants(indexPath, trecQueryPath, queryVariantsPath, outFilePath);
        
        totalDocs = indexReader.maxDoc();
        System.out.println("total no. of docs in the collection : " + totalDocs);
        aiqv.calculateQueryAvgIdf();
        
        aiqv.calculateAvgIdfUQV(); // for kurland UQV query variants
//        aiqv.calculateAvgIdfDSD(); // for automatically generated variants by RLM / W2V (debasis/suchana/derek)
        fileWriter.close();
    }    
}
