import java.rmi.*;
import java.rmi.Naming;
import java.rmi.server.*;
import java.io.*;
import java.net.*;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//
//
// This is the Code for the Node that is part of the DHT. 
//
//
public class NodeDHT implements Runnable //extends UnicastRemoteObject implements NodeDHTInterface
{
    private int ID;
    private static SuperNodeDef service;

    private Socket connection;
    private static ServerSocket serverSocket = null; 

    private static Node me, pred;
    private static Logger logger = LogManager.getLogger("mylog");
    //static int m = 5;
    //static FingerTable[] finger = new FingerTable[m+1];
    //static int numDHT = (int)Math.pow(2,m);
    private static int m;
    private static FingerTable[] finger;
    private static int numDHT;
    private static List<Word> wordList = new ArrayList<Word>();
    private static ExecutorService es = Executors.newFixedThreadPool(8);
    private static ArrayList<ReentrantReadWriteLock> locks = new ArrayList<>();

    public NodeDHT(Socket s, int i) {
        this.connection = s;
        this.ID = i;
    }

    @SuppressWarnings("deprecation")
	public static void main(String args[]) throws Exception
    {
    	logger.info("Creating a DHT Node");
        System.out.println(" ***************************************************************************************************");
        // Check for hostname argument
        if (args.length < 3)
        {
            System.out.println("Syntax - NodeDHT [LocalPortnumber] [SuperNode-HostName] [numNodes]");
            System.out.println("         *** [LocaPortNumber] = is the port number which the Node will be listening waiting for connections.");
            System.out.println("         *** [SuperNode-HostName] = is the hostName of the SuperNode.");
            System.exit(1);
        }	

        int maxNumNodes = Integer.parseInt(args[2]);
        m = (int) Math.ceil(Math.log(maxNumNodes) / Math.log(2));
        finger = new FingerTable[m+1];
        numDHT = (int)Math.pow(2,m);

        System.out.println("The Node starts by connecting at the SuperNode.");
        System.out.println("Establishing connection to the SuperNode...");
        logger.info("The Node starts by connecting at the SuperNode.");
        logger.info("Establishing connection to the SuperNode...");
        // Assign security manager
        if (System.getSecurityManager() == null)
        {
            System.setSecurityManager(new RMISecurityManager());
        }

        InetAddress myIP = InetAddress.getLocalHost();
        System.out.println("My IP: " + myIP.getHostAddress() + "\n");
        logger.info("My IP: " + myIP.getHostAddress() + "\n");

        // Call registry for PowerService
        service = (SuperNodeDef) Naming.lookup("rmi://" + args[1] + "/SuperNodeDef");

        String initInfo = service.getNodeInfo(myIP.getHostAddress(),args[0]);
        if (initInfo.equals("NACK")) {
            System.out.println("NACK! SuperNode is busy. Try again in a few seconds...");
            System.exit(0);
        } else {
            System.out.println("Connection to the SuperNode established succefully");
            System.out.println("Now Joining the DHT network and receiving the Node ID.");	
        }

        String[] tokens = initInfo.split("/");
        me = new Node(Integer.parseInt(tokens[0]),myIP.getHostAddress(),args[0]);
        pred = new Node(Integer.parseInt(tokens[1]),tokens[2],tokens[3]);

        System.out.println("My given Node ID is: "+me.getID() + ". Predecessor ID: " +pred.getID());
        logger.info("My given Node ID is: "+me.getID() + ". Predecessor ID: " +pred.getID());

        Socket temp = null;
        es.submit(new NodeDHT(temp,0));

        int count = 1;
        System.out.println("Listening for connection from Client or other Nodes...");
        logger.info("Listening for connection from Client or other Nodes...");
        logger.info("----------------------------------------------------------");
        int port = Integer.parseInt(args[0]);

        try {
            serverSocket = new ServerSocket( port );
        } catch (IOException e) {
            System.out.println("Could not listen on port " + port);
            System.exit(-1);
        }

        while (true) {
            //System.out.println( "*** Listening socket at:"+ port + " ***" );
            Socket newCon = serverSocket.accept();
//            Thread t = new Thread(new NodeDHT(newCon,count++));
//            t.start();
            es.submit(new NodeDHT(newCon,count++));
        }
        //Start the Client for NodeDHT 	
    }

