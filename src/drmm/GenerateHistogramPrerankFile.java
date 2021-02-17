/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package drmm;

import static drmm.Getter.readPrerankFile;
import static drmm.TrecDocIndexer.FIELD_ID;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;


/**
 *
 * @author suchana
 */

public class GenerateHistogramPrerankFile {
    
    WordVecs wvs;
    Properties prop;
    TrecDocIndexer indexer;
    IndexSearcher searcher;
    List<QueryObject> queries;
    GetStats stats;
    String fieldName;
    List<PrerankRelevance> prerankData;  // to contain the judged documents for each query

    public GenerateHistogramPrerankFile (String propFile) throws Exception {
        
        prop = new Properties();
        prop.load(new FileReader(propFile));
        
        indexer = new TrecDocIndexer(propFile);

        File indexDir = indexer.getIndexDir();
 
        searcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(indexDir.toPath())));

        queries = constructQueries();
        for (QueryObject qo : queries) {
            System.out.println(qo.id + " " + qo.title);
        }
        
        stats = new GetStats(propFile);
        fieldName = prop.getProperty("fieldName", "content");

        prerankData = readPrerankFile(prop.getProperty("preranks.file"));
        System.out.println("SIze of prerank : " + prerankData.size());
        System.out.println("Vector loading started...");
        wvs = new WordVecs(prop);
        wvs.loadFromTextFile();
        System.out.println("embedding size : " + wvs.wordvecmap.size());
        System.out.println("Done.");
    }

    public List<QueryObject> constructQueries() throws Exception {

        List<QueryObject> queries = new ArrayList<>();
        String queryFile = prop.getProperty("query.file");
        TRECQueryParser parser = new TRECQueryParser(queryFile, indexer.getAnalyzer());
        queries = parser.makeQuery();
        return queries;
    }

    /**
     * Returns a set with all the query terms
     * @return 
     */
    public Set getAllQueryTerms() {
        System.out.println("Getting all query terms...");
        Set<String> queryTerms = new HashSet<>();
        for (QueryObject query : queries) {
            List qterms = query.getQueryTerms(fieldName);
            queryTerms.addAll(qterms);
        }

        System.out.println("Done.");
        return queryTerms;
    }

    /**
     * Make the bin for one query-document pair
     * @param binSize
     * @param qterms
     * @param docTerms 
     * @return  
     */
    public float[] makeBin(int binSize, List<String> qterms, List<String> docTerms) {

        float [] oneDoc = new float[0];
        WordVec qtv, dtv; // query and document word vector
        float cossim;

        // for each query term
        for (String qterm : qterms) {
//            System.out.println("QTERM : " + qterm);
            float oneQterm[] = new float[binSize];
//            System.out.println("oneqterm size : " + oneQterm.length);
            qtv = wvs.getVec(qterm);
//            System.out.println("");
            if(qtv != null) {
                System.out.println("QTV : " + qtv.word + " :::: " + qtv.vec.length);
                // for each document term
                for (String docTerm : docTerms) {
//                    System.out.println("docterm : " + docTerm);
                    dtv = wvs.getVec(docTerm);
                    if(dtv != null) {
                        // compute the cosine similarity between the query term and document term
                        System.out.println("DTV : " + dtv.word + " :::: " + dtv.vec.length);
                        cossim = qtv.cosineSim(dtv);
//                        System.out.println("Cosim : " + cossim);
                        int vid = (int) ((cossim+1.0) / 2 * (binSize-1)); 
//                        System.out.println("vid : " + vid);
                        oneQterm[vid] += 1;
//                        System.out.println("oneqterm : " + oneQterm[vid]);
                    }
                }
                oneDoc = ArrayUtils.addAll(oneDoc, oneQterm);
            }
        }
        return oneDoc;
    }
    
    public List<String> getDocumentVector(int luceneDocId, String fieldName, IndexReader indexReader) throws IOException {

        int docSize = 0;

        if(indexReader==null) {
            System.out.println("Error: null == indexReader in showDocumentVector(int,IndexReader)");
            System.exit(1);
        }

        // t vector for this document and field, or null if t vectors were not indexed
        Terms terms = indexReader.getTermVector(luceneDocId, fieldName);
        if(null == terms) {
            System.err.println("Error getDocumentVector(): Term vectors not indexed: "+luceneDocId);
            return null;
        }

        TermsEnum iterator = terms.iterator();
        BytesRef byteRef = null;

        List<String> all_terms = new ArrayList<>();
        //* for each word in the document
        while((byteRef = iterator.next()) != null) {
            String term = new String(byteRef.bytes, byteRef.offset, byteRef.length);
            all_terms.add(term);
            int docFreq = iterator.docFreq();            // df of 't'
            long termFreq = iterator.totalTermFreq();    // tf of 't'
            //System.out.println(t+": tf: "+termFreq);
            docSize += termFreq;
            //* termFreq = cf, in a document; df = 1, in a document
        }

        return all_terms;
    }
    
    public void makeHistogramPrerankFile() throws IOException, Exception {
        
	PrintWriter writer = new PrintWriter(prop.getProperty("histogramFile", "histogram.prerank"));

        Set<String> allQueryTerms = getAllQueryTerms();
        System.out.println("query terms : " + allQueryTerms);
        HashMap<String, Double> termIdf;
        ScoreDoc[] hits;
        List<String> docTerms;

        // get idfs of all query terms
        termIdf = stats.getAllIDF(fieldName, allQueryTerms);
        
        // for each judged rel docs for that query:
        for (PrerankRelevance oneDoc : prerankData) {
            
            for (QueryObject query : queries) {
                
                if(query.id.equalsIgnoreCase(oneDoc.queryid)) {
                    System.out.println(query.id + " " + query.title);
                    List<String> qterms = query.getQueryTerms(fieldName);
                    
                    System.out.println("doc no. : " + oneDoc.docid + " :;:: " + oneDoc.docscore);                    
                    hits = Getter.getLuceneDocid(oneDoc.docid, searcher, FIELD_ID);
                    if (hits.length > 0) {
                        int luceneDocid = hits[0].doc;
                        System.out.println("lucendocid : " + luceneDocid);
                        docTerms = getDocumentVector(luceneDocid, "content", searcher.getIndexReader());
                        System.out.println("Doc terms : " + docTerms);
                        writer.print(oneDoc.queryid + " " + oneDoc.docid + " " + oneDoc.docscore + " " + qterms.size() + " ");
                        for (String qterm : qterms) {
                            Double idf = termIdf.get(qterm);
                            System.out.println("qterm : " + qterm + "\tIDF : " + idf);
                            if (idf == null) 
                                idf = 0.0;
                            writer.write(idf.toString() + " ");
                        }
                        float [] onedoc = makeBin(30, qterms, docTerms);
                        StringBuilder builder = new StringBuilder();
                        for (float f: onedoc) {
                            builder.append(f==0?0.0:Math.log10(f)).append(" ");
                        }
                        writer.write(builder.toString());
                        writer.write("\n"); 
                    }                    
                    else {
                        System.out.println("======= Match kore ni =======");
                    }
                }
            }           
        }
        writer.close();
    }

    public static void main(String[] args) {

        if (args.length < 1) {
            args = new String[1];
            args[0] = "drmm_hist.properties";
        }
        
        try {
            GenerateHistogramPrerankFile calHist = new GenerateHistogramPrerankFile(args[0]);
            
            // generate histogram from prerank file
            calHist.makeHistogramPrerankFile();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }    
}
