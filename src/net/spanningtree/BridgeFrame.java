package net.spanningtree;

/**
 * Created by Amir Razmjou
 * Fitzroy Nembhard on 11/27/14.
 */
public final class BridgeFrame implements Cloneable {
    // either rootId or "cost to root"
    private final int message;
    private final int tieBreaker;
    private final Bridge.States mode;
    private final Element sender;
    private final String source;
    private int cost;

    BridgeFrame(Element sender, String source, int message, Bridge.States mode) {
        this.sender = sender;
        this.source = source;
        this.message = message;
        this.mode = mode;
        this.cost = 1;
        tieBreaker = 0;
    }


    BridgeFrame(Element sender, String source, int message, Bridge.States mode, int tieBreaker) {
        this.sender = sender;
        this.source = source;
        this.message = message;
        this.mode = mode;
        this.cost = 0;
        this.tieBreaker = tieBreaker;
    }

    /***
     * copy constructor
     * @param sender
     * @param source
     * @param message
     * @param mode
     * @param cost
     * @param tieBreaker
     */
    private BridgeFrame(Element sender, String source, int message, Bridge.States mode, int cost, int tieBreaker) {
        this.sender = sender;
        this.source = source;
        this.message = message;
        this.mode = mode;
        this.cost = cost;
        this.tieBreaker = tieBreaker;
    }



    public BridgeFrame clone(Element newSender) {
        return new BridgeFrame(newSender, this.source, this.message, this.mode, this.cost, this.tieBreaker);
    }

    public int getMessage() {

        return message;
    }

    public Bridge.States getMode() {
        return mode;
    }

    @Override
    public String toString() {
        return "BridgeFrame{" +
                "message=" + message +
                ", tieBreaker=" + tieBreaker +
                ", mode=" + mode +
                ", sender=" + sender +
                ", source='" + source + '\'' +
                ", cost=" + cost +
                '}';
    }

    public Element getSender() {
        return sender;
    }


    public void incrementCost() {
        cost++;
    }

    int getCost() {
        return cost;
    }

    public int getTieBreaker() {
        return tieBreaker;
    }

}
