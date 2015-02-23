package net.spanningtree;

import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.BronKerboschCliqueFinder;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.FloydWarshallShortestPaths;
import org.jgrapht.experimental.GraphTests;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;
import java.util.stream.Collectors;

class Main {
    public static void main(String[] args) {
        GetCliParameters getCliParameters = new GetCliParameters(args).invoke();
        if (getCliParameters.is()) return;
        Integer n = getCliParameters.getNods();
        Float d = getCliParameters.getDensity();

        UndirectedGraph<Integer, DefaultEdge> g = createConceptGraph(n, d);
        printConceptGraphInformation(g);

        // create all required bridges according to user input.
        final HashMap<Integer, Bridge> bridges = new HashMap<>();
        final HashSet<LanSegment> lanSegments = new HashSet<>();

        networkAdaptor(g, bridges, lanSegments);
        printNetworkGraph(bridges, lanSegments);
        if (Configurations.showCsv)
          printCsv(bridges, lanSegments);
        runDistributed(bridges, lanSegments);

    }

    /***
     * Runs LAN segments and Bridges in distributed mode using timers
     * @param bridges, keys represent port number, values bridges
     * @param lanSegments LAN Segments
     */
    private static void runDistributed(HashMap<Integer, Bridge> bridges, Set<LanSegment> lanSegments) {
        lanSegments.stream().forEach(Element::start);
        bridges.values().stream().forEach(Element::start);
        // wait until all bridges all done.
        while (bridges.values().stream().anyMatch(p -> p.isAlive()));
        lanSegments.stream().forEach(p -> p.stop());
    }

    /***
     * Prints information of networks and bridges
     * @param bridges bridges to print
     * @param lanSegments  lan segments to print
     */
    private static void printNetworkGraph(HashMap<Integer, Bridge> bridges, Set<LanSegment> lanSegments) {
        System.out.println("networks: " + lanSegments);
        System.out.println("bridges:  " + bridges.values());
    }

    /***
     * Concept diagram is the diagram we created using portability function
     * Here we take several tests:
     * 1. Random Graph is not tree and making sure we have loops for
     *    spanning tree algorithm.
     * 2. Graph diameter so we can make sure we have path costs more than 1
     * 3. Degree distribution so we make sure we have no bridge with less than two ports
     * 4. Connectivity to make sure we have single component network
     * @param g jGraphT graph
     */
    private static void printConceptGraphInformation(UndirectedGraph<Integer, DefaultEdge> g) {
        FloydWarshallShortestPaths floydWarshallShortestPaths = new FloydWarshallShortestPaths(g);
        ConnectivityInspector connectivityInspector = new ConnectivityInspector(g);

        if (GraphTests.isTree(g)) {
            System.out.println("WARNING: Generated graph is already a tree, try changing seed numbers or node numbers.");
        }

        if (!connectivityInspector.isGraphConnected()) {
            System.out.println("WARNING: Graph is not connected. , try changing seed numbers or node numbers.");
        }

        // calculate degree distribution
        Map<Integer, Long> degreeDistribution = g.vertexSet()
                .stream()
                .collect(Collectors.groupingBy(g::degreeOf,
                        Collectors.counting()));

        System.out.println("graph: " + g.toString());
        System.out.println("degree distribution: " + degreeDistribution);
        System.out.println("graph diameter: " + floydWarshallShortestPaths.getDiameter());
    }

    /***
     * Creates jGraphT concept diagram to be converted into network and LAN segments
     * It also take cares of bridges with less than 1 degree and bridges with no degree
     * @param nb number of bridges
     * @param d desired density between 0 and 1
     * @return
     */
    private static UndirectedGraph<Integer, DefaultEdge> createConceptGraph(int nb, double d) {
        UndirectedGraph<Integer, DefaultEdge> g =
                new SimpleGraph<>(DefaultEdge.class);

        for (int i = 0; i < nb; i++) {
            g.addVertex(i);
        }

        // creates graph with the density provided
        Set<Integer> nodes = g.vertexSet();
        float density = (float) d;
        Random random = new Random(2);
        for (int node1 : nodes) {
            for (int node2 : nodes) {
                if (node1 == node2 || random.nextFloat() >= density)
                    continue;
                g.addEdge(node1, node2);

            }
        }

        // check for nodes with degree less than 2
        boolean countSingleDegree = nodes.stream().filter(p -> g.degreeOf(p) < 2).count() > 0;
        if (countSingleDegree) {
            System.out.println("WARNING: Bridge with degree less than 2 found");
        }
        return g;
    }

