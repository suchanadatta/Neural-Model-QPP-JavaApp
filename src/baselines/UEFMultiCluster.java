package baselines;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import static neural.common.CommonVariables.FIELD_BOW;
import static neural.common.CommonVariables.FIELD_FULL_BOW;
import neural.common.DocumentVector;
import neural.common.EnglishAnalyzerSmartStopWords;
import neural.common.PerTermStat;
import neural.common.TRECQuery;
import neural.common.TRECQueryParse;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.AfterEffectB;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicModelIF;
import org.apache.lucene.search.similarities.DFRSimilarity;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.NormalizationH2;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
 *
 * @author suchana
 */

public class UEFMultiCluster {

    Properties      prop;
    String          indexPath;
    String          queryPath;               // path of the query file
    File            queryFile;               // the query file
    String          wrigPath;
    File            wrigFile;
    String          stopFilePath;
    IndexReader     indexReader;
    IndexSearcher   indexSearcher;
    String          resPathLM;                 // path of the res file
    String          resPathRLM;
    FileWriter      resFileWriterLM;           // the res file writer
    FileWriter      resFileWriterRLM;      // the res file writer
    int             numHits;                 // number of document to retrieveWithExpansionTermsFromFile
    String          runName;                 // name of the run
    List<TRECQuery> queries;
    File            indexFile;               // place where the index is stored
    Analyzer        analyzer;                // the analyzer
    boolean         boolIndexExists;         // boolean flag to indicate whether the index exists or not
    String          fieldToSearch;           // the field in the index to be searched
    String          fieldForFeedback;        // field, to be used for feedback
    TRECQueryParse  trecQueryparser;
    int             simFuncChoice;
    float           param1, param2;
    long            vocSize;                 // vocabulary size
    HashMap<String, TopDocs> allTopDocsFromFileHashMap;     // For feedback from file, to contain all topdocs from file
    float           mixingLambda;            // mixing weight, used for doc-col weight distribution
    int             numFeedbackTerms;        // number of feedback terms at the first step
    int             numFeedbackDocs;         // number of feedback documents
    int             numCluster;
    int             sizeCluster;
    float           QMIX;                    // query mix to weight between P(w|R) and P(w|Q)
    TopDocs          topDocs;
    TopScoreDocCollector collector;
    SpearmansCorrelation spc;
    HashMap<String, Float> wrigVal;
    
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
    List<WordProbabilityUEF> list_PwGivenR;
    /**
     * HashMap of P(w|R) for 'numFeedbackTerms' terms with top P(w|R) among each w in R,
     * keyed by the term with P(w|R) as the value.
     */
    HashMap<String, WordProbabilityUEF> hashmap_PwGivenR;
    
    List<ClusterInfo> list_clustInfo;
    

    public UEFMultiCluster(Properties prop) throws IOException, Exception {

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
        fieldToSearch = prop.getProperty("fieldToSearch", FIELD_FULL_BOW);
        fieldForFeedback = prop.getProperty("fieldForFeedback", FIELD_BOW);
        //System.out.println("Searching field for retrieval: " + fieldToSearch);
        //System.out.println("Field for Feedback: " + fieldForFeedback);
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
        
        /* calculate vocabulary size */
        vocSize = getVocabularySize();

        /* setting query path */
        queryPath = prop.getProperty("queryPath");
        System.out.println("queryPath set to: " + queryPath);
        queryFile = new File(queryPath);
        /* query path set */
        
        /* setting wrig path */
        wrigPath = prop.getProperty("wrigPath");
        System.out.println("WRIG(LR) path set to: " + wrigPath);
        wrigFile = new File(wrigPath);
        constructWrigMap();
        /* wrig path set */

        /* constructing the query */
        trecQueryparser = new TRECQueryParse(queryPath, analyzer, fieldToSearch);
        queries = constructQueries();
        System.out.println("Query list : " + queries.size());
        /* constructed the query */

        /* numFeedbackTerms = number of top terms to select in two steps */
        numFeedbackTerms = Integer.parseInt(prop.getProperty("numFeedbackTerms"));
        /* numFeedbackDocs = number of top documents to select */
        numFeedbackDocs = Integer.parseInt(prop.getProperty("numFeedbackDocs"));
        
        /* number of clusters to be chosen */
        numCluster = Integer.parseInt(prop.getProperty("numCluster"));
        /* size of eac cluster */
        sizeCluster = Integer.parseInt(prop.getProperty("sizeCluster"));

        /* setting mixing Lambda */
        if(param1 > 0.99)
            mixingLambda = 0.8f;
        else
            mixingLambda = param1;

        numHits = Integer.parseInt(prop.getProperty("numHits","1000"));
        QMIX = Float.parseFloat(prop.getProperty("rm3.queryMix"));
        
        /* setting res path */
        resPathLM = prop.getProperty("resPath") + "UEF_LM.res";
        resPathRLM = prop.getProperty("resPath") + "UEF_RLM.res";

        resFileWriterLM = new FileWriter(resPathLM);
        System.out.println("Result will be stored in: "+ resPathLM);
        
        resFileWriterRLM = new FileWriter(resPathRLM);
        System.out.println("Result will be stored in: "+ resPathRLM);
        /* res path set */
        
        spc = new SpearmansCorrelation(this);
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
            case 4:
                indexSearcher.setSimilarity(new DFRSimilarity(new BasicModelIF(), new AfterEffectB(), new NormalizationH2()));
                System.out.println("Similarity function set to DFRSimilarity with default parameters");
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
     * @return A list with all the queries
     * @throws Exception 
     */
    private List<TRECQuery> constructQueries() throws Exception {

        trecQueryparser.queryFileParse();
        return trecQueryparser.queries;
    } // ends constructQueries()
    
    private void constructWrigMap() throws FileNotFoundException, IOException {
        
        wrigVal = new HashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(wrigFile));
        String line = br.readLine();
        while(line != null){ 
            String[] words = line.split("\t");
            wrigVal.put(words[0], Float.parseFloat(words[1]));
            line = br.readLine();            
        }
//        System.out.println("WRIG : " + wrigVal);
    }
    
