/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

/**
 *
 * @author Debasis
 */
public class KMedoidsClusterer extends KMeansClusterer {

    public KMedoidsClusterer(String propFile) throws Exception {
        super(propFile);
    }
    
    TermVector getClosestPointToCentroid(TermVector centroidVec, int centroidId) throws Exception {
        float maxSim = 0, sim;
        int medoidDocId = 0;
        
        for (int i=0; i < numDocs; i++) {
            if (i == centroidId)
                continue;

            int clusterId = getClusterId(i);
            if (clusterId != centroidId)
                continue;

            TermVector docVec = TermVector.extractAllDocTerms(reader, i, contentFieldName, lambda);
            sim = docVec.cosineSim(centroidVec);
            if (sim > maxSim) {
                maxSim = sim;
                medoidDocId = i;
            }
        }
        return TermVector.extractAllDocTerms(reader, medoidDocId, contentFieldName, lambda);
    }
        
    // Work with medoids... A medoid in this case is that point which is
    // closest to the centroid...
    @Override
    void recomputeCentroids() throws Exception {        
        int k = 0;
        for (int centroidId : centroidDocIds.keySet()) {
            TermVector centroidVec = computeCentroid(centroidId);            
            centroidVecs[k++] = getClosestPointToCentroid(centroidVec, centroidId);            
        }
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java KMedoidsClusterer <prop-file>");
            args[0] = "init.properties";
        }
        
        try {
            LuceneClusterer fkmc = new KMedoidsClusterer(args[0]);
            fkmc.cluster();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
