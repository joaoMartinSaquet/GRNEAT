package evaluators;

import evolver.GRNGenome;
import grn.GRNModel;

// websockets client imports 
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
import java.util.Map;
import java.util.Random;
import com.fasterxml.jackson.databind.ObjectMapper;


public class PongEvaluator extends GRNGenomeEvaluator {

    public static int numEvaluations = 0;
    private WebSocketClient cc;
    private int numMaxEp = 500;
    HashMap<String, String> resetCmd = new HashMap<>();
    HashMap<String, String> endCmd = new HashMap<>();
    HashMap<String, String> stepCmd = new HashMap<>();
    private static Semaphore response = new Semaphore(0);
    private long seed = 1854784;
    private String reply;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private boolean debug = false;

    public PongEvaluator() {


        // // PONG in & out
        // numGRNInputs = 4;  // Obs = {px, py, bx, by} 
        // numGRNOutputs = 3; // A = {Right, Left, Noop}
        // name = "PongEvaluator";
        
        // mountain car in & out
        numGRNInputs = 2;  // Obs = {px, py, bx, by} 
        numGRNOutputs = 1; // A = {Right, Left, Noop}
        name = "MountainCar";

        // construct default command 
        resetCmd.put("cmd", "reset");
        resetCmd.put("args", Long.toString(seed));


        endCmd.put("cmd", "end");
        endCmd.put("args", "0");

        stepCmd.put("cmd", "step");
        stepCmd.put("args", "0");

        // System.out.println("reset cmd pas send: " + resetCmd);
    }

