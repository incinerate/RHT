
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.math.BigInteger;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClientNode2 extends ClientNode implements Runnable{
	
	private static ExecutorService es = Executors.newFixedThreadPool(8);
	private static SuperNodeDef service; 
    private static int m;
    private static int numDHT;
    private volatile static int count = 0;
    static String line = null;
    static ArrayList<Word> dic;
    
    public ClientNode2(String choice) {
		// TODO Auto-generated constructor stub
    	this.line = choice;
	}

    private static ArrayList<Word> readFile(ArrayList<Word> dic) {
		File fileSample = null;
		BufferedReader buff = null;
		String line = "";
		String[] stringSplit = null;

		fileSample = new File("src/SampleWords.txt");
		try {
			buff = new BufferedReader(new FileReader(fileSample));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		int count = 0;
		try {
			while ((line = buff.readLine()) != null) {
				stringSplit = line.split(":");
				MessageDigest md = MessageDigest.getInstance("SHA1");
				md.reset();
				md.update(stringSplit[0].getBytes());
				byte[] hashBytes = md.digest();
				BigInteger hashNum = new BigInteger(1, hashBytes);
				int key = Math.abs(hashNum.intValue()) % numDHT;
				dic.add(new Word(key, stringSplit[0], stringSplit[1]));
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return dic;
	}
    
	public static void main(String[] args) throws Exception {
		dic = new ArrayList<>();
		numDHT = 5;
		dic = readFile(dic);
		SuperNode supernode = new SuperNode();
		System.out.println(dic.size());
		service = (SuperNodeDef) Naming.lookup("rmi://localhost/SuperNodeDef");
		ClientNode2 task1= new ClientNode2("2");
		ClientNode2 task2= new ClientNode2("3");
		ClientNode2 task3= new ClientNode2("5");
		long startTime = System.currentTimeMillis();
		for (int i = 0; i < 100; i++) {
			es.submit(task1);
			es.submit(task1);
			es.submit(task1);
			es.submit(task2);
			es.submit(task3);
		}
		es.shutdown();
		es.awaitTermination(1, TimeUnit.HOURS);
		long endTime = System.currentTimeMillis();
		System.out.println("Running Time: "+ (endTime-startTime));
		System.out.println(count);
	}
	
	@Override
	public void run() {
		int initSample = 0;
		Random r = new Random();
		Word randomWord = dic.get(r.nextInt(dic.size() - 1));
	    try {
			if (line.equals("1")) {
			    dic = readFile(dic);
			    initSample = 1;
			    System.out.println("");
			}
			else if (line.equals("2")) {
			    System.out.print ("Lookup for this word: ");
			    String wordLookup = randomWord.getWord();


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
			    String wordInput = randomWord.getWord();					

			    MessageDigest md3 = MessageDigest.getInstance("SHA1");
			    md3.reset();
			    md3.update(wordInput.getBytes());
			    byte[] hashBytes3 = md3.digest();
			    BigInteger hashNum3 = new BigInteger(1,hashBytes3);
			    int key3 = Math.abs(hashNum3.intValue()) % numDHT;  

			    System.out.println("Hashed key: " + key3);
			    System.out.print ("Tell me the meaning of this word: ");
			    String meaningInput = "The meaning has been changed!";

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
			    String word1 = randomWord.getWord();					

			    MessageDigest md = MessageDigest.getInstance("SHA1");
			    md.reset();
			    md.update(word1.getBytes());
			    byte[] hashBytes = md.digest();
			    BigInteger hashNum3 = new BigInteger(1,hashBytes);
			    int key1 = Math.abs(hashNum3.intValue()) % numDHT;  

			    System.out.println("Hashed key: " + key1);
			    System.out.print ("Tell me the meaning of this word: ");
			    String wordMeaning1 = "meaning 1";
			    
			    
			    System.out.print ("Tell me the second word you want to put: ");
			    String word2 = dic.get(r.nextInt(dic.size() - 1)).getWord();					
			    
			    md.reset();
			    md.update(word2.getBytes());
			    hashBytes = md.digest();
			    hashNum3 = new BigInteger(1,hashBytes);
			    int key2 = Math.abs(hashNum3.intValue()) % numDHT;  
			    
			    System.out.println("Hashed key: " + key2);
			    System.out.print ("Tell me the meaning of this word: ");
			    String wordMeaning2 = "meaning 2";
			    
			    
			    System.out.print ("Tell me the third word you want to put: ");
			    String word3 = dic.get(r.nextInt(dic.size() - 1)).getWord();					
			    
			    md.reset();
			    md.update(word3.getBytes());
			    hashBytes = md.digest();
			    hashNum3 = new BigInteger(1,hashBytes);
			    int key3 = Math.abs(hashNum3.intValue()) % numDHT;  
			    
			    System.out.println("Hashed key: " + key3);
			    System.out.print ("Tell me the meaning of this word: ");
			    String wordMeaning3 = "meaning 3";

			    // Call remote method
			    String response3 = service.getRandomNode();
			    String[] token3 = response3.split(":");
			    String message3 = key1 + "/" + word1 + "/" + wordMeaning1 + "/"
						+ key2 + "/" + word2 + "/" + wordMeaning2 + "/"
						+ key3 + "/" + word3 + "/" + wordMeaning3;
			    while(!multiputDHT(token3[0],token3[1],message3)) {
			    	count++;
//			    	Thread.sleep(1000);
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
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
