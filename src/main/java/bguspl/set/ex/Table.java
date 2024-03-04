package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    public static final int INIT_INDEX = 0; //The first index to consider when iterating.
    public static final int NOT_EXIST = -1;
    public static final int SECOND_IN_MILLIS = 1000;
    public static final int DELAY_IN_MILLIS = 100;

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)


    //===========================================================
    //                  added by us
    //===========================================================

    /**
     * Mapping between a player and the tokens the player has placed
     */
    protected final List<List<Integer>> playerToTokens;
    /**
     * an object used for locking on the table (for synchronization)
     */
    protected Object tableLock;
    /**
     * an object used for locking on the table (for synchronization)
     */
    protected volatile boolean tableIsLocked;


    //===========================================================
    //                  up until here
    //===========================================================

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;

        tableLock = new Object();
        tableIsLocked = false;

        synchronized(tableLock){
            playerToTokens = new ArrayList<List<Integer>>();

            for(int i = INIT_INDEX; i < env.config.players; i++)
                playerToTokens.add(new LinkedList<Integer>());
        }
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);

            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */

    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        int cardToRemove = slotToCard[slot];

        slotToCard[slot] = null;
        cardToSlot[cardToRemove] = null;

        synchronized(tableLock){

            Iterator<List<Integer>> iter = playerToTokens.iterator();
            int player = INIT_INDEX;

            while(iter.hasNext()){
                List<Integer> playerTokens = playerToTokens.get(player);
                int indexOfToken = playerTokens.indexOf(slot);
                if(indexOfToken != NOT_EXIST){
                    playerTokens.remove(indexOfToken);
                    env.ui.removeToken(player, slot);
                }
                player++;
            }

            // for(int player = INIT_INDEX; player < playerToTokens.size(); player++){

            //     List<Integer> playerTokens = playerToTokens.get(player);
            //     int indexOfToken = playerTokens.indexOf(slot);
            //     if(indexOfToken != NOT_EXIST){
            //         playerTokens.remove(indexOfToken);
            //         env.ui.removeToken(player, slot);
            //     }
            // }
        }

        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {

        synchronized(tableLock){

            List<Integer> playerTokens = playerToTokens.get(player);

            if(slotToCard[slot] != null && playerTokens.size() < 3){

                    playerTokens.add(slot);
                    env.ui.placeToken(player, slot);
            }
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {

        synchronized(tableLock){

            List<Integer> playerTokens = playerToTokens.get(player);
            if(slotToCard[slot] != null){

                int indexOfToken = playerTokens.indexOf(slot);

                if(indexOfToken != NOT_EXIST){

                    playerTokens.remove(indexOfToken);
                    env.ui.removeToken(player, slot);
                    return true;
                }
            }
            return false;
        }
    }


    //===========================================================
    //                  added by us 
    //===========================================================

    /**
     * @return - a list of integers, representing the indexes of all the empty slots in the table.
     */
    public List<Integer> getEmptySlots(){

        List<Integer> nullSlots = new LinkedList<Integer>();
        for (int slot = INIT_INDEX; slot < slotToCard.length; slot++)
            if (slotToCard[slot] == null)
                nullSlots.add(slot);

        return nullSlots;
    }

    /**
     * @return - an array of cards representing the player's tokens
     */
    public int[] getPlayerCards(int player){

        synchronized(tableLock){
            List<Integer> playerTokensList = playerToTokens.get(player);
            int[] playerCards = new int[playerTokensList.size()];
            int slot;
            
            Iterator<Integer> iter = playerTokensList.iterator();
            int index = INIT_INDEX;

            while(iter.hasNext()){
                slot = iter.next();
                playerCards[index++] = slotToCard[slot];
            }

            // for( int i = INIT_INDEX; i < playerTokensList.size(); i++){
            //     slot = playerTokensList.get(i);
            //     playerCards[i] = slotToCard[slot];
            // }

            return playerCards;
        }
    }

    /**
     * Removes all the tokens that have been placed by the specified player
     * @param - the player's ID.
     * @post - the player's tokens are removed from the table
     */
    public void removeAllTokensByPlayer(int player){

        synchronized(tableLock){
            List<Integer> playerTokens = playerToTokens.get(player);
            Iterator<Integer> iter = playerTokens.iterator();

            while(iter.hasNext())
                removeToken(player, iter.next());
        }
    }

    /**
     * @return - true iff the player corresponding the the parameter 'player' has a token in the slot 'slot'
     */
    public int getPlayerNumOfTokens(int player){

            return playerToTokens.get(player).size();
    }

    /**
     * locks the table from actions
     */
    public void lockTable(){

        tableIsLocked = true;
    }

    /**
     * releases the table lock
     */
    public void releaseTable(){

        tableIsLocked = false;
    }
    
    /**
     * @return - true iff the player corresponding the the parameter 'player' has a token in the slot 'slot'
     */
    public boolean playerHasTokenInSlot(int player, int slot){

            return playerToTokens.get(player).contains(slot);
    }
}
