package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.ArrayList;
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
     * An object for synchronizing a dealer sleep in sleepUntilWokenOrTimeout
     */
    protected Object dealerLock;

    /**
     * A variable indicating if there is a player that submitted a set for the dealer to check.
     */
    private Integer playerRequireCheck;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;

        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        terminate = false;

        dealerLock = new Object();
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

        //Dealer's main loop
        while (!shouldFinish()) { 
            placeCardsOnTable();
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
        synchronized(table.tableLock){
            //Locking the table from keypresses - 
            // releases it after new cards are dealt to replace the removed ones
            table.lockTable(); 

            if( playerRequireCheck != null ){

                int[] setToTest = table.getPlayerCards(playerRequireCheck);
                testPlayerSet(setToTest);
            }

            table.tableLock.notify(); //Wakes up the thread that is waiting for the dealer's response
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {

        synchronized(table.tableLock){
            table.lockTable();

            int numOfCardsOnTheTable = table.countCards();

            //Checking if game should terminate - no more valid sets available
            if(deck.size() + numOfCardsOnTheTable < env.config.featureSize || shouldFinish() )
                terminate(); 
            
            else {
                List<Integer> emptySlots = table.getEmptySlots();
    
                //If dealer replaces all cards on the table - shuffles the deck beforehand
                if(emptySlots.size() == env.config.tableSize && !deck.isEmpty())
                    shuffleDeck();
    
                for(Integer slot : emptySlots)
                    table.placeCard(deck.remove(Table.INIT_INDEX) , slot);
                
                if(!emptySlots.isEmpty()) //Resetting the timer when cards are dealt to the table
                    updateTimerDisplay(true);
            }

            //Checking if a valid set exists on the table - if not replacing all the cards
            List<Integer> cardsOnTable = new ArrayList<Integer>();

            for(int slot = Table.INIT_INDEX; slot < env.config.tableSize; slot++)
                if(table.slotToCard[slot] != null)
                    cardsOnTable.add(table.slotToCard[slot]);

            if( env.util.findSets(cardsOnTable, 1).size() == 0 )
                removeAllCardsFromTable();

            table.releaseTable();
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
            boolean timerShouldBeWarning = time < env.config.turnTimeoutWarningMillis;
            env.ui.setCountdown(time , timerShouldBeWarning);
        }

        //Updating frozen players' countdown
        for(Player player : players){
            if( player.isFrozen() ) {

                long timeUntilUnfrozen = player.unfreezeTime - System.currentTimeMillis();

                if(timeUntilUnfrozen > 0)
                    env.ui.setFreeze(player.id , timeUntilUnfrozen + Table.SECOND_IN_MILLIS); 
                    //Adding 1 second for display delay issues
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {

        synchronized(table.tableLock){
            table.lockTable();

            for(Player player : players)
                player.clearKeyQueue();

            for(int slot = Table.INIT_INDEX; slot < env.config.tableSize; slot++){
                if(table.slotToCard[slot] != null){
                    deck.add(table.slotToCard[slot]);
                    table.removeCard(slot);
                }
            }
            table.releaseTable();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {

        int max = Table.INIT_INDEX;
        int numOfWinners = 0;

        for(Player player : players) //Finds the maximum score
            if(player.score() > max)
                max = player.score();

        for(Player player : players) //Finds how many players has the maximum score
            if(player.score() == max)
                numOfWinners++;
        
        int[] winnersArray = new int[numOfWinners];
        int index = Table.INIT_INDEX;

        for(Player player : players) //Collects the players' id of the winners
            if(player.score() == max)
                winnersArray[index] = player.id;

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
     * If valid - award the player a point.
     * If not valid - penalize the player.
     */
    public void testPlayerSet(int[] setToTest) {

        if(setToTest != null){

            if( env.util.testSet(setToTest) ){ //Set is valid

                players[playerRequireCheck].point();

                for(int card : setToTest)
                    table.removeCard( table.cardToSlot[card] );
                
                updateTimerDisplay(true); //Resets the timer after valid set is found
            }
            else //Set is not valid
                players[playerRequireCheck].penalty();
        }
        playerRequireCheck = null;
    }

    /**
     * Wakes the dealer thread up when a player is waiting for it's response.
     */
    public void notifyDealerAboutSet(int player){
        playerRequireCheck = player;
        dealerThread.interrupt();
    }
}