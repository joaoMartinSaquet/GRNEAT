
# Gene Regulatory Network Evolution Through Augmenting Topologies

# Quickstart:

This code was originally made in Eclipse, and works well there with the standard "Run" mechanism.

Go to: java/evolver/Evolver.java and press Run

# To change experiments: 

Go to the main() function in Evolver.java

Change line:
			e.evaluator = new IntertwinedSpirals( args );
			
To the evaluator of your choice.

The main function was designed for command line usage in a .jar, but by swapping the evaluator class that you initialize you can easily change the problem within the IDE of your choice.



# Using with Regressor

to use it with regressor you first needs a python evaluator that is going to take a server role like shown below : 

```
class Regressor(object):

    def __init__(self, fitness_function = 'mse'):

        if fitness_function == 'mse':
            self.fit_fun = mse


        # defaults datas 
        self.x = np.array([np.linspace(-5,5), np.linspace(-5,5)]).T
        self.y = self.x

    def load_data(self, x, y):
        """load data from a script

        Args:
            x (array): GRN inputs
            y (array): true output to get from GRN 
        """

        self.x = x
        self.y = y

    async def handler(self, websocket):
        """This function handle the communication between GRNEAT and the python evaluator 
        
            Python Component (py)                       Java Component
            -----------------------------------------------------------------
            1. Sends `GRN_input`                  ----> 1. Receives `GRN_input`
                                                            - Processes `GRN_input` to generate `yGRN`

            2. Waits to receive `yGRN`             <--- 2. Sends `yGRN`
            - Computes the fitness based on `yGRN`

            3. Sends the computed fitness            -> 3. Receives the computed fitness
        Args:
            websocket (_type_): _description_
            path (_type_): _description_
        """
        self.end = False
        while True:
            # attend un message
            msg = await websocket.recv()
            if msg == "START":
                reply = {"inputs" : self.x.tolist()}
            elif msg == "END":
                reply = "ended"
                self.end = True
            else : 
                # get ygrn
                y_grn = json.loads(msg)['y_grn']
                reply = {"fitness" : self.fit_fun(y_grn, self.y)}    
                
            await websocket.send(json.dumps(reply))
            if self.end:
                break
        

    async def start_server(self):
        
        # start_server = websockets.serve(self.handler, "localhost", config[server][port])

        async with websockets.serve(self.handler, "localhost", 8000):
        # async with websockets.serve(self.handler, host, port):
            print(f"WebSocket server started at ")
            await asyncio.Future()  # Run forever



if __name__ == "__main__":

    server = Regressor()
    asyncio.run(server.start_server())
    server.start_server()
```


when it's done, you need to start first the python server that will initialize every things and load data, or run env for RL task. then launch the mvn project


Launch the python server
```
python regressor_file.py
```

Launch the java client
```
mvn -f pom.xml exec:java -Dexec.mainClass="evolver.Evolver"

```

# Citing:

Cussat-Blanc, S., Harrington, K., and Pollack, J. (2015) Gene Regulatory Network Evolution Through Augmenting Topologies. IEEE Transactions on Evolutionary Computation 19(6), pp. 823 - 837.
http://dx.doi.org/10.1109/TEVC.2015.2396199
