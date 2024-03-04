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


        while (!terminate) {

            if(frozen){
                try{
                    long sleepTime = unfreezeTime - System.currentTimeMillis();
                    Thread.sleep(sleepTime);
                } catch(InterruptedException ignored){}
                finally{
                    frozen = false;
                    env.ui.setFreeze(id , Table.INIT_INDEX);
                }
            }
            else{

                synchronized(table.tableLock){
                    if( !keyPressedQueue.isEmpty() ){

                        try{
                            int slot = keyPressedQueue.take();

                            if( table.playerHasTokenInSlot(id , slot) )
                                table.removeToken(id, slot);    

                            else if(table.getPlayerNumOfTokens(id) < 3) {                    
                                table.placeToken(id, slot);

                                if(table.getPlayerNumOfTokens(id) == 3){
                                        table.lockTable();
                                        dealer.notifyDealerAboutSet(id);
                                        //WAITS FOR DEALER
                                        while(table.tableIsLocked)
                                            table.tableLock.wait();
                                }
                            }
                        } catch(InterruptedException ignored){}
                    }
                }
            }
        }

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

            while (!terminate) {
                keyPressed( (int)(Math.random() * (env.config.tableSize)) );
                try{
                    Thread.sleep(env.config.pointFreezeMillis);
                }
                catch(InterruptedException ignored){}
            }

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
        //Check if table is locked or player is locked, and card exists.
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

            table.lockTable();
            frozen = true;
            unfreezeTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
        
            int ignored = table.countCards(); // this part is just for demonstration in the unit tests
            env.ui.setScore(id, ++score);
            env.ui.setFreeze(id, env.config.pointFreezeMillis);
    
            table.removeAllTokensByPlayer(id);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        
        unfreezeTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
        frozen = true;

        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);

        if(!human)
            table.removeAllTokensByPlayer(id);
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

    // /**
    //  *  Waits if dealer is dealing cards or removing cards right now
    //  */
    // public void waitForDealer()  {
    //     synchronized()
    //     try {
    //         while (dealer.playerRequireCheck != null) {
    //                 playerLock.wait();
    //             }
    //         }
    //     } catch(InterruptedException ignored) {}
    // }
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
}
