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
	boolean pseudoCentroid;

    public KMedoidsClusterer(String propFile) throws Exception {
        super(propFile);

		// allowable value: pseudo and true
		pseudoCentroid = prop.getProperty("kmedoids.mode", "pseudo").equals("pseudo");
    }
    
    TermVector getClosestPointToCentroid(TermVector centroidVec, int centroidId) throws Exception {
        float maxSim = 0, sim;
        int medoidDocId = 0;
        
        for (int i=0; i < numDocs; i++) {

            int clusterId = getClusterId(i);
            if (clusterId != centroidId)
                continue;

            TermVector docVec = TermVector.extractAllDocTerms(reader, i, contentFieldName, lambda);
			if (docVec == null) {
        		System.out.println("Skipping cluster assignment for empty doc: " + i);
				continue;
        	}

            sim = docVec.cosineSim(centroidVec);
            if (sim > maxSim) {
                maxSim = sim;
                medoidDocId = i;
            }
        }
        return TermVector.extractAllDocTerms(reader, medoidDocId, contentFieldName, lambda);
    }

    TermVector getMedoid(int centroidId) throws Exception {
        float maxSim = 0, sim;
        int medoidDocId = 0;
        
        for (int i=0; i < numDocs; i++) {
			sim = 0;
			if (getClusterId(i) != centroidId)
				continue;

        	for (int j=i+1; j < numDocs; j++) {
				if (getClusterId(j) != centroidId)
					continue;

				// we are here only if cluster-id(i) == cluster-id(j) == centroidId
        	    TermVector d_i = TermVector.extractAllDocTerms(reader, i, contentFieldName, lambda);
				if (d_i == null) {
        			System.out.println("Skipping cluster assignment for empty doc: " + i);
					continue;
    	    	}

        	    TermVector d_j = TermVector.extractAllDocTerms(reader, j, contentFieldName, lambda);
				if (d_j == null) {
        			System.out.println("Skipping cluster assignment for empty doc: " + j);
					continue;
				}

        	    sim += d_i.cosineSim(d_j);
			}

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
			if (pseudoCentroid) {
	            TermVector centroidVec = computeCentroid(centroidId);            
    	        centroidVecs[k++] = getClosestPointToCentroid(centroidVec, centroidId);
			}
			else {
				// true medoid
    	        centroidVecs[k++] = getMedoid(centroidId);
			}
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
            
            boolean eval = Boolean.parseBoolean(fkmc.getProperties().getProperty("eval", "false"));
            if (eval) {
                ClusterEvaluator ceval = new ClusterEvaluator(args[0]);
                System.out.println("Purity: " + ceval.computePurity());
                System.out.println("NMI: " + ceval.computeNMI());            
                System.out.println("RI: " + ceval.computeRandIndex());            
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
