/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

// commenting and output file writing to be fixed (ref : Predicting Query Performance Sigir)

package baselines;

import neural.common.DocumentVector;
import neural.common.EnglishAnalyzerSmartStopWords;
import neural.common.PerTermStat;
import neural.common.TRECQuery;
import neural.common.TRECQueryParse;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
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

public class Clarity {
    
    Properties      prop;
    String          indexPath;
    String          queryPath;               // path of the query file
    File            queryFile;               // the query file
    String          stopFilePath;
    IndexReader     indexReader;
    IndexSearcher   indexSearcher;
    String          resPath;                 // path of the res file
    FileWriter      resFileWriter;           // the res file writer
    FileWriter      baselineFileWriter;      // the res file writer
    int             numHits;                 // number of document to retrieveWithExpansionTermsFromFile
    String          runName;                 // name of the run
    List<TRECQuery> queries;
    File            indexFile;               // place where the index is stored
    Analyzer        analyzer;                // the analyzer
    boolean         boolIndexExists;         // boolean flag to indicate whether the index exists or not
    String          fieldToSearch;           // the field in the index to be searched
    String          fieldForFeedback;        // field, to be used for feedback
    TRECQueryParse trecQueryparser;
    int             simFuncChoice;
    float           param1, param2;
    float           mixingLambda;            // mixing weight, used for doc-col weight distribution
    long            vocSize;                 // vocabulary size
    int             numFeedbackDocs;         // number of feedback documents
    ClarityQueryVariants cqv;
    
    /**
     * Hashmap of Vectors of all feedback documents, keyed by luceneDocId.
     */
    HashMap<Integer, DocumentVector>    feedbackDocumentVectors;
    /**
     * HashMap of PerTermStat of all feedback terms, keyed by the term.
     */
    HashMap<String, PerTermStat>        feedbackTermStats;
    /**
     * HashMap of P(Q|D) for all feedback documents, keyed by luceneDocId.
     */
    HashMap<Integer, Float> hash_P_Q_Given_D;
    /**
     * List, for sorting the words in non-increasing order of probability.
     */
    List<WordProbability> list_PwGivenQ;
    /**
     * HashMap of P(w|R) for 'numFeedbackTerms' terms with top P(w|R) among each w in R,
     * keyed by the term with P(w|R) as the value.
     */
    HashMap<String, WordProbability> hashmap_PwGivenQ;

    
    public Clarity (Properties prop) throws IOException, Exception {

        this.prop = prop;
        /* property file loaded */

        /* setting the analyzer with English Analyzer with Smart stopword list */
        EnglishAnalyzerSmartStopWords engAnalyzer;
        stopFilePath = prop.getProperty("stopFilePath");
        if (null == stopFilePath)
            engAnalyzer = new neural.common.EnglishAnalyzerSmartStopWords();
        else
            engAnalyzer = new neural.common.EnglishAnalyzerSmartStopWords(stopFilePath);
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
        fieldToSearch = prop.getProperty("fieldToSearch", "content");
        fieldForFeedback = prop.getProperty("fieldForFeedback", "content");
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
        trecQueryparser = new TRECQueryParse(queryPath, analyzer, fieldToSearch);
        queries = constructQueries();
        /* constructed the query */

        /* numFeedbackDocs = number of top documents to select */
        numFeedbackDocs = Integer.parseInt(prop.getProperty("numFeedbackDocs"));

        /* setting mixing Lambda */
        if(param1>0.99)
            mixingLambda = 0.6f; // suggested in the reference paper
        else
            mixingLambda = param1;

        numHits = Integer.parseInt(prop.getProperty("numHits","1000"));
        
        vocSize = getVocabularySize();
        
        /* setting res path */
//        setRunName_ResFileName();
//        resFileWriter = new FileWriter(resPath);
//        System.out.println("Result will be stored in: "+resPath);
        /* res path set */
    }
    
    public Clarity(ClarityQueryVariants cqv) throws IOException {
    
        this.indexReader = cqv.indexReader;
        this.analyzer = cqv.analyzer; 
        this.numHits = cqv.numHits;
        
    }
    
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
    
