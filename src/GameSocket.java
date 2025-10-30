
import java.io.*;
import java.net.Socket;

public class GameSocket {

	/** Game port. */
	static final int port = 80;
	/** connect() result constants. */
	public static final int COMFAIL = -3, INVALID = -2, NOSIT = -1, SIT_FIRST = 1, SIT_SECOND = 2;
	/** Game communication codes. */
	final static int CLOSING = -1, PLEASE_PLAY = 0, YOUR_RESULT = 1, OPP_RESULT = 2, GAME_OVER = 3; 
	/** Invalid move score/result constant */
	final static int INVALID_MOVE = -1;
	/** Opponent type constants. */
	public static final int ANY = 0, RANDOM_BOT_FIRST = -1, RANDOM_BOT_SECOND = -2;
 
	
	/** The sockets and its associated input and output data streams. */
	Socket s;
	/** The sockets and its associated input and output data streams. */
	DataInputStream inStrm;
	/** The sockets and its associated input and output data streams. */
	DataOutputStream outStrm;
	
	/** connect() error transmitted message string. */
	public String connMsg;
	
	/** Internal communication flag to know whether or not a move must be send. */
	private boolean myTurn;
	
	/** Try to connect to the game server. 
	 * 		Returns -3 for failure to connect, -2, failure as an individual, -1 failure to sit at table
	 * 		Returns 1 if first player
	 *  	Returns 2 if second player
	 *  If connection fails, the message is in the variable connMsg. See also class constants above.*/
	public int connect(String IP, int ID, int desiredTable, int pw, int desiredOpp, int desiredSize) {
		try {
			//Connect to server
			myTurn = false;
			s = new Socket(IP, port);
			inStrm = new DataInputStream(s.getInputStream());
			outStrm = new DataOutputStream(s.getOutputStream());
			
			//Send 5 int data
			outStrm.writeInt(ID);
			outStrm.writeInt(desiredTable);
			outStrm.writeInt(pw);
			outStrm.writeInt(desiredOpp);
			outStrm.writeInt(desiredSize);
						
			//Wait for response
			int rep = inStrm.readInt();
			//If ok, we are ready to go
			if ((rep == SIT_FIRST)  || (rep == SIT_SECOND)){
			//Otherwise report error
			} else if (rep < 0) {
				connMsg = inStrm.readUTF();
				System.err.println("Closing GameSocket for INVALID or NOSIT: " + connMsg);
				s.close();
				inStrm = null;
				outStrm = null;
				s = null;
			} else {
				throw new IOException("Unexpected answer!");
			}
			return rep;
			
		//If communication fails
		} catch (IOException e) {
			System.err.println("Closing GameSocket for COMFAIL: " + e.toString());
			inStrm = null;
			outStrm = null;
			s = null;
			return COMFAIL;
		}
	}
	
	
	/** Indicates whether there is a message to read, such as a move, or game results. */
	public boolean isMessage() throws IOException {
		if (inStrm != null) {
			return (inStrm.available() > 0) ? true : false;
		} else {return false;}
	}
	
	/** Reading the message, message has up to 5 integers:
	 * The first integer is a code, any of:
	 * 		 CLOSING (for whatever reason)
	 * 		 PLEASE_PLAY (indicating you should call sendMove())
	 *       YOUR_RESULT (the second integer indicates how much point you did, or INVALID_MOVE)
	 *       OPP_RESULT (the next integers, in order will be the opponent moves information:
	 *                   HORZ (0) or VERT (1), row, column, points made)
	 *       GAME_OVER (the game is completed, next two integers are player 1 and player 2 scores respectively)
	 * Note that if it is your turn to play, you should call sendMove first
	 */
	public int[] readMessage() throws IOException {
		if (!myTurn) {
			int[] msg = new int[5];
			msg[0] = inStrm.readInt(); //Read message code
			switch (msg[0]) {
				case CLOSING:
					System.out.println("CLOSING");
					break;
				case PLEASE_PLAY:
					myTurn = true;
					System.out.println("PLEASE PLAY");
					break;
				case YOUR_RESULT:
					msg[1] = inStrm.readInt(); //Read result: INVALID_MOVE or some pts
					System.out.println("YOUR RESULT = " + Integer.toString(msg[1]));
					break;
				case OPP_RESULT:
					for (int i=1; i<=4; i++ ) {
						msg[i] = inStrm.readInt(); //Read 3-byte move and 1 byte pts
					}
					System.out.println("OPP RESULT = " + Integer.toString(msg[1]) 
							                    + " "  + Integer.toString(msg[2])
							                    + " "  + Integer.toString(msg[3])
							                    + " "  + Integer.toString(msg[4]));
					break;
				case GAME_OVER:
					msg[1] = inStrm.readInt(); //Read player 1 score
					msg[2] = inStrm.readInt(); //Read player 2 score
					System.out.println("GAME OVER = " + Integer.toString(msg[1])
							                 + " -- " + Integer.toString(msg[2]));
					break;
			}
			return msg;
		} else {
			throw new IOException("Your turn to play!");
		}
	}
	
	/** Allows you to send your move to the server, if it is your turn
	 *  You should send in order, whether it is HORZ (0) or VERT (1), the row, and the column.
	 *  Note that if it is not your turn, you should call readMessage first.
	 */
	public void sendMove(int HorV, int row, int col) throws IOException {
		if (myTurn) {
			System.out.println("PLAYING = " + Integer.toString(HorV)
	                 				  + " " + Integer.toString(row)
	                 				 + " " + Integer.toString(col));
			outStrm.writeInt(HorV);
			outStrm.writeInt(row);
			outStrm.writeInt(col);
			myTurn = false;
		} else {
			throw new IOException("Not your turn to play!");
		}
		
	}
	
	
	/** Indicates whether or not it time to send a move */
	public boolean isMyTurn() {
		return myTurn;
	}
	
	/** Close the socket connection and therefore, ends the game */
	public void close() {
		//Close socket as smoothly as possible
		if (s != null) {
			try {
				s.close();
			} catch (IOException e) {}
		}
		inStrm = null;
		outStrm = null;
		s = null;
	}
	
	
}

