import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Random;

public class Start {

	private static SuperNodeDef service;
	private static int m;
	private static int numDHT;
	// Restore all the available words

	public static void main(String[] args) throws Exception {
		FileWriter writer = new FileWriter(new File("D:\\Eclipse\\My project\\DHT\\src\\SampleWords.txt"), false);
		for (int i = 0; i < 100; i++) {
			writer.write("Word"+i+":meaning"+i+"\r\n");
		}
		writer.close();
	}
}