    public static String makeConnection(String ip, String port, String message) throws Exception {
        //System.out.println("Making connection to " + ip + " at " +port + " to " + message);
        if (me.getIP().equals(ip) && me.getPort().equals(port)){
            String response = considerInput(message);
            //System.out.println("local result " + message + " answer: "  + response);
            return response;
        } else {

            Socket sendingSocket = new Socket(ip,Integer.parseInt(port));
            DataOutputStream out = new DataOutputStream(sendingSocket.getOutputStream());
            BufferedReader inFromServer = new BufferedReader(new InputStreamReader(sendingSocket.getInputStream()));

            //System.out.println("Sending request: " + message + " to " + ip + " at " + port);
            out.writeBytes(message + "\n");

            String result = inFromServer.readLine();
            //System.out.println("From Server: " + result);
            out.close();
            inFromServer.close();
            sendingSocket.close(); 
            return result;
        }
    }


    public void run() {

        if (this.ID == 0) {

            System.out.println("Building Finger table ... ");
            logger.info("Building Finger table ... ");
            for (int i = 1; i <= m; i++) {
                finger[i] = new FingerTable();
                finger[i].setStart((me.getID() + (int)Math.pow(2,i-1)) % numDHT);
            }
            for (int i = 1; i < m; i++) {
                finger[i].setInterval(finger[i].getStart(),finger[i+1].getStart()); 
            }
            finger[m].setInterval(finger[m].getStart(),finger[1].getStart()-1); 


            if (pred.getID() == me.getID()) { //if predcessor is same as my ID -> only node in DHT
                for (int i = 1; i <= m; i++) {
                    finger[i].setSuccessor(me);
                }
                System.out.println("Done, all finger tablet set as me (only node in DHT)");
                logger.info("Done, all finger tablet set as me (only node in DHT)");
            }
            else {
                for (int i = 1; i <= m; i++) {
                    finger[i].setSuccessor(me);
                }
                try{
                    init_finger_table(pred);
                    System.out.println("Initiated Finger Table!");
                    update_others();
                    System.out.println("Updated all other nodes!");
                } catch (Exception e) {}
            }
            try { 
                service.finishJoining(me.getID());
            } catch (Exception e) {}
        }
        else {
            try {
//                System.out.println( "*** A Client came; Service it *** " + this.ID );
            	logger.info( "*** A Client came; Service it *** " + this.ID );

                BufferedReader inFromClient =
                    new BufferedReader(new InputStreamReader(connection.getInputStream()));
                DataOutputStream outToClient = new DataOutputStream(connection.getOutputStream());
                String received = inFromClient.readLine();
                logger.info("received a message from client : " + received);
//                System.out.println("Received: " + received);
                String response = considerInput(received);
                logger.info("The response for client: "+ response);
                //System.out.println("Sending back to client: "+ response);

                outToClient.writeBytes(response + "\n");	
            } catch (Exception e) {
                System.out.println("Thread cannot serve connection");
            }

        }
    }