    public TopDocs retrieve(TRECQuery query, int numFeedbackDocs) throws Exception {

        System.out.println(query.qid+": \t" + query.luceneQuery.toString());

        return retrieve(query.luceneQuery, numFeedbackDocs);
    }
    
    public TopDocs retrieve(Query luceneQuery, int numHits) throws Exception {
        
        TopDocs topDocs;

        System.out.println(luceneQuery.toString());
        topDocs = search(luceneQuery, numHits);

        return topDocs;
    }
    
    public TopDocs search(Query query, int numHits) throws IOException {

        topDocs = null;
        collector = TopScoreDocCollector.create(numHits);

        indexSearcher.search(query, collector);
        topDocs = collector.topDocs();

        return topDocs;
    } 
    
    public void setFeedbackStats(TopDocs topDocs, String[] analyzedQuery) throws IOException {

        feedbackDocumentVectors = new HashMap<>();
        feedbackTermStats = new HashMap<>();
        hash_P_Q_Given_D = new HashMap<>();

        ScoreDoc[] hits;
        int hits_length;
        hits = topDocs.scoreDocs;
        hits_length = hits.length;    // number of documents retrieved in the first retrieval
        
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
//                    System.out.println("CF : " + cf);
                    long df = indexReader.docFreq(termInstance);        // DF: Returns the number of documents containing the term
//                    System.out.println("DF : " + df);
                    
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
//                System.out.println("QTERM : " + qTerm);
                smoothMLE = return_Smoothed_MLE_Log(qTerm, docV);
//                System.out.println("MLE : " + smoothMLE);
                p_Q_GivenD += smoothMLE;
            }                
            if(null == hash_P_Q_Given_D.get(luceneDocId)){
                hash_P_Q_Given_D.put(luceneDocId, p_Q_GivenD);
            }
            else {
                System.err.println("Error while pre-calculating P(Q|d). "
                + "For luceneDocId: " + luceneDocId + ", P(Q|d) already existed.");
            }
        }
    }
    
    public float return_Smoothed_MLE_Log(String t, DocumentVector dv) throws IOException {
        
        float smoothedMLEofTerm = 1;
        PerTermStat docPTS;
        docPTS = dv.docPerTermStat.get(t);
//        System.out.println("DOCPTS : " + docPTS.getCF());
        PerTermStat colPTS = feedbackTermStats.get(t);
//        System.out.println("COLPTS : " + colPTS.getCF());

        if (colPTS != null) 
            smoothedMLEofTerm = 
                ((docPTS!=null)?(mixingLambda * (float)docPTS.getCF() / (float)dv.getDocSize()):(0)) /
                ((feedbackTermStats.get(t)!=null)?((1.0f-mixingLambda)*(float)feedbackTermStats.get(t).getCF()/(float)vocSize):0);
     
        return (float)Math.log(1+smoothedMLEofTerm);

    } // ends return_Smoothed_MLE_Log()
    
    /**
     * RM1: IID Sampling <p>
     * Returns 'hashmap_PwGivenR' containing all terms of PR docs (PRD) with 
     * weights calculated using IID Sampling <p>
     * P(w|R) = \sum{d\in PRD} {smoothedMLE(w,d)*smoothedMLE(Q,d)}
     * Reference: Relevance Based Language Model - Victor Lavrenko (SIGIR-2001)
     * @param query The query
     * @param topDocs Initial retrieved document list
     * @return 'hashmap_PwGivenR' containing all terms of PR docs with weights
     * @throws Exception 
     */
    public HashMap RM1(TRECQuery query, TopDocs topDocs) throws Exception {

        float p_W_GivenR_one_doc;
        list_PwGivenR = new ArrayList<>();
        hashmap_PwGivenR = new LinkedHashMap<>();
        int expansionTermCount = 0;
        float normFactor = 0;
        
        /* Calculating for each w_i in R: P(w_i|R)~P(wi, q1 ... qk)
           P(wi, q1 ... qk) = \sum{d\in PRD} {P(w|D)*\prod_{i=1... k} {P(qi|D}} */

        for (Map.Entry<String, PerTermStat> entrySet : feedbackTermStats.entrySet()) {
            // for each t in R:
            String t = entrySet.getKey();
            p_W_GivenR_one_doc = 0;

            for (Map.Entry<Integer, DocumentVector> docEntrySet : feedbackDocumentVectors.entrySet()) {
            // for each doc in RF-set
                int luceneDocId = docEntrySet.getKey();
                p_W_GivenR_one_doc += return_Smoothed_MLE_Log(t, feedbackDocumentVectors.get(luceneDocId)) *
                    hash_P_Q_Given_D.get(luceneDocId);
            }
            list_PwGivenR.add(new WordProbabilityUEF(t, p_W_GivenR_one_doc));
        }
        
        /* sorting list in descending order
           T1 = Normalized RM1(D1) -- <sorted> */
        Collections.sort(list_PwGivenR, (WordProbabilityUEF t, WordProbabilityUEF t1) 
                -> t.p_w_given_R<t1.p_w_given_R?1:t.p_w_given_R==t1.p_w_given_R?0:-1); 
        // -- sorted list in descending order
        
        // T1'= normalized top n terms of t1 <with highest weights>
        for (WordProbabilityUEF singleTerm : list_PwGivenR) {
            if (null == hashmap_PwGivenR.get(singleTerm.w)) {
                hashmap_PwGivenR.put(singleTerm.w, new WordProbabilityUEF(singleTerm.w, singleTerm.p_w_given_R));
                expansionTermCount++;
                normFactor += singleTerm.p_w_given_R;
                if(expansionTermCount>=numFeedbackTerms)
                    break;
            }
            //* else: The t is already entered in the hash-map 
        } //hashmap_PwGivenR has all terms of PRDs along with their probabilities 

        /* selecting top numFeedbackTerms terms and normalize */
        // ++ Normalizing 
        for (Map.Entry<String, WordProbabilityUEF> entrySet : hashmap_PwGivenR.entrySet()) {
            WordProbabilityUEF wp = entrySet.getValue();
            wp.p_w_given_R /= normFactor;
            wp.expansionWeight = wp.p_w_given_R;
        }
        // -- Normalizing done
        
        //System.out.println("No of terms from RM1 : " + hashmap_PwGivenR.size());       
        return hashmap_PwGivenR; 
    }   // ends RM1()
    
    /**
     * Returns the expanded query in BooleanQuery form with P(w|R) as 
     * corresponding weights for the expanded terms
     * @param expandedQuery The expanded query
     * @param query The query
     * @return BooleanQuery to be used for consequent re-retrieval
     * @throws Exception 
     */
    public BooleanQuery getExpandedQuery(HashMap<String, WordProbabilityUEF> expandedQuery, TRECQuery query) throws Exception {

        BooleanQuery booleanQuery = new BooleanQuery();
        
        for (Map.Entry<String, WordProbabilityUEF> entrySet : expandedQuery.entrySet()) {
            String key = entrySet.getKey();
            if(key.contains(":"))
                continue;
            WordProbabilityUEF wProba = entrySet.getValue();
            float value = wProba.expansionWeight;
            Term t = new Term(fieldToSearch, key);
            Query tq = new TermQuery(t);
            tq.setBoost(value);
            BooleanQuery.setMaxClauseCount(4096);
            booleanQuery.add(tq, BooleanClause.Occur.SHOULD);
        }

        return booleanQuery;
    } // ends getExpandedQuery()
    
    public void makeUEFCluster() throws Exception {
        
        TopDocs topRetDocsLM, topRetDocsRLM;                    // top retrieved documents
        ScoreDoc[] hits;
        HashMap<Integer, Float> mapLM, mapRLM;
        HashMap<String, WordProbabilityUEF> hashmap_PwGivenR;
        int hits_lengthLM;
        float spearmanVal;
        
        for (TRECQuery query : queries) {
            
            
            
            mapLM = new HashMap<>();
            mapRLM = new HashMap<>();
            
            collector = TopScoreDocCollector.create(numHits);
            Query luceneQuery = trecQueryparser.getAnalyzedQuery(query);

//            System.out.println("\n" + query.qid +": Initial query: " + luceneQuery.toString(fieldToSearch));

            /* PRF - initial retrieval performed */
            indexSearcher.search(luceneQuery, collector);
            topRetDocsLM = collector.topDocs();
            hits = topRetDocsLM.scoreDocs;
            
//            System.out.println("docs retrieved : " + topRetDocsLM.totalHits);
            /* PRF */
            hits_lengthLM = hits.length;
            System.out.println("hits length : " + hits_lengthLM);
            
//            StringBuffer resBuffer;
//            resFileWriterLM = new FileWriter(resPathLM, true);
//            
//            /* res file in TREC format with doc text (6 columns) */
//            resBuffer = new StringBuffer();
//            
//            for (int i = 0; i < Math.min(numFeedbackDocs, hits_lengthLM); ++i) {
////                int docId = hits[i].doc;
////                Document d = indexSearcher.doc(docId);
////                resBuffer.append(query.qid).append("\tQ0\t").
////                append(d.get(FIELD_ID)).append("\t").
////                append((i)).append("\t").
////                append(hits[i].score).append("\t").
////                append("LMDirichlet-1000").append("\n");
//                
//                mapLM.put(i, hits[i].score);
//            }
////            resFileWriterLM.write(resBuffer.toString());
////            System.out.println("LM map size : " + mapLM);
//            
//            // RM1
//            setFeedbackStats(topRetDocsLM, luceneQuery.toString(fieldToSearch).split(" "));
//            hashmap_PwGivenR = RM1(query, topRetDocsLM);
//            
//            BooleanQuery booleanQuery;
//
//            booleanQuery = getExpandedQuery(hashmap_PwGivenR, query);
////            System.out.println("\nRe-retrieval after RM1 :");
////            System.out.println(booleanQuery.toString(fieldToSearch));
//            collector = TopScoreDocCollector.create(numHits);
//            indexSearcher.search(booleanQuery, collector);      //retrieve with EQ1
//            
//            /* D2 = top k docs of search (EQ1,C) */
//            topRetDocsRLM = collector.topDocs(); 
//            hits = topRetDocsRLM.scoreDocs;
//                if(hits == null)
//                System.out.println("Nothing found");
//
//            resFileWriterRLM = new FileWriter(resPathRLM, true);
//            
//            /* res file in TREC format with doc text (6 columns) */
//            resBuffer = new StringBuffer();
//            
//            for (int i = 0; i < Math.min(numFeedbackDocs, hits_lengthLM); ++i) {
////                int docId = hits[i].doc;
////                Document d = indexSearcher.doc(docId);
////                resBuffer.append(query.qid).append("\tQ0\t").
////                append(d.get(FIELD_ID)).append("\t").
////                append((i)).append("\t").
////                append(hits[i].score).append("\t").
////                append("RLM").append("\n");
//                
//                mapRLM.put(i, hits[i].score);
//            }
////            resFileWriterRLM.write(resBuffer.toString());
////            System.out.println("RLM map size : " + mapRLM);
//            
////            resFileWriterLM.close();
////            resFileWriterRLM.close();
//            spearmanVal = spc.spearman(mapLM, mapRLM);
////            System.out.println("Spearman : " + spearmanVal);
//            
//            System.out.println(query.qid + "\t" + spearmanVal * wrigVal.get(query.qid));
        }
    }

    public static void main(String[] args) throws IOException, Exception {

        String usage = "java UEF <properties-file>\n"
                + "Properties file must contain the following fields:\n"
                + "1. stopFilePath: path of the stopword file\n"
                + "2. indexPath: Path of the index\n"
                + "3. queryPath: path of the query file (in proper xml format)\n"
                + "4. resPath: path of the directory to store res file\n"
                + "5. numFeedbackDocs: number of feedback documents to use\n"
                + "6. numFeedbackTerms: number of feedback terms to use"
                + "7. rm3.queryMix (0.0-1.0): query mix to weight between P(w|R) and P(w|Q)\n"
                + "8. similarityFunction: 0.DefaultSimilarity, 1.BM25Similarity, 2.LMJelinekMercerSimilarity, 3.LMDirichletSimilarity\n"
                + "9. WRIG(LR) values";               
                
        Properties prop = new Properties();

        if(1 != args.length) {
            System.out.println("Usage: " + usage);
            args = new String[1];
            args[0] = "uef.properties";
            System.exit(1);
        }
        prop.load(new FileReader(args[0]));
        UEFMultiCluster uef = new UEFMultiCluster(prop);

        uef.makeUEFCluster();
    } // ends main()
}
