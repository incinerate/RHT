import java.security.*;
import java.math.*;
import java.rmi.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.*;

public class ClientNode implements Runnable
{
    private static SuperNodeDef service; 
    private static int m;
    private static int numDHT;
    private static String choice;
    
    public static void readSampleWords(){
        File fileSample = null;
        BufferedReader buff = null;
        String line = "";
        String[] stringSplit = null;
        //SortedMap<String, String>       sortedMapWords  = null;

        try{
            try{
                fileSample = new File ("src/SampleWords.txt");
                buff = new BufferedReader( new FileReader (fileSample));
                //sortedMapWords  = new TreeMap<String, String>();

                int count = 0;
                while(( line = buff.readLine()) != null){
                    stringSplit = line.split(":");
                    //sortedMapWords.put(stringSplit[0], stringSplit[1]);

                    MessageDigest md = MessageDigest.getInstance("SHA1");
                    md.reset();
                    md.update(stringSplit[0].getBytes());
                    byte[] hashBytes = md.digest();
                    BigInteger hashNum = new BigInteger(1,hashBytes);
                    int key = Math.abs(hashNum.intValue()) % numDHT;  
                    System.out.println("String 1=> "+stringSplit[0] + " || String 2=> "+ key);

                    String response = service.getRandomNode();
                    String[] token = response.split(":");
                    int key2 = key+1;
                    String message_key1 = key + "/" + stringSplit[0] + "/" + stringSplit[1];
                    String message_key2 = key2 + "/" + stringSplit[0] + "/" + stringSplit[1];
                    putKeyDHT(token[0],token[1],message_key1);
                    putKeyDHT(token[0],token[1],message_key2);
                }
            }
            finally{
                buff.close();
            }
        } catch (Exception ae){
            System.err.println("Error reading the file");
            ae.printStackTrace();
        }
    }

    public static boolean lookupKeyDHT(String ip, String port, String message) throws Exception{
        Socket sendingSocket = new Socket(ip,Integer.parseInt(port));
        DataOutputStream out = new DataOutputStream(sendingSocket.getOutputStream());
        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(sendingSocket.getInputStream()));

        out.writeBytes("lookupKey/" + message + "\n");

        String result = inFromServer.readLine();
        if(result.contentEquals("Conflict happened, please try again"))
        	return false;
        String[] token = result.split("/");
        
        
        if (!result.equals("No Word Found!"))
            System.out.println("Lookup result: the meaning is <" + token[1] + "> found in Node " + token[0]);
        else System.out.println(result);
        out.close();
        inFromServer.close();
        sendingSocket.close(); 
        return true;
    }

    public static boolean putKeyDHT(String ip, String port, String message) throws Exception{
        Socket sendingSocket = new Socket(ip,Integer.parseInt(port));
        DataOutputStream out = new DataOutputStream(sendingSocket.getOutputStream());
        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(sendingSocket.getInputStream()));

        System.out.println(message);
//        out.writeChars("tryInsert/" + message + "\n");
        out.writeBytes("putKey/" + message + "\n");

        String result = inFromServer.readLine();
        if(result.contentEquals("Conflict writing operation, retrying"))
        	return false;
        System.out.println(result);
        out.close();
        inFromServer.close();
        sendingSocket.close(); 
        return true;
    }
    
    public static boolean multiputDHT(String ip, String port, String message) throws Exception {
		// TODO Auto-generated method stub
		Socket sendingSocket = new Socket(ip,Integer.parseInt(port));
        DataOutputStream out = new DataOutputStream(sendingSocket.getOutputStream());
        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(sendingSocket.getInputStream()));

        System.out.println(message);
        out.writeBytes("tryMultiput/" + message + "\n");
        
        String result = inFromServer.readLine();
        System.out.println(result);
        if(result.contentEquals("Conflict multiput operation, retrying")) return false;
        out.close();
        inFromServer.close();
        sendingSocket.close(); 
        return true;
	}


    public static void getEachNode(String id, String ip, String port) throws Exception{
        Socket sendingSocket = new Socket(ip,Integer.parseInt(port));
        DataOutputStream out = new DataOutputStream(sendingSocket.getOutputStream());
        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(sendingSocket.getInputStream()));

        out.writeBytes("print" + "\n");
