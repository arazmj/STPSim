package net.spanningtree;

import java.util.HashMap;

/**
 * Created by Amir Razmjou
 * Fitzroy Nembhard on 11/26/14.
 */
class Bridge extends Element {
    private States state = States.ROOT_ADVERTISE;
    private int rootId = id;
    private int costToRoot = Integer.MAX_VALUE;
    private int rootPort;
    private int designatedPort;
    private LanSegment designatedBridge;
    private LanSegment designatedPortNetwork;
    private boolean isRoot = true;
    private long time = 0;
    final HashMap<Integer, Integer> portCostToRoot = new HashMap<>();
    private enum PortStatus { RP, DP, BLOCKED}
    final HashMap<Integer,PortStatus> portStates = new HashMap<>();
    public enum States {ROOT_ADVERTISE, RP_ELECTION, DP_ELECTION, ROOT_LISTEN, DP_LISTEN, SHUTDOWN}



    public Bridge(int id) {
        super(id);
    }

    @Override
    String getAbbreviation() {
        return "B";
    }

    @Override
    void tick() {
        time++;

        final int ROOT_ELECT_TIME_OUT = 100;
        final int RP_ELECT_TIME_OUT = ROOT_ELECT_TIME_OUT + 100;
        final int DP_ELECT_TIME_OUT = RP_ELECT_TIME_OUT;
        final int DP_LISTEN_TIME_OUT = DP_ELECT_TIME_OUT + 100;


        BridgeFrame frame;
        //simple sequential state machine
        switch (state) {
            case ROOT_ADVERTISE:
                // for every neighbor advertise yourself as root with your own root id
                for (Element neighbor : ports.values()) {
                    neighbor.enqueue(this, new BridgeFrame(this, getId(), rootId,
                            States.ROOT_ADVERTISE));
                }
                System.out.println(getId() + " QS:" + frameQueue.size() +
                        " Advertising R: " + rootId + " to " + ports.values());

                // switch ino listen state
                state = States.ROOT_LISTEN;
                break;
            case ROOT_LISTEN:
                if ((frame = frameQueue.peek()) != null) {
                    if (frame.getMode() == States.ROOT_ADVERTISE) {
                        frameQueue.remove(frame);
                        // change your root id if you received root id less than yours
                        if (frame.getMessage() < rootId) {

                            rootId = frame.getMessage();
                            rootPort = ports.inverse().get(frame.getSender());
                            isRoot = false;
                            System.out.println(getId() + " QS:" + frameQueue.size() +
                                    " Changing root ID to: " + rootId);
                            state = States.ROOT_ADVERTISE;
                        }
                    }
                }

                if (time > ROOT_ELECT_TIME_OUT) {
                    // all ports of root must remain dp
                    if (!isRoot)
                       portStates.put(rootPort, PortStatus.RP);
                    state = States.RP_ELECTION;
                }

                break;

            case RP_ELECTION:
                // for roots only
                if (isRoot) {
                    //  System.out.println(getId() + " I AM ROOT");
                    for (Element neighbor : ports.values()) {
                        neighbor.enqueue(this, new BridgeFrame(this, getId(), rootId,
                                States.RP_ELECTION));
                    }
                    costToRoot = 0;
                    // make all ports of root DP
                    for (Integer port : ports.keySet()) {
                        portStates.put(port, PortStatus.DP);
                    }
                } else {
                    if ((frame = frameQueue.peek()) != null
                            && frame.getMode() == States.RP_ELECTION) {

                        frameQueue.remove(frame);

                        int receivedPort = ports.inverse().get(frame.getSender());

                        if (frame.getMode() == States.RP_ELECTION) {
                            if (frame.getCost() < costToRoot) {
                                costToRoot = frame.getCost();
                                rootPort = receivedPort;
                                designatedBridge = (LanSegment) frame.getSender();

                                System.out.println(getId() + ": cost to root is " + costToRoot + " from " + rootPort + " " + designatedBridge);
                                for (Integer port : ports.keySet()) {
                                    if (port == receivedPort)
                                        continue;

                                    final BridgeFrame clone = frame.clone(this);
                                    clone.incrementCost();
                                    ports.get(port).enqueue(this, clone);
                                }

                            }
                        }
                    }
                }

                if (time > RP_ELECT_TIME_OUT) {
                    state = States.DP_ELECTION;
                }

                break;

            case DP_ELECTION:
                // port to "cost to root"
                for (Element neighbor : ports.values()) {
                    if (neighbor == designatedBridge)
                        continue;

                    int  portId = ports.inverse().get(neighbor);
                    neighbor.enqueue(this, new BridgeFrame(this, getId(), costToRoot,
                            States.DP_ELECTION, portId));

                    System.out.println(getId() + " advertising DP with cost " + costToRoot + " to " + neighbor);

                    portCostToRoot.put(portId, costToRoot);
                }

                //if (time > DP_ELECT_TIME_OUT)
                    state = States.DP_LISTEN;

                //System.out.println("I'm " + getId() + " my cost is " + costToRoot);
                break;

            case DP_LISTEN:
                if ((frame = frameQueue.peek()) != null) {
                    if (frame.getMode() == States.DP_ELECTION) {
                        int receivedPort = ports.inverse().get(frame.getSender());
                        Integer myCost = portCostToRoot.get(receivedPort);
                        final int newCost = frame.getMessage();
                        final int tieBreaker = frame.getTieBreaker();

                        if (myCost == null) {
                            myCost = costToRoot;
                        }

                        System.out.println(getId() + " got cost of " + newCost + " on port " + receivedPort + " but I already got " + myCost + " his portId is " + frame.getTieBreaker());

                            if (newCost < myCost)  {
                                portStates.put(receivedPort, PortStatus.BLOCKED);

                            }
                    }
                }


                // if we didn't hear from a lan segment it means we are the
                // only bridge connecting him to rest of network
                for (Integer port : ports.keySet()) {
                    if (!portStates.keySet().contains(port))
                        portStates.put(port, PortStatus.DP);
                }

                //   if (time > DP_LISTEN_TIME_OUT)
                state = States.SHUTDOWN;
            break;

            case SHUTDOWN:
                System.out.println(this + " " + portStates);
                stop();
                break;

            default:
                throw new RuntimeException("Unknown bridge state " + state);
        }
    }

}
