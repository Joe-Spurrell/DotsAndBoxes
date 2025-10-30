
public class Demo {

	final static String ServerIP1 = "localhost";
	final static String ServerIP2 = "x.x.x.x";
	
	final static int myAIID = 1700;
	final static int myTable = 17;
	final static int myPW = 1751;
	final static int size = 4; //Play on a 4 boxes per 4 boxes
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		
		GameSocket gs = new GameSocket();
		
		//Connect
		int res = gs.connect(ServerIP1, myAIID, myTable, myPW, GameSocket.RANDOM_BOT_SECOND, size);
		
		//Read result, if fail, display error message
		if (res < 0) {
			System.out.println(gs.connMsg);
			return;
		}
		
		//first message should be PLEASE_PLAY since bot is second
		int[] msg = gs.readMessage(); 
		if (msg[0] != GameSocket.PLEASE_PLAY) {
			throw new Exception("NOT MY TURN?");
		}
		
		//While the game is active
		while (!((msg[0] == GameSocket.CLOSING) || (msg[0] == GameSocket.GAME_OVER))) {
			
			do {
				//play randomly
				int HorV, row, col;
				HorV = Math.random() < 0.5 ? 0 : 1;
				if (HorV == 0) { //Play horizontal Horizontal
					row = (int) (Math.random()*(size+1));
					col = (int) (Math.random()*(size));
				} else { //Play vertical
					row = (int) (Math.random()*(size));
					col = (int) (Math.random()*(size+1));
				}
				//Send move
				gs.sendMove(HorV, row, col);
				//Read result
				msg = gs.readMessage();
				//Read next message
				msg = gs.readMessage();
			} while (msg[0] == GameSocket.PLEASE_PLAY);
				
			//Then wait for my turn
			while (!((msg[0] == GameSocket.PLEASE_PLAY) || (msg[0] == GameSocket.CLOSING) || (msg[0] == GameSocket.GAME_OVER))) {
				msg = gs.readMessage();
			}
			
		}
		

	}

}
