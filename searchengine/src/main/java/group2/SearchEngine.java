package group2;

import java.io.IOException;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.ByteBuffersDirectory;

public class SearchEngine {
    public static void main(String[] args) throws IOException, ParseException {
        if (args.length < 2) {
            System.out.println("Usage: java SearchEngine <search_type> <search_query>");
            return;
        }

        // Extracting command-line arguments
        String searchType = args[0]; // e.g., "business", "review", "geospatial"
        String queryStr = args[1]; // e.g., the search keyword or bounding box for geospatial search

        // 0. Specify the analyzer for tokenizing text.
        //    The same analyzer should be used for indexing and searching
        StandardAnalyzer analyzer = new StandardAnalyzer();

        // 1. create the index
        Directory index = new ByteBuffersDirectory();

        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        IndexWriter w = new IndexWriter(index, config);

        // Adding sample business documents
        addDoc(w, "Joe's Diner", "Great place for breakfast!", "40.730610", "-73.935242"); // New York
        addDoc(w, "Sushi World", "Best sushi in town!", "34.052235", "-118.243683"); // Los Angeles
        addDoc(w, "Pizza Palace", "Delicious pizza and more.", "41.878113", "-87.629799"); // Chicago
        addDoc(w, "Burger Shack", "Amazing burgers and fries.", "29.760427", "-95.369804"); // Houston
        addDoc(w, "Burger King", "Amazing burgers and fries.", "29.760427", "-95.369804"); // Houston
        addDoc(w, "Burger Zoe", "Amazing burgers and fries.", "29.760427", "-95.369804"); // Houston

        w.close();

        // 2. query
        // construct query
        Query query = null;
        switch (searchType.toLowerCase()) {
            case "business":
                // Keyword search in business name
                query = new QueryParser("business", analyzer).parse(queryStr);
                break;
            case "review":
                // Keyword search in reviews
                query = new QueryParser("review", analyzer).parse(queryStr);
                break;
            case "geospatial":
                // Geospatial search by bounding box (latitude and longitude)
                String[] boundingBox = queryStr.split(",");
                if (boundingBox.length != 4) {
                    System.out.println("Geospatial search requires 4 coordinates: minLat,minLon,maxLat,maxLon");
                    return;
                }
                double minLat = Double.parseDouble(boundingBox[0]);
                double minLon = Double.parseDouble(boundingBox[1]);
                double maxLat = Double.parseDouble(boundingBox[2]);
                double maxLon = Double.parseDouble(boundingBox[3]);

                // Custom query logic for geospatial search
                query = createGeospatialQuery(minLat, minLon, maxLat, maxLon);
                break;
            default:
                System.out.println("Unknown search type: " + searchType);
                return;
        }

        // 3. search
        int hitsPerPage = 10;
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs docs = searcher.search(query, hitsPerPage);
        ScoreDoc[] hits = docs.scoreDocs;

        // 4. display results
        System.out.println("Found " + hits.length + " hits.");
        
        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);

            switch (searchType.toLowerCase()) {
                case "business":
                    // Display business name only for business search
                    System.out.println((i + 1) + ". Business: " + d.get("business"));
                    break;
                case "review":
                    // Display business name and review for review search
                    System.out.println((i + 1) + ". Review: " + d.get("review"));
                    break;
                case "geospatial":
                    // Display business name and location (latitude and longitude) for geospatial search
                    System.out.println((i + 1) + ". Business: " + d.get("business"));
                    break;
                default:
                    break;
            }
        }

        // reader can only be closed when there
        // is no need to access the documents any more.
        reader.close();
    }

    private static void addDoc(IndexWriter w, String business, String review, String latitude, String longitude) throws IOException {
        Document doc = new Document();
        doc.add(new TextField("business", business, Field.Store.YES));
        doc.add(new TextField("review", review, Field.Store.YES));
        doc.add(new DoublePoint("latitude", Double.parseDouble(latitude)));
        doc.add(new DoublePoint("longitude", Double.parseDouble(longitude)));
        w.addDocument(doc);
    }

    // Replace NumericRangeQuery with DoublePoint
    private static Query createGeospatialQuery(double minLat, double minLon, double maxLat, double maxLon) {
        // Create range queries for latitude and longitude
        Query latQuery = DoublePoint.newRangeQuery("latitude", minLat, maxLat);
        Query lonQuery = DoublePoint.newRangeQuery("longitude", minLon, maxLon);

        System.out.println("minLat " + minLat);
        System.out.println("maxLat " + maxLat);
        System.out.println("minLon " + minLon);
        System.out.println("maxLon " + maxLon);

        // Combine latitude and longitude queries with a boolean query
        BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
        booleanQuery.add(latQuery, BooleanClause.Occur.MUST);
        booleanQuery.add(lonQuery, BooleanClause.Occur.MUST);

        return booleanQuery.build();
    }
}