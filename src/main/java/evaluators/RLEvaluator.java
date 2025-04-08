package evaluators;
import java.util.ArrayList;
import java.io.*;
import java.security.spec.EncodedKeySpec;

import evolver.GRNGenome;
import grn.GRNModel;


public class RLEvaluator extends GRNGenomeEvaluator {
	
	public static int numEvaluations=0;
	// public static EnvEvaluator env_evaluator = new env_evaluator.RLEvaluator();
	public static EnvEvaluator env_evaluator;
	public RLEvaluator() {
		numGRNInputs=4;
		numGRNOutputs=3;
		name="RLEvaluator";
	}

	@Override
	public double evaluate(GRNGenome aGenome) {

		double fitness=0.0;
		GRNModel grn = buildGRNFromGenome(aGenome);
		// System.err.println("fitness="+fitness+"  =>  "+grn.toString() + "|" + grn.beta + "|" + grn.delta );

        fitness = env_evaluator.eval_episodes(grn, numGRNInputs, numGRNOutputs);
		// System.err.println("fitness="+fitness+"  =>  "+grn.toString());
		aGenome.setNewFitness(fitness);
		numEvaluations++;
		return fitness;
	}


	public static void main(String[] args) {

		String path = "CoverageControl/run_24692440971211/grn_668_-2.393888979239919.grn";


		try {
			GRNModel grn_test = GRNModel.loadFromFile(path);
			GRNSinusEvaluator evaluator = new GRNSinusEvaluator();
			double fitness = evaluator.evaluate(grn_test);
			System.out.println("fitness " + fitness);
		}
		catch (Exception e) {
			e.printStackTrace();
		}

	}

}

