package bguspl.set.ex;

import bguspl.set.Env;

//------------------ our imports -------------------------------
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
//--------------------------------------------------------------

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    //===========================================================
    //                  added by us
    //===========================================================

     /**
     * The dealer that manages the player's game. -> we added that 
     */
    private final Dealer dealer;

     /**
     * Holds the actions made by the key presses of the player (last 3)
     */
    private BlockingQueue<Integer> keyPressedQueue; 

    /**
     * A variable indicating if a current player is frozen (because of point() or penalty())
     */
    protected volatile boolean frozen; 
    /**
     * A variable indicating the system time (in millis) when the player should get unfrozen.
     */
    protected long unfreezeTime; 

    //===========================================================
    //                  up until here
    //===========================================================

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;

        keyPressedQueue = new ArrayBlockingQueue<Integer>( env.config.featureSize );
        terminate = false;
        frozen = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {

        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        
        if (!human)
            createArtificialIntelligence();

        while (!terminate) { //Main loop of player

            if(frozen)
                playerIsFrozen();
            else
                playerMakeAction(); 
        }

        //Game is finished
        if (!human) {
            try { 
                aiThread.join(); 
            } catch (InterruptedException ignored) {}
        }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {

        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {

            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

            while (!terminate) //Simulates keypresses
                keyPressed( (int)(Math.random() * (env.config.tableSize)) );

            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");

        }, "computer-" + id);

        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        //Check if table and player are unlocked, and card exists in the slot pressed.
        if( !table.tableIsLocked && !frozen && table.slotToCard[slot] != null )
            keyPressedQueue.offer(slot);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {

            frozen = true;
            unfreezeTime = System.currentTimeMillis() + env.config.pointFreezeMillis;
        
            int ignored = table.countCards(); // this part is just for demonstration in the unit tests
            env.ui.setScore(id, ++score);
    
            table.removeAllTokensByPlayer(id);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        
        unfreezeTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
        frozen = true;
    }

    /**
     * @return Current's player score.
     */
    public int score() {
        return score;
    }


//===========================================================
//                  added by us 
//===========================================================

    /**
     * @post - the key pressed queue is empty
     */
    public void clearKeyQueue(){
        keyPressedQueue.clear();
    }

    /**
     *@return - true iff the player is frozen right now
     */
    public boolean isFrozen()  {
        return frozen;
    }

    /**
     *Implements player's behaviour when frozen - sleep then awake and update the UI
     */
    public void playerIsFrozen(){

        try{ //Sleep for the appropriate freeze time
            long sleepTime = unfreezeTime - System.currentTimeMillis();
            if(sleepTime > Table.INIT_INDEX)
                Thread.sleep(sleepTime);
        } catch(InterruptedException ignored){}
        finally { //Unfreezes and updates UI
            frozen = false;
            env.ui.setFreeze(id , Table.INIT_INDEX);
            if(!human)
                table.removeAllTokensByPlayer(id);
        }
    }

    /**
     *Implements player's behaviour upon regular turn - 
     * place/remove tokens, notify dealer when needed
     */
    public void playerMakeAction() {
        
        synchronized(table.tableLock){

            if( !keyPressedQueue.isEmpty() ){

                try{
                    int slot = keyPressedQueue.take();

                    if( table.playerHasTokenInSlot(id , slot) )//If player has token in the slot - removes it
                        table.removeToken(id, slot);    

                    else if(table.getPlayerNumOfTokens(id) < 3) { //If player can place a token - places it                 
                        table.placeToken(id, slot);

                        if(table.getPlayerNumOfTokens(id) == 3){ //If player has placed 3 tokens - 
                                table.lockTable();                // notifies dealer and waits for response
                                dealer.notifyDealerAboutSet(id);
                                //waits for dealer to check the set
                                while(table.tableIsLocked)
                                    table.tableLock.wait();
                        }
                    }
                } catch(InterruptedException ignored){}
            }
        }
    }
}
