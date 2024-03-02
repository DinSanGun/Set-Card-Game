package bguspl.set.ex;

import bguspl.set.Env;

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
     * Mapping between a slot and player to the tokens placed on by the player.
     */
    protected final Boolean[][] slotPlayerToToken; // slot per card (if any)
    /**
     * Mapping between a player and the number of tokens placed on the table by the player.
     */
    protected final int[] playerToNumOfTokens; // slot per card (if any)
    /**
     * Marks if a player needs a dealer check (-1 if none).
     */
    protected final boolean[] playerRequireDealerCheck; // slot per card (if any)
    /**
     * An object used for synchronizing.
     */
    protected final Object lock; // slot per card (if any)


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
        playerToNumOfTokens = new int[env.config.players];
        playerRequireDealerCheck = new boolean[env.config.players];

        Arrays.fill(playerToNumOfTokens , INIT_INDEX);
        Arrays.fill(playerRequireDealerCheck , false);

        for(Boolean[] slot : slotPlayerToToken)
            Arrays.fill(slot , false);

        lock = new Object();

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

        if(slotToCard[slot] != null && playerToNumOfTokens[player] < 3){

            synchronized(lock){

                slotPlayerToToken[slot][player] = true;
                playerToNumOfTokens[player]++;

                env.ui.placeToken(player, slot);

                    if( playerToNumOfTokens[player] == 3 )
                        playerRequireDealerCheck[player] = true;
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
        if(slotToCard[slot] != null){
            slotPlayerToToken[slot][player] = false;
            playerToNumOfTokens[player]--;
            env.ui.removeToken(player, slot);
            return true;
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
        for (int i = INIT_INDEX; i < slotToCard.length; i++)
            if (slotToCard[i] == null)
                nullSlots.add(i);

        return nullSlots;
    }

    /**
     * @return - an array of cards representing a player's set
     * @return - null if the player's tokens is less than 3.
     */
    public int[] getPlayerCards(int player){
        int[] cards = new int[env.config.featureSize];
        int index = INIT_INDEX;

        for(int slot = INIT_INDEX; slot < env.config.tableSize; slot++)
            if(slotPlayerToToken != null && slotPlayerToToken[slot][player])
                cards[index++] = slotToCard[slot];
        
        if(index != 3)
            return null;

        return cards;
    }

    /**
     * Removes all the tokens that have been placed by the specified player
     * @param - the player's ID.
     * @post - the player's tokens are removed from the table
     */
    public void removeAllTokensByPlayer(int player){
        for(int i = INIT_INDEX; i < env.config.tableSize; i++)
            slotPlayerToToken[i][player] = false;
        playerToNumOfTokens[player] = 0;
    }

}
