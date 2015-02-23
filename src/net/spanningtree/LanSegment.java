package net.spanningtree;


/**
 * Created by Amir Razmjou
 * Fitzroy Nembhard on 11/26/14.
 */
class LanSegment extends Element {
    public LanSegment(int id) {
        super(id);

    }

    @Override
    String getAbbreviation() {
        return "N";
    }

    /***
     * Simply acts as cables or hubs except that for sake of simplicity doesn't forward back
     * received frame to source
     */
    @Override
    void tick() {
        BridgeFrame frame;// = null;
        while ((frame = frameQueue.poll()) != null) {
            // broadcasting frame to all ports except the incoming one.
            for (Integer port : ports.keySet()) {
                if (frame.getSender().getId().contains(ports.get(port).getId()))
                    continue;
                ports.get(port).enqueue(this, frame.clone(this));
            }

        }

    }
}