    public static String considerInput(String received) throws Exception {
        String[] tokens = received.split("/");
        String outResponse = "";

        if (tokens[0].equals("setPred")) {
        	logger.info("Set the predecessor");
            Node newNode = new Node(Integer.parseInt(tokens[1]),tokens[2],tokens[3]);
            setPredecessor(newNode);
            outResponse = "set it successfully";	
        }
        else if (tokens[0].equals("getPred")) {
        	logger.info("Get the predecessor");
            Node newNode = getPredecessor();
            outResponse = newNode.getID() + "/" + newNode.getIP() + "/" + newNode.getPort() ;
        }
        else if (tokens[0].equals("findSuc")) {
        	logger.info("Find the Successor of Node " +me.getID()+ ", " + me.getIP()+":"+me.getPort());
            Node newNode = find_successor(Integer.parseInt(tokens[1]));
            outResponse = newNode.getID() + "/" + newNode.getIP() + "/" + newNode.getPort() ;
        }
        else if (tokens[0].equals("getSuc")) {
        	logger.info("get the Successor of Node "+ me.getID());
            Node newNode = getSuccessor();
            outResponse = newNode.getID() + "/" + newNode.getIP() + "/" + newNode.getPort() ;
        }
        else if (tokens[0].equals("closetPred")) {
            Node newNode = closet_preceding_finger(Integer.parseInt(tokens[1]));
            outResponse = newNode.getID() + "/" + newNode.getIP() + "/" + newNode.getPort() ;
        }
        else if (tokens[0].equals("updateFing")) {
            Node newNode = new Node(Integer.parseInt(tokens[1]),tokens[2],tokens[3]);
            update_finger_table(newNode,Integer.parseInt(tokens[4]));
            outResponse = "update finger " + Integer.parseInt(tokens[4]) + " successfully";	
        }
        else if (tokens[0].equals("print")) {
            outResponse = returnAllFingers();
        }
        else if (tokens[0].equals("acquireLock")) {
        	logger.info("Accquire the lock");
        	if(acquireLock(tokens[1], tokens[2], tokens[3]))
        		outResponse = "Sucessed";
        	else {
				outResponse = "Failed";
			}
		}
        else if (tokens[0].equals("releaseLock")) {
        	logger.info("Release the lock");
        	if(releaseLock(tokens[1], tokens[2], tokens[3]))
        		outResponse = "Sucessed";
        	else {
        		outResponse = "Failed";
        	}
        }
        else if (tokens[0].equals("releaseSingleLock")) {
        	logger.info("Release one lock in this node");
        	if(releaseSingleLock(tokens[1]))
        		outResponse = "Sucessed";
        	else {
        		outResponse = "Failed";
        	}
        }
        else if (tokens[0].equals("putKey")) {
        	outResponse = putKey(Integer.parseInt(tokens[1]),tokens[2], tokens[3]);
		}
        else if (tokens[0].equals("put")){
            if(put(Integer.parseInt(tokens[1]),tokens[2],tokens[3])) {
            	outResponse = "put pair " + tokens[2] + ":" + tokens[3] + " into DHT";
            	logger.info("put operation successed");
            }
            else {
            	outResponse = "Conflict writing operation, retrying";
            	logger.info("put operation failed");
            }
        }
        else if (tokens[0].equals("putReplica")) {
        	if(putReplica(Integer.parseInt(tokens[1]),tokens[2],tokens[3])) {
        		logger.info("put replica operation successed");
        		outResponse = "put replica pair " + tokens[2] + ":" + tokens[3] + " into DHT";
        	}
            else {
            	logger.info("put replica operation failed");
            	outResponse = "Conflict writing replica operation, retrying";
            }
		}
        else if (tokens[0].equals("tryMultiput")) {
        	if(tryMultiput(Integer.parseInt(tokens[1]),tokens[2],tokens[3],Integer.parseInt(tokens[4]),tokens[5],tokens[6],
        			Integer.parseInt(tokens[7]),tokens[8],tokens[9]))
            	outResponse = "put multi pair " + tokens[2] + ":" + tokens[3] + ";"+ tokens[5] + ":" + tokens[6]+ ";" + tokens[8] + ":" + tokens[9]+ " into DHT";
            else
            	outResponse = "Conflict multiput operation, retrying";
		}
        else if (tokens[0].equals("multiput")) {
			outResponse = multiput_put(Integer.parseInt(tokens[1]),tokens[2],tokens[3]);
		}
        else if (tokens[0].equals("insertKey")) {
            insertKey(Integer.parseInt(tokens[1]),tokens[2],tokens[3]);
        }
        else if (tokens[0].equals("lookupKey")){
            outResponse = lookupKey(Integer.parseInt(tokens[1]),tokens[2]);
        }
        else if (tokens[0].equals("getWord")) {
            outResponse = getWord(tokens[1]);
        }
        //System.out.println("outResponse for " + tokens[0] + ": " + outResponse);
        return outResponse;
    }
    