    @SuppressWarnings("unchecked")
    @Override
    public double evaluate(GRNGenome aGenome) {
        double rewardEp = 0;
        float rewardTs = 0 ;
        ArrayList<Double> obs = new ArrayList<>();
        boolean truncated;
        boolean terminated;

        
        // GRN controller constructor
        GRNModel grnController = buildGRNFromGenome(aGenome);
        int proteinNumber = grnController.proteins.size();
        grnController.reset();
        // evolve ? 
        grnController.evolve(25);

        // websocket clioent construction 
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
                    // System.out.println("Connection closed: " + reason);
                    
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
        
        // wait connection has been etablished
        try {
            response.acquire(); // Blocks until response is released
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Random random = new Random(seed);

        // System.err.println("------------------- run episodes start -------------------");
        // reset the environment
        try {
            String json = objectMapper.writeValueAsString(resetCmd);
            if (debug) {System.out.println("reset cmd send : " + resetCmd);}
            cc.send(json);
        }catch (IOException e) {
            
            e.printStackTrace();
        }

        // Wait for the response
        try {
            if (debug) {System.out.println("waiting response reset ");}
            response.acquire(); // Blocks until latch.countDown() is called
           
        } catch (InterruptedException e) {
            if (debug) {System.out.println("waiting response reset error ");}
            e.printStackTrace();
        }

        if (debug) {System.out.println("reply after reset : " + reply);}
        // read obs after reset 
        try {

            HashMap<String, Object> map = objectMapper.readValue(reply, HashMap.class);
            
            rewardTs  = ((Number) map.get("reward")).floatValue();
            obs = (ArrayList<Double>) map.get("obs"); 
            truncated = (Boolean) map.get("truncated");
            terminated = (Boolean) map.get("terminated");
        } catch (Exception e) {
            if (debug) {System.out.println("reply reset error " + reply);}
            if (debug) {System.err.println("error read reply");}
            e.printStackTrace();
        }

        for (int i = 0; i < this.numMaxEp; i++)
        {   

            // action choose
            // random
            // int action = random.nextInt(3);
            
            // e-greedy
            
            for (int input = 0; input < numGRNInputs;  input++)
                grnController.proteins.get(input).concentration = (double) (obs.get(input));

            grnController.evolve(1); // forward pass is 1 step good ?

            // // e-greedy cases
            // int action = 0;
            // for (int output = 1; output<numGRNOutputs ;output++)
            // {
            //     action = (grnController.proteins.get(proteinNumber-numGRNOutputs-1 + output).concentration > grnController.proteins.get(proteinNumber-numGRNOutputs-1 + action).concentration) ? output : action;
            //     if (debug) {System.out.println("action taken" + action); }

            // }

            // stepCmd.put("args", Integer.toString(action));

            double action = grnController.proteins.get(proteinNumber-numGRNOutputs-1).concentration;
            stepCmd.put("args", Double.toString(action));


            try {
                cc.send(objectMapper.writeValueAsString(stepCmd));
            }catch (IOException e) {
                e.printStackTrace();
            }

            // Wait for the response
            try {
                response.acquire(); // Blocks until latch.countDown() is called
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // read reply
            // System.out.println("reward_ts : " + rewardTs  + "rewards_ep : " +  rewardEp );    
            try {

                HashMap<String, Object> map = objectMapper.readValue(reply, HashMap.class);
                
                rewardTs  = ((Number) map.get("reward")).floatValue();
                obs = (ArrayList<Double>) map.get("obs");
                truncated = (Boolean) map.get("truncated");
                terminated = (Boolean) map.get("terminated");

                if (truncated || terminated)
                {
                    break;
                }
            } catch (Exception e) {
                System.out.println("reply step error " + reply);
                System.err.println("error read reply");
                e.printStackTrace();
            }

            rewardEp += rewardTs;
            // System.out.println("log line : "+ i + " current reward" + rewardEp + " reply : " + reply );

        }

        // close the connecction 
        try {
            cc.send(objectMapper.writeValueAsString(endCmd));
        }catch (IOException e) {
            e.printStackTrace();
        }

        cc.close();
        numEvaluations++;
        aGenome.setNewFitness(rewardEp);
        return rewardEp;
    }

    public double run_episodes() {
        // example of typical episodes run
        double reward_ep = 0;
        float reward_ts = 0 ;
        ArrayList<Float> obs;
        boolean truncated;
        boolean terminated;

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
                    if (debug) {System.out.println("Received message: " + reply);}
                    response.release();
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
        cc.connect();

        // wait connection has been etablished
        try {
            response.acquire(); // Blocks until response is released
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Random random = new Random(seed);

        System.err.println("------------------- run episodes start -------------------");
        // reset the environment
        try {
            String json = objectMapper.writeValueAsString(resetCmd);
            if (debug) {System.out.println("reset cmd send : " + resetCmd);}
            cc.send(json);
        }catch (IOException e) {
            
            e.printStackTrace();
        }

        // Wait for the response
        try {
            if (debug) {System.out.println("waiting response reset ");}
            response.acquire(); // Blocks until latch.countDown() is called
           
        } catch (InterruptedException e) {
            if (debug) {System.out.println("waiting response reset error ");}
            e.printStackTrace();
        }

        if (debug) {System.out.println("reply after reset : " + reply);}
        // read obs after reset 
        try {

            HashMap<String, Object> map = objectMapper.readValue(reply, HashMap.class);
            
            reward_ts  = ((Number) map.get("reward")).floatValue();
            obs = (ArrayList<Float>) map.get("obs");
            truncated = (Boolean) map.get("truncated");
            terminated = (Boolean) map.get("terminated");
        } catch (Exception e) {
            if (debug) {System.out.println("reply reset error " + reply);}
            if (debug) {System.err.println("error read reply");}
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
                response.acquire(); // Blocks until latch.countDown() is called
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // read reply
            System.out.println("reward_ts : " + reward_ts  + "rewards_ep : " +  reward_ep );    
            try {

                HashMap<String, Object> map = objectMapper.readValue(reply, HashMap.class);
                
                reward_ts  = ((Number) map.get("reward")).floatValue();
                obs = (ArrayList<Float>) map.get("obs");
                truncated = (Boolean) map.get("truncated");
                terminated = (Boolean) map.get("terminated");

                if (truncated || terminated)
                {
                    break;
                }
            } catch (Exception e) {
                System.out.println("reply step error " + reply);
                System.err.println("error read reply");
                e.printStackTrace();
            }

            reward_ep += reward_ts;
            System.out.println("log line : "+ i + " current reward" + reward_ep + " reply : " + reply );

        }

        // close the connecction 
        try {
            cc.send(objectMapper.writeValueAsString(endCmd));
        }catch (IOException e) {
            e.printStackTrace();
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