    /**
     * Returns the vocabulary size of the collection for the field 'fieldForFeedback'.
     * @return vocSize : Total number of terms in the vocabulary
     * @throws IOException IOException
     */
    private long getVocabularySize() throws IOException {

        Fields fields = MultiFields.getFields(indexReader);
        Terms terms = fields.terms(fieldForFeedback);
        if(null == terms) {
            System.err.println("Field: "+fieldForFeedback);
            System.err.println("Error buildCollectionStat(): terms Null found");
        }
        vocSize = terms.getSumTotalTermFreq();  // total number of terms in the index in that field

        return vocSize;                         // total number of terms in the index in that field
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
    
    public void computeClarityScore() throws Exception {
        
        ScoreDoc[] hits;
        TopDocs topDocs;
        TopScoreDocCollector collector;
        HashMap<String, WordProbability> hashmap_PwGivenQ;
                        
        for (TRECQuery query : queries) {            
            float clarity = 0;
            collector = TopScoreDocCollector.create(numHits);
            Query luceneQuery = trecQueryparser.getAnalyzedQuery(query);

//            System.out.println("\n" + query.qid +": Initial query: " + luceneQuery.toString(fieldToSearch));

            /* PRF - initial retrieval performed */
            indexSearcher.search(luceneQuery, collector);
            topDocs = collector.topDocs();
            //System.out.println("docs retrieved : " + topDocsPRD1.totalHits);
            /* PRF */
            
            setFeedbackStats(topDocs, luceneQuery.toString(fieldToSearch).split(" "));
            
            /**
             * HashMap of P(w|Q) for 'numFeedbackTerms' terms,
             * keyed by the term with P(w|Q) as the value.
             */
            hashmap_PwGivenQ = queryLanguageModel(query, topDocs);
            
            for (Map.Entry<String, WordProbability> entry : hashmap_PwGivenQ.entrySet()) {
                String term = entry.getKey();
                WordProbability wp = entry.getValue();
                clarity = clarity + wp.expansionWeight;
            }
            System.out.print(query.qid + "\t" + clarity/hashmap_PwGivenQ.size() + "\n");
        }
    }
    
    public HashMap computeClarityScore(List<TRECQuery> queries) throws Exception {
        
        ScoreDoc[] hits;
        TopDocs topDocs;
        TopScoreDocCollector collector;
        HashMap<String, WordProbability> hashmap_PwGivenQ;
        HashMap<String, Float> clarityMap;
        
        clarityMap = new LinkedHashMap<>();
                        
        for (TRECQuery query : queries) {            
            float clarity = 0;
            collector = TopScoreDocCollector.create(numHits);
            Query luceneQuery = trecQueryparser.getAnalyzedQuery(query);

//            System.out.println("\n" + query.qid +": Initial query: " + luceneQuery.toString(fieldToSearch));

            /* PRF - initial retrieval performed */
            indexSearcher.search(luceneQuery, collector);
            topDocs = collector.topDocs();
            //System.out.println("docs retrieved : " + topDocsPRD1.totalHits);
            /* PRF */
            
            setFeedbackStats(topDocs, luceneQuery.toString(fieldToSearch).split(" "));
            
            /**
             * HashMap of P(w|Q) for 'numFeedbackTerms' terms,
             * keyed by the term with P(w|Q) as the value.
             */
            hashmap_PwGivenQ = queryLanguageModel(query, topDocs);
            
            for (Map.Entry<String, WordProbability> entry : hashmap_PwGivenQ.entrySet()) {
                String term = entry.getKey();
                WordProbability wp = entry.getValue();
                clarity = clarity + wp.expansionWeight;
            }
            System.out.print(query.qid + "\t" + clarity/hashmap_PwGivenQ.size() + "\n");
            clarityMap.put(query.qid, clarity/hashmap_PwGivenQ.size());
        }
        
        return clarityMap;
    }
    
    /**
     * Sets the following variables with feedback statistics: to be used consequently.<p>
     * {@link #feedbackDocumentVectors},<p> 
     * {@link #feedbackTermStats}, <p>
     * {@link #hash_P_Q_Given_D}
     * @param topDocs
     * @param analyzedQuery
     * @throws IOException 
     */
    public void setFeedbackStats(TopDocs topDocs, String[] analyzedQuery) throws IOException {

        feedbackDocumentVectors = new HashMap<>();
        feedbackTermStats = new HashMap<>();
        hash_P_Q_Given_D = new HashMap<>();

        ScoreDoc[] hits;
        int hits_length;
        hits = topDocs.scoreDocs;
        hits_length = hits.length;  // number of documents retrieved in the first retrieval
        
        for (int i = 0; i < Math.min(numFeedbackDocs, hits_length); i++) {
            // for each feedback document
            int luceneDocId = hits[i].doc;
            Document d = indexSearcher.doc(luceneDocId);
            DocumentVector docV = new DocumentVector(fieldForFeedback);
            docV = docV.getDocumentVector(luceneDocId, indexReader);
            if(docV == null)
                continue;
            feedbackDocumentVectors.put(luceneDocId, docV); // the feedback document vector is added in the list
            
            for (Map.Entry<String, PerTermStat> entrySet : docV.docPerTermStat.entrySet()) {
            // for each term of that feedback document
                String key = entrySet.getKey();
                PerTermStat value = entrySet.getValue();
                
                if(null == feedbackTermStats.get(key)) {
                // this feedback term is not already put in the hashmap, hence to be added;
                    Term termInstance = new Term(fieldForFeedback, key);
                    long cf = indexReader.totalTermFreq(termInstance);  // CF: Returns the total number of occurrences of term across all documents (the sum of the freq() for each doc that has this term).
                    long df = indexReader.docFreq(termInstance);        // DF: Returns the number of documents containing the term

                    feedbackTermStats.put(key, new PerTermStat(key, cf, df));
                }
            } // ends for each term of that feedback document
        } // ends for each feedback document

        // Calculating P(Q|d) for each feedback documents
        for (Map.Entry<Integer, DocumentVector> entrySet : feedbackDocumentVectors.entrySet()) {
            // for each feedback document
            int luceneDocId = entrySet.getKey();
            DocumentVector docV = entrySet.getValue();

            float p_Q_GivenD = 0;
            float smoothMLE = 0;
            
            for (String qTerm : analyzedQuery){
                smoothMLE = return_Smoothed_MLE_Log(qTerm, docV);
                p_Q_GivenD += smoothMLE;
            }                
            if(null == hash_P_Q_Given_D.get(luceneDocId)){
                
                /* compute P(D|Q) using Bayesian method of inversion
                *  P(Q|D) = P(D|Q)*P(Q)/P(D)
                *  P(Q|D) is computed in p_Q_GivenD 
                */
                p_Q_GivenD = p_Q_GivenD * 1/numFeedbackDocs;
                hash_P_Q_Given_D.put(luceneDocId, p_Q_GivenD);
            }
            else {
                System.err.println("Error while pre-calculating P(Q|d). "
                + "For luceneDocId: " + luceneDocId + ", P(Q|d) already existed.");
            }
        }
    }
    
    /**
     * Returns 'hashmap_PwGivenQ' containing all terms of PR docs (PRD) with 
     * weights calculated using IID Sampling <p>
     * P(w|R) = \sum{d\in PRD} {smoothedMLE(w,d)*smoothedMLE(D,Q)}
     * @param query The query
     * @param topDocs Initial retrieved document list
     * @return 'hashmap_PwGivenQ' containing all terms of PR docs with weights
     * @throws Exception 
     */
    public HashMap queryLanguageModel(TRECQuery query, TopDocs topDocs) throws Exception {

        float p_W_GivenQ_one_doc;
        list_PwGivenQ = new ArrayList<>();
        hashmap_PwGivenQ = new LinkedHashMap<>();
        int expansionTermCount = 0;
        float normFactor = 0;
        
        /* Calculating for each w_i in R: P(w_i|Q)~P(wi, q1 ... qk)
           P(wi, q1 ... qk) = \sum{d\in PRD} {P(w|D)*\prod_{i=1... k} {P(qi|D}} */

        for (Map.Entry<String, PerTermStat> entrySet : feedbackTermStats.entrySet()) {
            // for each t in R:
            String t = entrySet.getKey();
            p_W_GivenQ_one_doc = 0;

            for (Map.Entry<Integer, DocumentVector> docEntrySet : feedbackDocumentVectors.entrySet()) {
            // for each doc in RF-set
                int luceneDocId = docEntrySet.getKey();
                p_W_GivenQ_one_doc += return_Smoothed_MLE_Log(t, feedbackDocumentVectors.get(luceneDocId)) *
                    hash_P_Q_Given_D.get(luceneDocId);
            }
            
            /* computed P(w|Q) so far for each feedback term and stored in p_W_GivenQ_one_doc
            *  now multiply \log_2 (P(w|Q)/P_coll(w)
            *  and store back to p_W_GivenQ_one_docand compute clarity after summing them up
            */  
            p_W_GivenQ_one_doc = (float) ((float)p_W_GivenQ_one_doc * ((Math.log(p_W_GivenQ_one_doc) - 
                    Math.log((float)feedbackTermStats.get(t).getCF()/(float)vocSize)) * (float)(Math.log(10) - Math.log(2))));
            list_PwGivenQ.add(new WordProbability(t, p_W_GivenQ_one_doc));
        }
        
        // T1'= normalized top n terms of t1 <with highest weights>
        for (WordProbability singleTerm : list_PwGivenQ) {
            if (null == hashmap_PwGivenQ.get(singleTerm.w)) {
                hashmap_PwGivenQ.put(singleTerm.w, new WordProbability(singleTerm.w, singleTerm.p_w_given_Q));
            }
            //* else: The t is already entered in the hash-map 
        } //hashmap_PwGivenQ has all terms of PRDs along with their probabilities 

        /* selecting top numFeedbackTerms terms and normalize */
        // ++ Normalizing 
        for (Map.Entry<String, WordProbability> entrySet : hashmap_PwGivenQ.entrySet()) {
            WordProbability wp = entrySet.getValue();
//            wp.p_w_given_Q /= normFactor;
            wp.expansionWeight = wp.p_w_given_Q;
        }
        // -- Normalizing done
        
        return hashmap_PwGivenQ; 
    }   // ends RM1()
    
    public float return_Smoothed_MLE_Log(String t, DocumentVector dv) throws IOException {
        
        float smoothedMLEofTerm = 1;
        PerTermStat docPTS;
        docPTS = dv.docPerTermStat.get(t);
        PerTermStat colPTS = feedbackTermStats.get(t);

        if (colPTS != null) 
            smoothedMLEofTerm = 
                ((docPTS!=null)?(mixingLambda * (float)docPTS.getCF() / (float)dv.getDocSize()):(0)) /
                ((feedbackTermStats.get(t)!=null)?((1.0f-mixingLambda)*(float)feedbackTermStats.get(t).getCF()/(float)vocSize):0);
     
        return (float)Math.log(1+smoothedMLEofTerm);

    } // ends return_Smoothed_MLE_Log()
    
    public static void main(String[] args) throws IOException, Exception {

        String usage = "java Clarity <properties-file>\n"
                + "Properties file must contain the following fields:\n"
                + "1. stopFilePath: path of the stopword file\n"
                + "2. indexPath: Path of the index\n"
                + "3. queryPath: path of the query file (in proper xml format)\n"
                + "4. resPath: path of the directory to store res file\n"
                + "5. numFeedbackDocs: number of feedback documents to use\n"
                + "6. similarityFunction: 0.DefaultSimilarity, 1.BM25Similarity, 2.LMJelinekMercerSimilarity, 3.LMDirichletSimilarity\n";               
                
        Properties prop = new Properties();

        if(1 != args.length) {
            System.out.println("Usage: " + usage);
            args = new String[1];
            args[0] = "clarity.properties";
            System.exit(1);
        }
        prop.load(new FileReader(args[0]));
        Clarity clarity = new Clarity(prop);

        clarity.computeClarityScore();
    } // ends main()
    
}