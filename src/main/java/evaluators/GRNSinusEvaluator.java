package evaluators;

import java.util.ArrayList;
import java.io.*;
import evolver.GRNGenome;
import grn.GRNModel;

public class GRNSinusEvaluator extends GRNGenomeEvaluator {
	
	public static int numEvaluations=0;
	
	public GRNSinusEvaluator() {
		numGRNInputs=1;
		numGRNOutputs=1;
		name="SinusExperience";
	}

	@Override
	public double evaluate(GRNGenome aGenome) {

		double fitness=0.0;
		GRNModel grn = buildGRNFromGenome(aGenome);
		System.err.println("fitness="+fitness+"  =>  "+grn.toString() + "|" + grn.beta + "|" + grn.delta );
		grn.reset();
		// grn.proteins.get(0).concentration=0.0;
		
		grn.evolve(25);
		

		for (int nStep=0; nStep<100; nStep++) {
			grn.proteins.get(0).concentration=(double)nStep/100.0;
			// grn.proteins.get(1).concentration=0.125*(Math.sin((double)nStep)+1.0)/2.0;
			grn.evolve(1);
			fitness+=Math.abs(Math.sin(0.5*(double)nStep)-(grn.proteins.get(1).concentration*2.0-1.0));
		}
		
		System.err.println("fitness="+fitness+"  =>  "+grn.toString());
		aGenome.setNewFitness(-fitness);

		numEvaluations++;
		return -fitness;
	}

	public double evaluate(GRNModel grn) {

		double fitness=0.0;
		System.err.println("fitness="+fitness+"  =>  "+grn.toString() + "|" + grn.beta + "|" + grn.delta );
		grn.reset();
		// grn.proteins.get(0).concentration=0.0;
		
		grn.updateSignatures();
		grn.evolve(25);

		System.err.println("GRN after warmup="+grn.toString());
		ArrayList<Float> x = new ArrayList<Float>();
		ArrayList<Double> y_pred = new ArrayList<Double>();
		ArrayList<Double> y_true = new ArrayList<Double>();

		for (int nStep=0; nStep<100; nStep++) {
			x.add(nStep/100.0f);
			grn.proteins.get(0).concentration=(double)nStep/100.0;
			// grn.proteins.get(1).concentration=0.125*(Math.sin((double)nStep)+1.0)/2.0;
			grn.evolve(1);
			fitness+=Math.abs(Math.sin(0.05*(double)nStep)-(grn.proteins.get(1).concentration*2.0-1.0));
			y_pred.add(grn.proteins.get(1).concentration*2.0-1.0);
			y_true.add(Math.sin(0.05*(double)nStep));
		}
		
		try {
			PrintWriter out = new PrintWriter(new File("res.txt"));
			for (int i=0; i<x.size(); i++) {
				out.println(x.get(i)+" "+y_pred.get(i)+" "+y_true.get(i));
			}
			out.close();
		} catch (FileNotFoundException e) {
			System.err.println("Error: Unable to create file 'res.txt': " + e.getMessage());
		}
		
		System.err.println("fitness="+fitness+"  =>  "+grn.toString());

		numEvaluations++;
		return -fitness;
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