	public static boolean acquireLock(String word1, String word2, String word3) throws Exception {
		// TODO Auto-generated method stub
		for (int i = 0; i < wordList.size(); ++i) {
			if(wordList.get(i).getWord().equals(word1)) {
				logger.info("Trying to grab the writelock of "+word1+" £¡");
				if(!locks.get(i).writeLock().tryLock(1, TimeUnit.SECONDS)&&!locks.get(i).writeLock().isHeldByCurrentThread()) {
					logger.info("Failed to grab the writelock of "+word1+" £¡");
					return false;
				}
				logger.info("grab the writelock of "+word1+" Successed£¡");
			}
			if(wordList.get(i).getWord().equals(word2)) {
				logger.info("Trying to grab the writelock of "+word2+" £¡");
				if(!locks.get(i).writeLock().tryLock(1, TimeUnit.SECONDS)&&!locks.get(i).writeLock().isHeldByCurrentThread()) {
					logger.info("Failed to grab the writelock of "+word2+" £¡");
					return false;
				}
				logger.info("grab the writelock of "+word2+" Successed£¡");
			}
			if(wordList.get(i).getWord().equals(word3)) {
				logger.info("Trying to grab the writelock of "+word3+" £¡");
				if(!locks.get(i).writeLock().tryLock(1, TimeUnit.SECONDS)&&!locks.get(i).writeLock().isHeldByCurrentThread()) {
					logger.info("Failed to grab the writelock of "+word3+" £¡");
					return false;
				}
				logger.info("grab the writelock of "+word3+" Successed£¡");
			}
			
		}
		return true;
	}
    
//	public static boolean acquireLock(String word1, String word2, String word3) throws Exception {
//		// TODO Auto-generated method stub
//		for (int i = 0; i < wordList.size(); ++i) {
//			if(wordList.get(i).getWord().equals(word1)) {
//				logger.info("Trying to grab the writelock of "+word1+" £¡");
//				if(!locks.get(i).writeLock().tryLock(10, TimeUnit.SECONDS)) {
////					logger.info("Failed to grab the writelock of "+word1+" £¡");
//					locks.get(i).writeLock().lockInterruptibly();
//					return true;
//				}
//				logger.info("grab the writelock of "+word1+" Successed£¡");
//			}
//			if(wordList.get(i).getWord().equals(word2)) {
//				logger.info("Trying to grab the writelock of "+word2+" £¡");
//				if(!locks.get(i).writeLock().tryLock(10, TimeUnit.SECONDS)) {
////					logger.info("Failed to grab the writelock of "+word2+" £¡");
//					locks.get(i).writeLock().lockInterruptibly();
//					return true;
//				}
//				logger.info("grab the writelock of "+word2+" Successed£¡");
//			}
//			if(wordList.get(i).getWord().equals(word3)) {
//				logger.info("Trying to grab the writelock of "+word3+" £¡");
//				if(!locks.get(i).writeLock().tryLock(10, TimeUnit.SECONDS)) {
////					logger.info("Failed to grab the writelock of "+word3+" £¡");
//					locks.get(i).writeLock().lockInterruptibly();
//					return true;
//				}
//				logger.info("grab the writelock of "+word3+" Successed£¡");
//			}
//		}
//		return true;
//	}
	public static boolean releaseLock(String word1, String word2, String word3) throws Exception {
		// TODO Auto-generated method stub
		for (int i = 0; i < wordList.size(); ++i) {
			if(wordList.get(i).getWord().equals(word1)) {
				logger.info("Trying to release the writelock of "+word1+" £¡");
				if(locks.get(i).writeLock().isHeldByCurrentThread()) {
					locks.get(i).writeLock().unlock();
					logger.info("Successed to release the writelock of "+word1+" £¡");
				}else {
					locks.set(i, new ReentrantReadWriteLock());
				}
			}
			if(wordList.get(i).getWord().equals(word2)) {
				logger.info("Trying to release the writelock of "+word2+" £¡");
				if(locks.get(i).writeLock().isHeldByCurrentThread()) {
					locks.get(i).writeLock().unlock();
					logger.info("Successed to release the writelock of "+word2+" £¡");
				}else {
					locks.set(i, new ReentrantReadWriteLock());
				}
			}
			if(wordList.get(i).getWord().equals(word3)) {
				logger.info("Trying to release the writelock of "+word3+" £¡");
				if(locks.get(i).writeLock().isHeldByCurrentThread()) {
					locks.get(i).writeLock().unlock();
					logger.info("Successed to release the writelock of "+word3+" £¡");
				}else {
					locks.set(i, new ReentrantReadWriteLock());
				}
			}
		}
		return true;
	}
	
	public static boolean releaseSingleLock(String word) throws Exception {
		// TODO Auto-generated method stub
		for (int i = 0; i < wordList.size(); ++i) {
			if(wordList.get(i).getWord().equals(word)) {
				logger.info("Trying to release the writelock of "+word+" £¡");
				if(locks.get(i).writeLock().isHeldByCurrentThread()) {
					locks.get(i).writeLock().unlock();
					logger.info("Successed to release the writelock of "+word+" £¡");
				}else {
					makeConnection(me.getIP(),me.getPort(),"releaseSingleLock/" +  word);
				}
			}
		}
		return true;
	}

