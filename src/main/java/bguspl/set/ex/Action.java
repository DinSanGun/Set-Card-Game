package bguspl.set.ex;

public class Action {

    /**
     * The slot of the card to put or remove token from.
     */
    private int slot;

    /**
     * Defines if the action is placing a token or removing it.
     * Placing token -> true
     * Removing token -> false
     */
    private boolean placingToken;

    public Action(int slot, boolean placingToken){

        this.slot = slot;
        this.placingToken = placingToken;
    }

    public int getSlot(){
        return slot;
    }
    
    public boolean placingToken(){
        return placingToken;
    }
}
