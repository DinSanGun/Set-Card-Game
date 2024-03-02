package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

//------------------ our imports -------------------------------
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
//--------------------------------------------------------------

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    public static final int INIT_INDEX = 0; //The first index to consider when iterating.
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
     * Mapping between a slot and the tokens placed on by the players (-1 if none).
     */
    protected final Boolean[][] slotPlayerToToken; // slot per card (if any)
    /**
     * This queue will store the sets that need to be checked
     */
    protected BlockingQueue<Integer> queueOfSets;

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

        slotPlayerToToken = new Boolean[env.config.tableSize][env.config.players];
        queueOfSets = new LinkedBlockingQueue<Integer>();
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
        queueOfSets = new LinkedBlockingQueue<Integer>();
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
        for(int i = INIT_INDEX; i < env.config.players; i++)
        slotPlayerToToken[slot][i] = false;
        
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
        for(int i = INIT_INDEX; i < env.config.players; i++)
            slotPlayerToToken[slot][i] = null;
        
        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        if(slotToCard[slot] != null){
            slotPlayerToToken[slot][player] = true;
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
        if(slotToCard[slot] != null){
            slotPlayerToToken[slot][player] = false;
            env.ui.removeToken(player, slot);
            return true;
        }

        return false;
    }


    //===========================================================
    //                  added by us 
    //===========================================================

    /**
     * 
     * @return - a list of integers, representing the indexes of all the empty slots in the table.
     */

    public List<Integer> getEmptySlots(){

        List<Integer> nullSlots = new LinkedList<Integer>();
        for (int i = INIT_INDEX; i < slotToCard.length; i++)
            if (slotToCard[i] == null)
                nullSlots.add(i);

        return nullSlots;
    }

}
