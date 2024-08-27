package evaluators;

import evolver.GRNGenome;
import grn.GRNModel;

// websockets client imports 
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.util.concurrent.CountDownLatch;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import com.fasterxml.jackson.databind.ObjectMapper;


public class PongEvaluator extends GRNGenomeEvaluator {

    public static int numEvaluations = 0;
    private WebSocketClient cc;
    private int numMaxEp = 5000;
    HashMap<String, String> resetCmd = new HashMap<>();
    HashMap<String, String> endCmd = new HashMap<>();
    HashMap<String, String> stepCmd = new HashMap<>();
    private static CountDownLatch latch = new CountDownLatch(1);
    private long seed = 1854784;
    private String reply;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PongEvaluator() {
        numGRNInputs = 4;  // Obs = {px, py, bx, by} 
        numGRNOutputs = 3; // A = {Right, Left, Noop}
        name = "PongEvaluator";
        

        // construct default command 
        resetCmd.put("cmd", "reset");
        resetCmd.put("args", Long.toString(seed));


        endCmd.put("cmd", "end");
        endCmd.put("args", "0");

        stepCmd.put("cmd", "step");
        stepCmd.put("args", "0");

        System.out.println("reset cmd pas se,d: " + resetCmd);

        try{
            // create connection 
            cc = new WebSocketClient(new URI("ws://localhost:8000")) 
            {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("Connected to server");
                    send("START");
                }

                @Override
                public void onMessage(String message) {
                    reply = message;
                    // System.out.println("Received message: " + reply);
                    latch.countDown();
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("Connection closed: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                }
            };

        } catch (URISyntaxException e) {
            System.err.println("Invalid URI syntax: " + e.getMessage());
        }
    }

    @Override
    public double evaluate(GRNGenome aGenome) {
        // TODO run the evaluation 
        numEvaluations++;
        return 100.666;
    }

    public double run_episodes() {
        
        double reward_ep = 0;
        float reward_ts = 0 ;
        ArrayList<?> obs;
        boolean truncated;
        boolean terminated;

        cc.connect();

        // wait connection has been etablished
        try {
            latch.await(); // Blocks until latch.countDown() is called
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Random random = new Random(seed);

        System.err.println("------------------- run episodes start -------------------");
        // reset the environment
        try {
            String json = objectMapper.writeValueAsString(resetCmd);
            System.out.println("reset cmd send : " + resetCmd);
            cc.send(json);
        }catch (IOException e) {
            
            e.printStackTrace();
        }

        // Wait for the response
        try {
            latch.await(); // Blocks until latch.countDown() is called
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("reply after reset : " + reply);
        // read obs after reset 
        try {

            HashMap<String, Object> map = objectMapper.readValue(reply, HashMap.class);
            
            reward_ts  = ((Number) map.get("reward")).floatValue();
            obs = (ArrayList<?>) map.get("obs");
            truncated = (Boolean) map.get("truncated");
            terminated = (Boolean) map.get("terminated");
        } catch (Exception e) {
            System.err.println("error read reply");
            e.printStackTrace();
        }

        for (int i = 0; i < this.numMaxEp; i++)
        {
            int action = random.nextInt(3);

            stepCmd.put("args", Integer.toString(action));


            try {
                cc.send(objectMapper.writeValueAsString(stepCmd));
            }catch (IOException e) {
                e.printStackTrace();
            }

            // Wait for the response
            try {
                latch.await(); // Blocks until latch.countDown() is called
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // read reply
            System.out.println("step reply" + reply);    
            try {

                HashMap<String, Object> map = objectMapper.readValue(reply, HashMap.class);
                
                reward_ts  = ((Number) map.get("reward")).floatValue();
                obs = (ArrayList<?>) map.get("obs");
                truncated = (Boolean) map.get("truncated");
                terminated = (Boolean) map.get("terminated");

                if (truncated || terminated)
                {
                    break;
                }
            } catch (Exception e) {
                System.err.println("error read reply");
                e.printStackTrace();
            }

            reward_ep += reward_ts;
            // TODO succeed to have -34 should no thappend check 

        }
        cc.close();
        return reward_ep;
    }

    public static void main(String[] args) {

        
        PongEvaluator evaluator = new PongEvaluator();
        // evaluator.cc.connect();


        // GRNGenome aGenome = new GRNGenome();
        // System.err.println("\n\n\nTest ! \n\n\n");      

        // System.out.println("evaluation : "+ evaluator.evaluate(aGenome));        
        double r = evaluator.run_episodes();
        System.out.println("reward gathered : "+ r);
    
    }
}