	public static String getWord(String word){
        Iterator<Word> iterator = wordList.iterator();
        int count = 0;
        while (iterator.hasNext()) {
            Word wordScan = iterator.next();
            String wordMatch = wordScan.getWord();
            if (word.equals(wordMatch)) {
            	logger.info("Word has been found in this node");
            	logger.info("Get the number of readlock: "+ locks.get(count).getReadHoldCount());
            	if(!locks.get(count).readLock().tryLock()) return "Conflict happened, please try again";
            	System.out.println("*** Found at this Node [" + me.getID() + "] the meaning (" 
            			+ wordScan.getMeaning() + ") of word (" + word + ")"); 
            	logger.info("*** Found at this Node [" + me.getID() + "] the meaning (" 
            			+ wordScan.getMeaning() + ") of word (" + word + ")"); 
                locks.get(count).readLock().unlock();
                logger.info(wordMatch + " readlock released!");
                return me.getID() + "/" + wordScan.getMeaning(); 
            }
            count++;
        }
        System.out.println("*** Found its Node [" + me.getID() + "] but No Word ("+word+") Found here!");
        logger.info("*** Found its Node [" + me.getID() + "] but No Word ("+word+") Found here!");
        return "No Word Found!";
    }
	
	public static String putKey(int key, String word, String meaning) throws Exception {
		System.out.println("*** Put starting here at Node [" + me.getID() +
	                "] for word (" + word + ") with key (" + key + ")");
		logger.info("*** Put starting here at Node [" + me.getID() +
				"] for word (" + word + ") with key (" + key + ")");
        Node destNode = find_successor(key);
        String request = "put/" + key + "/" +  word + "/" + meaning;
        String response = "";
        response = makeConnection(destNode.getIP(),destNode.getPort(),request);
        return response;
	}

    public static String lookupKey(int key, String word) throws Exception {
        System.out.println("*** Looking Up starting here at Node [" + me.getID() +
                "] for word (" + word + ") with key (" + key + ")");
        logger.info("*** Looking Up starting here at Node [" + me.getID() +
        		"] for word (" + word + ") with key (" + key + ")");
        Node destNode = find_successor(key);
        String request = "getWord/" +  word;
        String response = "";
        logger.info("DestNode founded, sending request to Node: "+ destNode.getID()+". " + destNode.getIP()+ ":"+destNode.getPort());
        response = makeConnection(destNode.getIP(),destNode.getPort(),request);
        return response;
    }

    public static void tryInsert(int key, String word, String meaning) throws Exception {
        System.out.println("*** Starting here at this Node ["+me.getID()+"] to insert word ("+word+
                ") with key ("+key+"), routing to destination Node...");
        logger.info("*** Starting here at this Node ["+me.getID()+"] to insert word ("+word+
        		") with key ("+key+"), routing to destination Node...");
        Node destNode = find_successor(key);
        String request = "insertKey/" + key + "/" +  word + "/" + meaning;
        makeConnection(destNode.getIP(),destNode.getPort(),request);
    }

    public static void insertKey(int key, String word, String meaning) throws Exception { 
        System.out.println("*** Found the dest Node ["+me.getID()+"] here for Insertion of word ("
                + word + ") with key ("+key+")");
        logger.info("*** Found the dest Node ["+me.getID()+"] here for Insertion of word ("
        		+ word + ") with key ("+key+")");
        locks.add(new ReentrantReadWriteLock());
        wordList.add(new Word(key,word,meaning));
    }

    public static String returnAllFingers(){
        String response = "";
        response = response + pred.getID() + "/" + pred.getIP() + ":" + pred.getPort() + "/";
        response = response + wordList.size() + "/";
        for (int i = 1; i <= m; i++) {
            response = response + finger[i].getStart() + "/" + finger[i].getSuccessor().getID() + "/" 
                + finger[i].getSuccessor().getIP() + ":" + finger[i].getSuccessor().getPort() + "/";
        }
        return response;
    }

