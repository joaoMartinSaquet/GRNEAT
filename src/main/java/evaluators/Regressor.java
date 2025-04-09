package evaluators;

import evolver.GRNGenome;
import grn.GRNModel;

// webscoket imports
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.fasterxml.jackson.databind.ObjectMapper;


public class Regressor extends GRNGenomeEvaluator {

    public static int numEvaluations = 0;
    private WebSocketClient cc;
    HashMap<String, String> resetCmd = new HashMap<>();
    HashMap<String, String> endCmd = new HashMap<>();
    HashMap<String, String> stepCmd = new HashMap<>();
    private static Semaphore response = new Semaphore(0);
    private String reply;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private boolean debug = false;

    public Regressor() {


        // // PONG in & out
        // numGRNInputs = 4;  // Obs = {px, py, bx, by} 
        // numGRNOutputs = 3; // A = {Right, Left, Noop}
        // name = "PongEvaluator";
        
        // mountain car in & out
        numGRNInputs = 2;  // Obs = {px, py, bx, by} 
        numGRNOutputs = 2; // A = {Right, Left, Noop}
        name = "Regressor";
        // System.out.println("reset cmd pas send: " + resetCmd);
    }

    @SuppressWarnings("unchecked")
    @Override
    public double evaluate(GRNGenome aGenome) {

        double fitness=0.0;
        ArrayList<List<Double>> grn_inputs = new ArrayList<>();
        ArrayList<Double[]> grn_output = new ArrayList<>();
        

        GRNModel grnIndividual = buildGRNFromGenome(aGenome);
        grnIndividual.reset();
        grnIndividual.evolve(25);

        // websocket construction 
        try{
            // create connection 
            cc = new WebSocketClient(new URI("ws://localhost:8000"))
            {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    // System.out.println("Connected to server");
                    send("START");
                }

                @Override
                public void onMessage(String message) {
                    reply = message;
                    if (debug) {System.out.println("Received message: " + reply);}
                    response.release();
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (debug) {System.out.println("Connection closed: " + reason);}
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                }
            };

        } catch (URISyntaxException e) {
            System.err.println("Invalid URI syntax: " + e.getMessage());
        }

        cc.connect();
        
        // if connection is established get the inputs inside reply
        try {
            response.acquire(); // Blocks until response is released
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {

            HashMap<String, Object> map = objectMapper.readValue(reply, HashMap.class);
            grn_inputs = (ArrayList<List<Double>>) map.get("inputs");
        } catch (Exception e) {
            if (debug) {System.out.println("reply reset error " + reply);}
            if (debug) {System.err.println("error read reply");}
            e.printStackTrace();
        }
        

        // process the grns input vector  to get y_grn 
        for (int i = 0; i < grn_inputs.size();  i++)

            // set inputs
            for (int k = 0; k < this.numGRNInputs; k++)
            {   
                if (this.debug) 
                {
                    System.out.println("k " + k);
                
                }
                Double input_ = grn_inputs.get(i).get(k);

                grnIndividual.proteins.get(k).concentration = (double) (input_);
            }

            // propagate the input
            grnIndividual.evolve(1); 
            
            // get the output 
            Double[] out = new Double[this.numGRNOutputs];
            for (int k=0; k < this.numGRNOutputs; k++)
            {
                out[k] = grnIndividual.proteins.get((this.numGRNInputs-1)+k).concentration;
            }
            grn_output.add(out);
        

        // send the GRN output to pyhon to process the error (do it in java ?na )
        HashMap<String, ArrayList<Double[]>> y_grn =  new HashMap<>();
        y_grn.put("y_grn", grn_output);
        
        try {
            String json = objectMapper.writeValueAsString(y_grn);
            if (debug) {System.out.println("y send size : " + json);}
            cc.send(json);
        }catch (IOException e) {
            
            e.printStackTrace();
        }

        // get fitness 
        try {
            response.acquire(); // Blocks until response is released
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        
        try {

            HashMap<String, Object> map = objectMapper.readValue(reply, HashMap.class);
            fitness = (Double) map.get("fitness");
        } catch (Exception e) {
            if (debug) {System.err.println("error read reply");}
            e.printStackTrace();
        }

        cc.close();

        return fitness;
    }

    public static void main(String[] args) {

        
        Regressor evaluator = new Regressor();
        
        System.out.println("hello world");
    
    }
}