    /***
     * Converts givens concept diagram into LAN segments and bridges
     * In order to have LAN segments with more than 2 degree surrounded by two bridges
     * We used Bron Kerbosch Clique finder to find cliques in network. By making each
     * Clique a single LAN Segment with created less LAN Segments without sacrificing
     * density of graph. Having LAN Segment with more than 2 degree looks more realistic
     * also.
     * @param g jGraphT concept diagram
     * @param bridges output parameter
     * @param lanSegments output parameter
     */
    private static void networkAdaptor(UndirectedGraph<Integer, DefaultEdge> g,
                                       HashMap<Integer, Bridge> bridges,
                                       Set<LanSegment> lanSegments) {
        for (Integer bridgeId : g.vertexSet()) {
            bridges.put(bridgeId, new Bridge(bridgeId));
        }

        BronKerboschCliqueFinder bronKerboschCliqueFinder = new BronKerboschCliqueFinder(g);

        // set of cliques
        Set<Set<Integer>> cliques = (Set<Set<Integer>>) bronKerboschCliqueFinder
                .getAllMaximalCliques()
                .stream()
                .map(m -> (Set<Integer>) m)
                .collect(Collectors.toSet());

        // create a network for each clique and connect all bridges in clique to that network
        int n = 0;
        for (Set<Integer> clique : cliques) {
            LanSegment lanSegment = new LanSegment(n++);
            lanSegments.add(lanSegment);
            for (Integer bridgeId : clique) {
                Bridge bridge = bridges.get(bridgeId);
                bridge.addPort(lanSegment);
                lanSegment.addPort(bridge);
                bridges.put(bridgeId, bridge);
            }
        }

        for (Bridge bridge : bridges.values()) {
            // it doesn't make sense to have bridge with a single lan connection
            if (bridge.ports.size() < 2) {
                final LanSegment lanSegment = new LanSegment(n++);
                bridge.addPort(lanSegment);
                lanSegment.addPort(bridge);
            }

            // taking care of bridge with no port
            if (bridge.ports.size() < 1) {
                bridge.addPort(lanSegments.stream().findFirst().get());
            }

        }
    }

    /***
     * Prints CSV version of network in case user wanted to import that to graph visualization apps
     * @param bridges
     * @param lanSegments
     */
    private static void printCsv(HashMap<Integer, Bridge> bridges, Set<LanSegment> lanSegments) {
        // print headers
        System.out.println("Source,Target,Type");
        // print lan segments
        for (LanSegment lanSegment : lanSegments) {
            for (Element element : lanSegment.ports.values()) {
                System.out.println(lanSegment.getId() + "," + element.getId() + ",\"LAN\"");
            }
        }

        // print bridges
        for (Bridge bridge : bridges.values()) {
            for (Element element : bridge.ports.values()) {
                System.out.println(bridge.getId() + "," + element.getId() + ",\"BRIDGE\"");
            }

        }
    }

    private static class GetCliParameters {
        private boolean myResult;
        private String[] args;
        private Integer n;
        private Float d;

        public GetCliParameters(String... args) {
            this.args = args;
        }

        private static void showHelp() {
            String help = "Spanning Tree Simulation by Amir Razmjou \n" +
                    "usage: java stpsim3.jar [options] [seed options]\n" +
                    "Options:\n" +
                    "  --help                                show this help\n" +
                    "  --node                                number of nodes [mandatory]\n" +
                    "  --density                             density of graph [mandatory]\n" +
                    "Seed Options:\n" +
                    "  --seed-network                        random seed number for network structure [optional]\n" +
                    "  --seed-sync                           random seed number for threads timing [optional]\n";

            System.out.println(help);
        }

        boolean is() {
            return myResult;
        }

        public Integer getNods() {
            return n;
        }

        public Float getDensity() {
            return d;
        }

        public GetCliParameters invoke() {
            if (args.length == 0) {
                showHelp();
                myResult = true;
                return this;
            }

            if (args[0].contains("--help")) {
                showHelp();
                myResult = true;
                return this;
            }


            n = null;
            d = null;
            Integer sn = 0;
            Integer ss = 0;
            for (int i = 0; i < args.length; i++) {
                try {
                    if (args[i].contains("--node")) {
                        n = Integer.parseInt(args[++i]);
                        if (n < 3) {
                            System.out.println("Number of nodes can not be less than 3.");
                            showHelp();
                            myResult = true;
                            n = null;
                            return this;
                        }
                    }
                    else if (args[i].contains("--density")) {
                        d = Float.parseFloat(args[++i]);
                    }
                    else if (args[i].contains("--seed-network")) {
                        sn = Integer.parseInt(args[++i]);
                    }
                    else if (args[i].contains("--seed-sync")) {
                        ss = Integer.parseInt(args[++i]);
                    }
                    else if (args[i].contains("--show-csv")) {
                        Configurations.showCsv = true;
                    }

                } catch (Exception e) {
                    showHelp();
                    myResult = true;
                    return this;
                }
            }

            if (n == null || d == null) {
                showHelp();
                myResult = true;
                return this;
            }

            Configurations.seedNetwork = sn;
            Configurations.seedSync = ss;
            myResult = false;
            return this;
        }
    }
}