    public static void init_finger_table(Node n) throws Exception {
        int myID, nextID;

        String request = "findSuc/" + finger[1].getStart();
        String result = makeConnection(n.getIP(),n.getPort(),request);
        System.out.println("Asking node " + n.getID() + " at " + n.getIP());

        String[] tokens = result.split("/");
        finger[1].setSuccessor(new Node(Integer.parseInt(tokens[0]),tokens[1],tokens[2]));
        //printAllFingers();

        String request2 = "getPred";
        String result2 = makeConnection(finger[1].getSuccessor().getIP(),finger[1].getSuccessor().getPort(),request2);
        String[] tokens2 = result2.split("/");
        pred = new Node(Integer.parseInt(tokens2[0]),tokens2[1],tokens2[2]);

        String request3 = "setPred/" + me.getID() + "/" + me.getIP() + "/" + me.getPort();
        makeConnection(finger[1].getSuccessor().getIP(),finger[1].getSuccessor().getPort(),request3);

        int normalInterval = 1;
        for (int i = 1; i <= m-1; i++) {

            myID = me.getID();
            nextID = finger[i].getSuccessor().getID(); 

            if (myID >= nextID)
                normalInterval = 0;
            else normalInterval = 1;

            if ( (normalInterval==1 && (finger[i+1].getStart() >= myID && finger[i+1].getStart() <= nextID))
                    || (normalInterval==0 && (finger[i+1].getStart() >= myID || finger[i+1].getStart() <= nextID))) {

                finger[i+1].setSuccessor(finger[i].getSuccessor());
            } else {

                String request4 = "findSuc/" + finger[i+1].getStart();
                String result4 = makeConnection(n.getIP(),n.getPort(),request4);
                String[] tokens4 = result4.split("/");

                int fiStart = finger[i+1].getStart();
                int succ = Integer.parseInt(tokens4[0]); 
                int fiSucc = finger[i+1].getSuccessor().getID();
                if (fiStart > succ) 
                    succ = succ + numDHT;
                if (fiStart > fiSucc)
                    fiSucc = fiSucc + numDHT;

                if ( fiStart <= succ && succ <= fiSucc ) {
                    finger[i+1].setSuccessor(new Node(Integer.parseInt(tokens4[0]),tokens4[1],tokens4[2]));
                }
            }
        }
    }

    public static void update_others() throws Exception{
        Node p;
        for (int i = 1; i <= m; i++) {
            int id = me.getID() - (int)Math.pow(2,i-1) + 1;
            if (id < 0)
                id = id + numDHT; 

            p = find_predecessor(id);


            String request = "updateFing/" + me.getID() + "/" + me.getIP() + "/" + me.getPort() + "/" + i;  
            makeConnection(p.getIP(),p.getPort(),request);

        }
    }

    public static void update_finger_table(Node s, int i) throws Exception // RemoteException,
           {

               Node p;
               int normalInterval = 1;
               int myID = me.getID();
               int nextID = finger[i].getSuccessor().getID();
               if (myID >= nextID) 
                   normalInterval = 0;
               else normalInterval = 1;

               //System.out.println("here!" + s.getID() + " between " + myID + " and " + nextID);

               if ( ((normalInterval==1 && (s.getID() >= myID && s.getID() < nextID)) ||
                           (normalInterval==0 && (s.getID() >= myID || s.getID() < nextID)))
                       && (me.getID() != s.getID() ) ) {

                   //	System.out.println("there!");

                   finger[i].setSuccessor(s);
                   p = pred;

                   String request = "updateFing/" + s.getID() + "/" + s.getIP() + "/" + s.getPort() + "/" + i;  
                   makeConnection(p.getIP(),p.getPort(),request);
                       }
               //printAllFingers();
    }

    public static void setPredecessor(Node n) // throws RemoteException
    {
        pred = n;
    }

    public static Node getPredecessor() //throws RemoteException 
    {
        return pred;
    }

    public static Node find_successor(int id) throws Exception //RemoteException,
           {
               System.out.println("Visiting here at Node <" + me.getID()+"> to find successor of key ("+ id +")"); 

               Node n;
               n = find_predecessor(id);

               String request = "getSuc/" ;
               String result = makeConnection(n.getIP(),n.getPort(),request);
               String[] tokens = result.split("/");
               Node tempNode = new Node(Integer.parseInt(tokens[0]),tokens[1],tokens[2]);
               return tempNode;
    }

