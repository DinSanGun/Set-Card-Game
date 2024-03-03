package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.ArrayList;
import java.util.Arrays;
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
    /**
     * An object for synchronization
     */
    protected Object dealerLock;
    /**
     * A variable indicating if the dealer is dealing right now
     */
    protected boolean dealing;
    /**
     * A variable indicating if the dealer is dealing right now
     */
    private Integer playerRequireCheck;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;

        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        terminate = false;

        dealerLock = new Object();
        dealing = false;
        playerRequireCheck = null;
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
            updateTimerDisplay(true);
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

        terminate = true;
        for(Player player : players)
            player.terminate();
        
        Thread.currentThread().interrupt();
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
        dealing = true;

            if( playerRequireCheck != null ){

                int[] setToTest = table.getPlayerCards(playerRequireCheck);

                if(setToTest != null){

                    if( env.util.testSet(setToTest) ){

                        players[playerRequireCheck].point();
                        for(int card : setToTest)
                            table.removeCard(card);
                    }
                    else
                        players[playerRequireCheck].penalty();
                }

                playerRequireCheck = null;
            }
        dealing = false;
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {

        synchronized(dealerLock){

            int numOfCardsOnTheTable = table.countCards();

            if(deck.size() - numOfCardsOnTheTable < env.config.featureSize || shouldFinish() ) //Checking if game should terminate
                terminate(); 
            
            else {
                List<Integer> emptySlots = table.getEmptySlots();
    
                if(emptySlots.size() == env.config.tableSize && !deck.isEmpty())
                    shuffleDeck();
    
                for(Integer slot : emptySlots)
                    table.placeCard(deck.remove(Table.INIT_INDEX) , slot);
            }
            dealing = false;
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        synchronized(dealerLock){
            try{
                dealerLock.wait(100);
            }
            catch(InterruptedException ignored){}
        }
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
            if(time < env.config.turnTimeoutWarningMillis)
                env.ui.setCountdown(time , true);
            else
                env.ui.setCountdown(time , false);
        }

        for(Player player : players){
            if( player.isFrozen() ) 
                env.ui.setFreeze(player.id , player.unfreezeTime - System.currentTimeMillis());

        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized(table.tableLock){
            for(Player player : players){
                table.removeAllTokensByPlayer(player.id);
                player.clearKeyQueue();
            }
            for(int slot = table.INIT_INDEX; slot < env.config.tableSize; slot++){
                if(table.slotToCard[slot] != null){
                    deck.add(table.slotToCard[slot]);
                    table.removeCard(slot);
                }
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        List<Integer> winnersID = new ArrayList<Integer>();

        int max = table.INIT_INDEX;

        for(Player player : players)
            if(player.score() > max)
                max = player.score();

        for(Player player : players)
            if(player.score() == max)
                winnersID.add(player.id);
        
        int[] winnersArray = new int[winnersID.size()];
        for(int i = table.INIT_INDEX; i < winnersID.size(); i++)
            winnersArray[i] = winnersID.remove(Table.INIT_INDEX);

        env.ui.announceWinner(winnersArray);
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
     * Wakes the dealer thread up
     */
    public void awake(){
        dealerThread.interrupt();
    }

    /**
     * Wakes the dealer thread up
     */
    public void notifyDealerAboutSet(int player){
        playerRequireCheck = player;
        dealerThread.interrupt();
    }
}
