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
     * The number of tokens currently placed on the table by this player so far. -> we added that
     */
    public int tokensPlaced; 

     /**
     * Holds the actions made by the key presses of the player (last 3)
     */
    private BlockingQueue<Action> actionQueue; 

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

        actionQueue = new ArrayBlockingQueue<Action>( env.config.featureSize );
        terminate = false;
        tokensPlaced = 0;
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

        Action actionToExecute;

        while (!terminate) {

            try{

                actionToExecute = actionQueue.take();
                if( actionToExecute.placingToken() )
                    playerPlaceToken( actionToExecute.getSlot() );
                else
                    playerRemoveToken( actionToExecute.getSlot() );

            }
            catch(InterruptedException e){}
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

            while (!terminate) 
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

        if(table.slotToCard[slot] != null) { //If a card exists in that slot

            if(table.slotPlayerToToken[slot][id] == false && tokensPlaced < 3) {

                Action newAction = new Action(slot, true);
                actionQueue.add( newAction );
            }
            else {
                Action newAction = new Action(slot, false);
                actionQueue.add( newAction );
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
    
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);

        env.ui.setFreeze(id, env.config.pointFreezeMillis);

        try{
            if( Thread.currentThread().getName().equals("player-" + id) )
                Thread.sleep(env.config.pointFreezeMillis);
        }
        catch(InterruptedException e){}

        removeAllTokensOfPlayer();

        env.ui.setFreeze(id, Table.INIT_INDEX);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {

        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);

        try{
            if( Thread.currentThread().getName().equals("player-" + id) )
                Thread.sleep(env.config.penaltyFreezeMillis);
        }
        catch(InterruptedException e){}

        env.ui.setFreeze(id, Table.INIT_INDEX);

        if(!human)
            removeAllTokensOfPlayer();
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
     * Removes all the tokens that have been placed by a specific player
     * @param - the player's ID.
     * @post - the player's tokens are removed from the table
     * @post - tokensPlace == 0
     */
    private void removeAllTokensOfPlayer() {

        for(int i = Table.INIT_INDEX; i < env.config.tableSize; i++)
            table.slotPlayerToToken[i][id] = false;
        tokensPlaced = 0;
    }


    /**
     * Handles placing a token by the player. Alerts dealer if 3 tokens are set.
     * @param - slot - slot to place the token.
     * @post - the token of the player is placed on the appropriate slot on the table
     * @post - tokensPlaced = tokensPlaced + 1
     */
    private void playerPlaceToken(int slot) {

        table.placeToken(id, slot);
        tokensPlaced++;

        if(tokensPlaced == 3) {
            int[] cards = new int[3];
            int setIndex = 0;
            for(int i = Table.INIT_INDEX; i < env.config.tableSize; i++)
                if(table.slotPlayerToToken[i][id])
                    cards[setIndex++] = table.slotToCard[i];
            
            dealer.testPlayerSet(id,cards);    
        }
    }

        /**
     * Handles removing a token by the player.
     * @param - slot - slot to remove the token
     * @post - the token of the player is removed from the appropriate slot on the table
     * @post - tokensPlaced = tokensPlaced - 1
     */
    private void playerRemoveToken(int slot) {

        boolean success = table.removeToken(id, slot);
        if(success)
            tokensPlaced--;
    }
}