    public static Node find_predecessor(int id)  throws Exception
    {
        Node n = me;
        int myID = n.getID();
        int succID = finger[1].getSuccessor().getID();
        int normalInterval = 1;
        if (myID >= succID)
            normalInterval = 0;


        //	System.out.println("id ... " + id + " my " + myID + " succ " + succID + " " );

        while ((normalInterval==1 && (id <= myID || id > succID)) ||
                (normalInterval==0 && (id <= myID && id > succID))) {


            String request = "closetPred/" + id ;
            String result = makeConnection(n.getIP(),n.getPort(),request);
            String[] tokens = result.split("/");

            n = new Node(Integer.parseInt(tokens[0]),tokens[1],tokens[2]);

            myID = n.getID();

            String request2 = "getSuc/" ;
            String result2 = makeConnection(n.getIP(),n.getPort(),request2);
            String[] tokens2 = result2.split("/");

            succID = Integer.parseInt(tokens2[0]);

            if (myID >= succID) 
                normalInterval = 0;
            else normalInterval = 1;
                }
        //System.out.println("Returning n" + n.getID());

        return n;
    }

    public static Node getSuccessor() //throws RemoteException
    {
        return finger[1].getSuccessor();
    }

    public static Node closet_preceding_finger(int id) //throws RemoteException 
    {
        int normalInterval = 1;
        int myID = me.getID();
        if (myID >= id) {
            normalInterval = 0;
        }

        for (int i = m; i >= 1; i--) {
            int nodeID = finger[i].getSuccessor().getID();
            if (normalInterval == 1) {
                if (nodeID > myID && nodeID < id) 
                    return finger[i].getSuccessor();
            } else {
                if (nodeID > myID || nodeID < id) 
                    return finger[i].getSuccessor();
            }
        }
        return me;
    }
    
