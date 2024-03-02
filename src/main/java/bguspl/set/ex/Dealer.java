package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Collections;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * The thread representing the current player.
     */
    private Thread dealerThread;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;

        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

        //Create and run Players' threads
        for(Player player : players) {
            ThreadLogger playerThreadWithLogger = new ThreadLogger(player, "player-" + player.id , env.logger);
            playerThreadWithLogger.startWithLog();
        }

        while (!shouldFinish()) { 

            placeCardsOnTable();
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + Table.SECOND_IN_MILLIS;
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        //TESTPLAYERSET HERE
        //add flags for every player that needs to check a set in slotPlayer to Tokens
        //Checks the table for all flags and checking all sets available
        for(int i = Table.INIT_INDEX; i < players.length; i++){

            if( table.playerRequireDealerCheck(i)){
                //TESTSET

                table.setPlayerNotRequireDealerCheck(i);
            }
        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {

        int numOfCardsOnTheTable = table.countCards();

        if(deck.size() - numOfCardsOnTheTable < env.config.featureSize && !shouldFinish() ) //Checking if game should terminate
            terminate(); //TODO - CHECK FOR AVAILABLE SETS FROM TABLE U DECK
        
        else {
            List<Integer> emptySlots = table.getEmptySlots();

            if(emptySlots.size() == env.config.tableSize && !deck.isEmpty())
                shuffleDeck();

            for(Integer slot : emptySlots)
                table.placeCard(deck.remove(Table.INIT_INDEX) , slot);
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try{
            Thread.sleep(Table.SECOND_IN_MILLIS);
        }
        catch(InterruptedException e){}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {

        if(reset){
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            env.ui.setCountdown( reshuffleTime - System.currentTimeMillis() , false);
        }
        else{
            long time = reshuffleTime - System.currentTimeMillis();
            if(time <= 3)
                env.ui.setCountdown(time , true);
            else
                env.ui.setCountdown(time , false);

        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
    }

    /**
     * Shuffles the deck.
     */
    private void shuffleDeck(){

        Collections.shuffle(deck);
    }

    /**
     * Tests if the player has assembled a valid set.
     * If valid set - award the player a point.
     * If not valid set - penalize the player.
     */
    public void testPlayerSet(int player, int[] cards) {

        if(env.util.testSet(cards)) {

            players[player].point();

            for(int card : cards)
                table.removeCard( table.cardToSlot[card] );

            placeCardsOnTable();
        }
        else
            players[player].penalty();
    }

    /**
     * Shuffles the deck.
     */
    public void awake(){

        dealerThread.interrupt();
    }


}
