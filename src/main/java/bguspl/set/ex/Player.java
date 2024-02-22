package bguspl.set.ex;

import bguspl.set.Env;

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

     /**
     * The dealer that manages the player's game.
     */
    private final Dealer dealer;

    /**
     * The number of tokens currently placed on the table by this player.
     */
    private int tokensPlaced;

     /**
     * A queue holding the player's card picks - which checks the set whenever 3 cards are chosen
     *          TODO: implement a queue for this task, which is fed by key presses
     */
    // private final SpecialQueue<Action> // the action can be removing a card;

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
        score = 0;
        tokensPlaced = 0;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            // REMOVE AN ACTION FROM THE QUEUE AND DISPATCH IT
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
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
                // TODO implement player key press simulator
                try {
                    synchronized (this) {
                         wait(); 
                    }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if(table.slotToTokens[slot][id] != null) {

            if(table.slotToTokens[slot][id] == false && tokensPlaced < 3) {

                table.placeToken(id, slot);
                tokensPlaced++;

                if(tokensPlaced == 3) {
                    int[] cards = new int[3];
                    int setIndex = 0;
                    for(int i = Table.INIT_INDEX; i < env.config.tableSize; i++)
                        if(table.slotToTokens[i][id])
                            cards[setIndex++] = table.slotToCard[i];
                    
                    dealer.testPlayerSet(id,cards);       
                }
            }
            else {
                boolean success = table.removeToken(id, slot);
                if(success)
                    tokensPlaced--;
            }
        }

        // INSERT ACTIONS INTO THE QUEUE
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

        removeAllTokensOfPlayer(id);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);

    }

    /**
     * @return Current's player score.
     */
    public int score() {
        return score;
    }

    /**
     * Removes all the tokens that have been placed by a specific player
     * @param - the player's ID.
     * @post - the player's tokens are removed from the table
     * @post - tokensPlace == 0
     */
    private void removeAllTokensOfPlayer(int player) {

        for(int i = Table.INIT_INDEX; i < env.config.tableSize; i++)
            table.slotToTokens[i][player] = false;
        tokensPlaced = 0;
    }
}