    public static boolean put(int key, String word, String meaning) {
    	logger.info("Put operation start at dest node "+me.getID());
    	Word newWord = new Word(key, word, meaning);
		if(wordList.contains(newWord)) {
			logger.info("The word "+ newWord.getWord() +" is exist!");
			int id = wordList.indexOf(newWord);
			try {
				logger.info("Trying to get the writelock of this word "+ newWord.getWord()+ " !!!");
				if(locks.get(id).writeLock().isHeldByCurrentThread()||locks.get(id).writeLock().tryLock(1, TimeUnit.SECONDS)) {
					//need to hold both of locks of this and replica data
					logger.info("Start finding the replica location of "+ newWord.getWord()+ " !");
					Node destNode = find_successor(key+1);
					logger.info("The replica data is at Node "+ destNode.getID() + ". " + destNode.getIP()+" : "+destNode.getPort());
					String request = "putReplica/" + key+1 + "/" +  word + "/" + meaning;
					logger.info("send msg to put replica word : " + request);
			        String result = makeConnection(destNode.getIP(),destNode.getPort(),request);
			        logger.info("result of put replica: "+ request);
			        if(!result.contentEquals("Conflict writing replica operation, retrying")) {
			        	wordList.set(id, newWord);
			        	if(locks.get(id).writeLock().isHeldByCurrentThread()) {
			        		locks.get(id).writeLock().unlock();
			        		logger.error("Writing operation failed, release the lock");
			        	}
			        	return true;
			        }
			        locks.get(id).writeLock().unlock();
			        logger.info("Writing operation successed, release the lock");
			        return false;
				}else {
					logger.error("Grab the original data writelock failed");
					return false;
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				logger.error("Grab the data's writelock failed");
				e.printStackTrace();
			} 
		}else {
			try {
				logger.info("start to put a new word");
				tryInsert(key, word, meaning);
				logger.info("start to put a new word's replica");
				tryInsert(key+1, word, meaning);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    	return true;
    }
    
    public static boolean putReplica(int key, String word, String meaning) {
    	Word newWord = new Word(key, word, meaning);
		if(wordList.contains(newWord)) {
			logger.info("The word's replica "+ newWord.getWord() +" is exist!");
			int id = wordList.indexOf(newWord);
			try {
				logger.info("Trying to get the writelock of this word's replica "+ newWord.getWord()+ " !!!");
				if(locks.get(id).writeLock().isHeldByCurrentThread()||locks.get(id).writeLock().tryLock(1, TimeUnit.SECONDS)) {
					//need to hold both of locks of this and replica data
					logger.info("Get the writelock of this word's replica "+ newWord.getWord()+ "Successed!");
					wordList.set(id, newWord);
					if(locks.get(id).writeLock().isHeldByCurrentThread())	{
						logger.info("Writing replica operation successed, release the lock");
						locks.get(id).writeLock().unlock();
					}
					return true;
				}else {
					logger.error("Grab the replica data writelock failed");
					return false;
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
		}else {
			try {
				logger.info("put a new word's replica");
				insertKey(key, word, meaning);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    	return true;
    }
    
    public static boolean tryMultiput(int key1, String word1, String meaning1, int key2, String word2, String meaning2,
    		int key3, String word3, String meaning3) throws Exception {
    	
    	HashSet<Node> nodeSet = new HashSet<>(); 
    	int replica_key1 = key1 + 1;
    	int replica_key2 = key2 + 1;
    	int replica_key3 = key3 + 1;
    	
		Node node1 = find_successor(key1);
		Node node2 = find_successor(key2);
		Node node3 = find_successor(key3);
		Node node1_dub = find_successor(replica_key1);
		Node node2_dub = find_successor(replica_key2);
		Node node3_dub = find_successor(replica_key3);
		
		nodeSet.add(node3_dub);
		nodeSet.add(node3);
		nodeSet.add(node2_dub);
		nodeSet.add(node2);
		nodeSet.add(node1_dub);
		nodeSet.add(node1);
		
		logger.info("Number of node waiting for acquireLock : "+nodeSet.size());
		String request = "acquireLock/" +  word1 + "/" + word2 + "/" + word3;
		for (Node node : nodeSet) {
			String result = makeConnection(node.getIP(),node.getPort(),request);
			logger.info("All LOCKS ACQUIRED£¡£¡" + result);
			System.out.println("All LOCKS ACQUIRED AT NODE " + node.getID()+" "+result+"!!!");
			if("Failed".equals(result)) {
				String request1 = "releaseLock/" +  word1 + "/" + word2 + "/" + word3;
				for (Node n : nodeSet) {
					String result1 = makeConnection(n.getIP(),n.getPort(),request1);
				}
				return false;
			}
		}
		
		String request1 = "multiput/" + key1 +"/"+ word1 + "/" + meaning1;
		String result1 = makeConnection(node1.getIP(),node1.getPort(),request1);
		
		String request1_dub = "multiput/" + replica_key1 +"/"+ word1 + "/" + meaning1;
		String result1_dub = makeConnection(node1_dub.getIP(),node1_dub.getPort(),request1_dub);
		
		String request2 = "multiput/" + key2 +"/"+ word2 + "/" + meaning2;
		String result2 = makeConnection(node2.getIP(),node2.getPort(),request2);
		
		String request2_dub = "multiput/" + replica_key2 +"/"+ word2 + "/" + meaning2;
		String result2_dub = makeConnection(node2_dub.getIP(),node2_dub.getPort(),request2_dub);
		
		String request3 = "multiput/" + key3 +"/"+ word3 + "/" + meaning3;
		String result3 = makeConnection(node3.getIP(),node3.getPort(),request3);
		
		String request3_dub = "multiput/" + replica_key3 +"/"+ word3 + "/" + meaning3;
		String result3_dub = makeConnection(node3_dub.getIP(),node3_dub.getPort(),request3_dub);
		return true;
	}
    
    public static String multiput_put(int key, String word, String meaning) throws Exception {
		// TODO Auto-generated method stub
    	Word newWord = new Word(key, word, meaning);
    	logger.info("mutiput_put "+word+" start");
    	if(wordList.contains(newWord)) {
    		int id = wordList.indexOf(newWord);
    		wordList.set(id, newWord);
    		logger.info("The number of thread which hold this word's lock: " + locks.get(id).writeLock().getHoldCount());
    		locks.set(id, new ReentrantReadWriteLock());
//    		if(locks.get(id).writeLock().getHoldCount()!=0) {
//    			logger.info(newWord.getWord()+" ÊÍ·ÅËø");
//    			locks.get(id).writeLock().unlock();
//    		}
    	}else {
    		logger.info("insert "+word);
			insertKey(key, word, meaning);
		}
		return "Multiput Success";
	}

}

