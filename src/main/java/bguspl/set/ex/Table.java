package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
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
    protected volatile Boolean tableLocked;


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

        tableLocked = false;

        playerToTokens = new ArrayList<List<Integer>>();

        for(int i = INIT_INDEX; i < env.config.players; i++)
            playerToTokens.add(new LinkedList<Integer>());
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

        for(List<Integer> playerTokens : playerToTokens){
            int indexOfToken = playerTokens.indexOf(slot);
            if(indexOfToken != NOT_EXIST)
                playerTokens.remove(indexOfToken);
        }

        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {

        List<Integer> playerTokens = playerToTokens.get(player);

        if(slotToCard[slot] != null && playerTokens.size() < 3){

                playerTokens.add(slot);
                env.ui.placeToken(player, slot);
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {

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

        List<Integer> playerTokensList = playerToTokens.get(player);
        int[] playerCards = new int[playerTokensList.size()];
        
        for( int i = INIT_INDEX; i < playerTokensList.size(); i++)
            playerCards[i] = playerTokensList.get(i);

        return playerCards;
    }

    /**
     * Removes all the tokens that have been placed by the specified player
     * @param - the player's ID.
     * @post - the player's tokens are removed from the table
     */
    public void removeAllTokensByPlayer(int player){
        playerToTokens.get(player).clear();
    }
}
