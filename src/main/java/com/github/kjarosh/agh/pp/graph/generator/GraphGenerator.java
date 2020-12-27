package com.github.kjarosh.agh.pp.graph.generator;

import com.github.kjarosh.agh.pp.graph.model.Edge;
import com.github.kjarosh.agh.pp.graph.model.Graph;
import com.github.kjarosh.agh.pp.graph.model.Permissions;
import com.github.kjarosh.agh.pp.graph.model.Vertex;
import com.github.kjarosh.agh.pp.graph.model.ZoneId;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author Kamil Jarosz
 */
@Slf4j
public class GraphGenerator {
    private final Random random = new Random();

    private final GraphGeneratorConfig config;
    private final EntityGenerator entityGenerator;
    private final List<ZoneId> zones;

    public GraphGenerator(GraphGeneratorConfig config) {
        this.config = config;
        this.entityGenerator = new EntityGenerator();
        this.zones = new ArrayList<>();
        for (int i = 0; i < config.getZones(); ++i) {
            this.zones.add(new ZoneId("zone" + i));
        }
    }

    public long estimateVertices() {
        double providers = config.getProviders();
        double spaces = config.getSpaces();
        double depth = config.getTreeDepth().avg();
        double groups = config.getGroupsPerGroup().avg();
        double users = config.getUsersPerGroup().avg();
        double parents = spaces * Math.pow(groups, depth);

        double estimated = providers + spaces + parents + parents * users;

        BigDecimal bd = new BigDecimal(estimated);
        bd = bd.round(new MathContext(1));
        return (long) bd.doubleValue();
    }

    public long estimateEdges() {
        double depth = config.getTreeDepth().avg();
        double groups = config.getGroupsPerGroup().avg();
        double users = config.getUsersPerGroup().avg();

        double providers = config.getProviders();
        double providersPerSpace = config.getProvidersPerSpace().avg();
        if (providersPerSpace > providers) {
            providersPerSpace = providers;
        }
        double spaces = config.getSpaces();
        double parents = spaces * Math.pow(groups, depth);

        double estimated = spaces * providersPerSpace + parents + parents * users;

        BigDecimal bd = new BigDecimal(estimated);
        bd = bd.round(new MathContext(1));
        return (long) bd.doubleValue();
    }

    public Graph generateGraph() {
        Graph graph = new Graph();

        // generate providers and spaces
        List<Vertex> providers = entityGenerator.generateVertices(
                this::randomZone,
                config.getProviders(),
                Vertex.Type.PROVIDER);
        providers.forEach(graph::addVertex);
        List<Vertex> spaces = entityGenerator.generateVertices(
                this::randomZone,
                config.getSpaces(),
                Vertex.Type.SPACE);
        spaces.forEach(graph::addVertex);

        // generate provider--space relations
        for (Vertex space : spaces) {
            int relations = config.getProvidersPerSpace().nextInt();
            for (int i = 0; i < relations; ++i) {
                Vertex provider = randomElement(providers);
                Edge e = new Edge(space.id(), provider.id(), Permissions.random(random));
                graph.addEdge(e);
            }
        }

        // generate space trees
        for (Vertex space : spaces) {
            int depth = config.getTreeDepth().nextInt();
            log.info("Generating space {}, depth {}", space.id(), depth);
            try {
                generateUserTree(graph, space, depth);
            } catch (StackOverflowError e) {
                log.error("Stack overflow for depth {}", depth, e);
                throw e;
            }
        }


        return graph;
    }

    private ZoneId randomZone() {
        return randomElement(zones);
    }

    private <T> T randomElement(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    private void generateUserTree(Graph graph, Vertex parent, int depth) {
        if (parent.type() != Vertex.Type.SPACE &&
                parent.type() != Vertex.Type.GROUP) {
            throw new AssertionError(parent.toString());
        }

        if (depth <= 0) {
            return;
        }

        int users = config.getUsersPerGroup().nextInt();
        for (int i = 0; i < users; ++i) {
            Vertex user = entityGenerator.generateVertex(randomZone(), Vertex.Type.USER);
            graph.addVertex(user);
            graph.addEdge(new Edge(user.id(), parent.id(), Permissions.random(random)));
        }

        if (depth <= 1) {
            return;
        }

        int groups = config.getGroupsPerGroup().nextInt();
        for (int i = 0; i < groups; ++i) {
            Vertex group = entityGenerator.generateVertex(randomZone(), Vertex.Type.GROUP);
            graph.addVertex(group);
            graph.addEdge(new Edge(group.id(), parent.id(), Permissions.random(random)));

            generateUserTree(graph, group, depth - 1);
        }
    }
}
