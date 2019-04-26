public class Node {
	private int myID;
	private String myIP;
	private String myPort;

	public void setID (int id) {
		this.myID = id;
	}
	public void setIP (String ip) {
		this.myIP = ip;
	}
	public void setPort (String port) {
		this.myPort = port;
	}
	public int getID() {
		return this.myID;
	}
	public String getIP() {
		return this.myIP;
	}
	public String getPort() {
		return this.myPort;
	}
	public Node(int id, String ip, String port){
		myID = id;
		myIP = ip;
		myPort = port;
	}
	@Override
	public int hashCode() {
		// TODO Auto-generated method stub
		return this.myID;
	}
	@Override
	public boolean equals(Object obj) {
		// TODO Auto-generated method stub
		if(obj instanceof Node) {
			Node n = (Node) obj;
			return this.myID == n.myID;
		}
		return false;
	}
	
}