//        out.writeChars("print" + "\n");
        
        String result = inFromServer.readLine();
        System.out.println(result);
        String[] token = result.split("/");

        System.out.println("NODE:<" + id + "> at " + ip + ":" + port + " **************");
        System.out.println("\tPredecessor: " + token[0] + " at " + token[1]); 
        System.out.println("\tContains: " + token[2] + " word:meaning pairs at this node");
        for (int i = 1,j=3; i <= m; i++,j+=3) {
            System.out.println("\tFinger[" + i + "] starts at " + token[j] + "\thas Successor Node ("
                    + token[j+1] + ")\tat " + token[j+2]);
        }
        System.out.println("");
        out.close();
        inFromServer.close();
        sendingSocket.close(); 
    }

    public static void printAllNodeInfo() throws Exception{
        String nodeList = service.getNodeList();
        String[] tokens = nodeList.split("/");
        for (int i = 1; i <= Integer.parseInt(tokens[0]); i++) {
            String[] nodeTok = tokens[i].split(":");
            getEachNode(nodeTok[0], nodeTok[1], nodeTok[2]);
        }
    }


//    public static void main(String[] args) throws Exception {
//		SuperNodeDef superNodeDef = (SuperNodeDef) Naming.lookup("rmi://localhost/SuperNode");
//		String nodeList = superNodeDef.getNodeList();
//		System.out.println(nodeList);
//	}
    @SuppressWarnings("deprecation")
	public static void main(String args[]) throws Exception
    {
        // Check for hostname argument
        if (args.length != 2)
        {
            System.out.println
                ("Syntax - ClientNode [Supernode's IP] [maxNumNodes]");
            System.exit(1);
        }

        int maxNumNodes = Integer.parseInt(args[1]);
        m = (int) Math.ceil(Math.log(maxNumNodes) / Math.log(2));
        numDHT = (int)Math.pow(2,m);

        // Assign security manager
        if (System.getSecurityManager() == null)
        {
            System.setSecurityManager
                (new RMISecurityManager());
        }

        // Call registry for PowerService
        service = (SuperNodeDef) Naming.lookup("rmi://" + args[0] + "/SuperNodeDef");

        Scanner sc = new Scanner(System.in, "UTF-8");
        
//        for(int i = 0; i<numDHT; ++i) {
//        	es.submit(new ClientNode(sc.nextLine()));
//        }

        int initSample = 0;

        for (;;)
        {
            if (initSample == 0)
                System.out.println("1 - Insert the given Sample Dictionary into DHT (only do this once)");
            System.out.println("2 - Look up for a Word in DHT");
            System.out.println("3 - put a Word into DHT");
            System.out.println("4 - Print DHT Structure - All Nodes Info");
            System.out.println("5 - Multi-put");
            System.out.println("6 - Exit"); 
            System.out.println();

            System.out.print ("Choice : ");

            String line = sc.nextLine();

            if (line.equals("1")) {
                readSampleWords();
                initSample = 1;
                System.out.println("");
            }
            else if (line.equals("2")) {
                System.out.print ("Lookup for this word: ");
                String wordLookup = sc.nextLine();


                MessageDigest md2 = MessageDigest.getInstance("SHA1");
                md2.reset();
                md2.update(wordLookup.getBytes());
                byte[] hashBytes2 = md2.digest();
                BigInteger hashNum2 = new BigInteger(1,hashBytes2);
                int key2 = Math.abs(hashNum2.intValue()) % numDHT;  
                int key2_dub = key2 + 1;
                System.out.println("Hashed key: " + key2);

                String response2 = service.getRandomNode();
                String[] token2 = response2.split(":");
                String message2 = key2 + "/" + wordLookup;
                String message2_dub = key2_dub + "/" + wordLookup;
                if(new Random().nextBoolean()) {
                	while(!lookupKeyDHT(token2[0],token2[1],message2))
                		if(!lookupKeyDHT(token2[0],token2[1],message2_dub))
                			Thread.sleep(5000);
                		else break;
                }else {
                	while(!lookupKeyDHT(token2[0],token2[1],message2_dub))
                		if(!lookupKeyDHT(token2[0],token2[1],message2))
                			Thread.sleep(5000);
                		else break;
                }
                System.out.println("");
            }
            else if (line.equals("3")) {
                System.out.print ("Tell me the word you want to insert: ");
                String wordInput = sc.nextLine();					

                MessageDigest md3 = MessageDigest.getInstance("SHA1");
                md3.reset();
                md3.update(wordInput.getBytes());
                byte[] hashBytes3 = md3.digest();
                BigInteger hashNum3 = new BigInteger(1,hashBytes3);
                int key3 = Math.abs(hashNum3.intValue()) % numDHT;  

                System.out.println("Hashed key: " + key3);
                System.out.print ("Tell me the meaning of this word: ");
                String meaningInput = sc.nextLine();

                // Call remote method
                String response3 = service.getRandomNode();
                String[] token3 = response3.split(":");
                String message3 = key3 + "/" + wordInput + "/" + meaningInput;
                //put both of data(key) and its replica(key+1) to DHT
                while(!putKeyDHT(token3[0],token3[1],message3))
                	Thread.sleep(5000);
                System.out.println("");
            }
            else if (line.equals("4")) {
                printAllNodeInfo();
            }
            else if (line.equals("5")) {
            	System.out.print ("Tell me the first word you want to put: ");
                String word1 = sc.nextLine();					

                MessageDigest md = MessageDigest.getInstance("SHA1");
                md.reset();
                md.update(word1.getBytes());
                byte[] hashBytes = md.digest();
                BigInteger hashNum3 = new BigInteger(1,hashBytes);
                int key1 = Math.abs(hashNum3.intValue()) % numDHT;  

                System.out.println("Hashed key: " + key1);
                System.out.print ("Tell me the meaning of this word: ");
                String wordMeaning1 = sc.nextLine();
                
                
                System.out.print ("Tell me the second word you want to put: ");
                String word2 = sc.nextLine();					
                
                md.reset();
                md.update(word2.getBytes());
                hashBytes = md.digest();
                hashNum3 = new BigInteger(1,hashBytes);
                int key2 = Math.abs(hashNum3.intValue()) % numDHT;  
                
                System.out.println("Hashed key: " + key2);
                System.out.print ("Tell me the meaning of this word: ");
                String wordMeaning2 = sc.nextLine();
                
                
                System.out.print ("Tell me the third word you want to put: ");
                String word3 = sc.nextLine();					
                
                md.reset();
                md.update(word3.getBytes());
                hashBytes = md.digest();
                hashNum3 = new BigInteger(1,hashBytes);
                int key3 = Math.abs(hashNum3.intValue()) % numDHT;  
                
                System.out.println("Hashed key: " + key3);
                System.out.print ("Tell me the meaning of this word: ");
                String wordMeaning3 = sc.nextLine();

                // Call remote method
                String response3 = service.getRandomNode();
                String[] token3 = response3.split(":");
                String message3 = key1 + "/" + word1 + "/" + wordMeaning1 + "/"
        				+ key2 + "/" + word2 + "/" + wordMeaning2 + "/"
        				+ key3 + "/" + word3 + "/" + wordMeaning3;
                while(!multiputDHT(token3[0],token3[1],message3)) {
                	Thread.sleep(5000);
                }
                System.out.println("");
			}
            else if (line.equals("6")) {
                System.exit(0);
            }
            else {
                System.out.println("Invalid option");
                System.out.println("");
            }
        }
    }

	@Override
	public void run() {

        try {
			// Call registry for PowerService
			Scanner sc = new Scanner(System.in, "UTF-8");

			int initSample = 0;

			for (;;)
			{
			    if (initSample == 0)
			        System.out.println("1 - Insert the given Sample Dictionary into DHT (only do this once)");
			    System.out.println("2 - Look up for a Word in DHT");
			    System.out.println("3 - put a Word into DHT");
			    System.out.println("4 - Print DHT Structure - All Nodes Info");
			    System.out.println("5 - Multi-put");
			    System.out.println("6 - Exit"); 
			    System.out.println();

			    System.out.print ("Choice : ");

			    String line = sc.nextLine();

			    if (line.equals("1")) {
			        readSampleWords();
			        initSample = 1;
			        System.out.println("");
			    }
			    else if (line.equals("2")) {
			        System.out.print ("Lookup for this word: ");
			        String wordLookup = sc.nextLine();


			        MessageDigest md2 = MessageDigest.getInstance("SHA1");
			        md2.reset();
			        md2.update(wordLookup.getBytes());
			        byte[] hashBytes2 = md2.digest();
			        BigInteger hashNum2 = new BigInteger(1,hashBytes2);
			        int key2 = Math.abs(hashNum2.intValue()) % numDHT;  
			        int key2_dub = key2 + 1;
			        System.out.println("Hashed key: " + key2);

			        String response2 = service.getRandomNode();
			        String[] token2 = response2.split(":");
			        String message2 = key2 + "/" + wordLookup;
			        String message2_dub = key2_dub + "/" + wordLookup;
			        if(new Random().nextBoolean()) {
			        	while(!lookupKeyDHT(token2[0],token2[1],message2))
			        		if(!lookupKeyDHT(token2[0],token2[1],message2_dub))
			        			Thread.sleep(5000);
			        		else break;
			        }else {
			        	while(!lookupKeyDHT(token2[0],token2[1],message2_dub))
			        		if(!lookupKeyDHT(token2[0],token2[1],message2))
			        			Thread.sleep(5000);
			        		else break;
			        }
			        System.out.println("");
			    }
			    else if (line.equals("3")) {
			        System.out.print ("Tell me the word you want to insert: ");
			        String wordInput = sc.nextLine();					

			        MessageDigest md3 = MessageDigest.getInstance("SHA1");
			        md3.reset();
			        md3.update(wordInput.getBytes());
			        byte[] hashBytes3 = md3.digest();
			        BigInteger hashNum3 = new BigInteger(1,hashBytes3);
			        int key3 = Math.abs(hashNum3.intValue()) % numDHT;  

			        System.out.println("Hashed key: " + key3);
			        System.out.print ("Tell me the meaning of this word: ");
			        String meaningInput = sc.nextLine();

			        // Call remote method
			        String response3 = service.getRandomNode();
			        String[] token3 = response3.split(":");
			        String message3 = key3 + "/" + wordInput + "/" + meaningInput;
			        //put both of data(key) and its replica(key+1) to DHT
			        while(!putKeyDHT(token3[0],token3[1],message3))
			        	Thread.sleep(5000);
			        System.out.println("");
			    }
			    else if (line.equals("4")) {
			        printAllNodeInfo();
			    }
			    else if (line.equals("5")) {
			    	System.out.print ("Tell me the first word you want to put: ");
			        String word1 = sc.nextLine();					

			        MessageDigest md = MessageDigest.getInstance("SHA1");
			        md.reset();
			        md.update(word1.getBytes());
			        byte[] hashBytes = md.digest();
			        BigInteger hashNum3 = new BigInteger(1,hashBytes);
			        int key1 = Math.abs(hashNum3.intValue()) % numDHT;  

			        System.out.println("Hashed key: " + key1);
			        System.out.print ("Tell me the meaning of this word: ");
			        String wordMeaning1 = sc.nextLine();
			        
			        
			        System.out.print ("Tell me the second word you want to put: ");
			        String word2 = sc.nextLine();					
			        
			        md.reset();
			        md.update(word2.getBytes());
			        hashBytes = md.digest();
			        hashNum3 = new BigInteger(1,hashBytes);
			        int key2 = Math.abs(hashNum3.intValue()) % numDHT;  
			        
			        System.out.println("Hashed key: " + key2);
			        System.out.print ("Tell me the meaning of this word: ");
			        String wordMeaning2 = sc.nextLine();
			        
			        
			        System.out.print ("Tell me the third word you want to put: ");
			        String word3 = sc.nextLine();					
			        
			        md.reset();
			        md.update(word3.getBytes());
			        hashBytes = md.digest();
			        hashNum3 = new BigInteger(1,hashBytes);
			        int key3 = Math.abs(hashNum3.intValue()) % numDHT;  
			        
			        System.out.println("Hashed key: " + key3);
			        System.out.print ("Tell me the meaning of this word: ");
			        String wordMeaning3 = sc.nextLine();

			        // Call remote method
			        String response3 = service.getRandomNode();
			        String[] token3 = response3.split(":");
			        String message3 = key1 + "/" + word1 + "/" + wordMeaning1 + "/"
							+ key2 + "/" + word2 + "/" + wordMeaning2 + "/"
							+ key3 + "/" + word3 + "/" + wordMeaning3;
			        while(!multiputDHT(token3[0],token3[1],message3)) {
			        	Thread.sleep(5000);
			        }
			        System.out.println("");
				}
			    else if (line.equals("6")) {
			        System.exit(0);
			    }
			    else {
			        System.out.println("Invalid option");
			        System.out.println("");
			    }
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

