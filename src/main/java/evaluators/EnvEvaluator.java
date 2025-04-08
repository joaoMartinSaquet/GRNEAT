package evaluators;

import java.util.ArrayList;
import java.io.*;
import evolver.GRNGenome;
import grn.GRNModel;

public interface EnvEvaluator {
    public float eval_episodes(GRNModel grn, int n_input, int n_output);
}