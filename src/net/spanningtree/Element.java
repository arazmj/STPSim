package net.spanningtree;


import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Created by Amir Razmjou
 * Fitzroy Nembhard on 11/26/14.
 */
public abstract class Element {
    private static final Random random = new Random(Configurations.seedNetwork);
    // the only static value shared among classes
    // this take cares of redundant portIds (MACs)
    private static int lastPortId = 1;
    final int id;
    final BiMap<Integer, Element> ports = new HashBiMap<>();

    // incoming FIFO queue must by concurrent
    final Queue<BridgeFrame> frameQueue = new ConcurrentLinkedDeque<>();
    private boolean isAlive = true;
    private Timer timer;

    Element(int id) {
        this.id = id;
    }

    void enqueue(Element sender, BridgeFrame frame) {
        frameQueue.offer(frame);
    }

    public void addPort(Element element) {
        ports.put(lastPortId++, element);
    }

    @Override
    public String toString() {
        String s = "[" + getId() + "] ";
        for (Element element : ports.values()) {
            s += element.getId() + " ";
        }
        return s;
    }

    abstract String getAbbreviation();

    public String getId() {
        return getAbbreviation() + id;
    }

    abstract void tick();

    public void start() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                tick();
            }
        };
        timer = new Timer("Timer");
        int period = 20;

        // desynchronization tasks starting time
        int delay = random.nextInt(period);
        timer.scheduleAtFixedRate(timerTask, delay, period);
    }

    protected void stop() {
        timer.cancel();
        isAlive = false;
    }

    public boolean isAlive() {
        return isAlive;
    }
}
