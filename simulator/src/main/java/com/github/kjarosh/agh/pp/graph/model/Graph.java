package com.github.kjarosh.agh.pp.graph.model;

import com.github.kjarosh.agh.pp.Config;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import static com.github.kjarosh.agh.pp.Config.ZONE_ID;

/**
 * @author Kamil Jarosz
 */
public class Graph {
    private final Map<VertexId, Vertex> vertices = new ConcurrentHashMap<>();
    private final Map<Pair<VertexId, VertexId>, Edge> edges = new ConcurrentHashMap<>();
    private final Map<VertexId, Set<Edge>> edgesBySrc = new ConcurrentHashMap<>();
    private final Map<VertexId, Set<Edge>> edgesByDst = new ConcurrentHashMap<>();

    public Graph() {

    }

    @SneakyThrows
    public static Graph deserialize(InputStream serialized) {
        Json value = Config.MAPPER.readValue(serialized, Json.class);
        Graph graph = new Graph();
        value.vertices.forEach(graph::addVertex);
        value.edges.forEach(graph::addEdge);
        return graph;
    }

    public void addVertex(Vertex v) {
        if (ZONE_ID == null || v.id().owner().equals(ZONE_ID)) {
            vertices.put(v.id(), v);
        }
    }

    public boolean hasVertex(VertexId id) {
        return vertices.containsKey(id);
    }

    public Vertex getVertex(VertexId id) {
        Vertex vertex = vertices.get(id);
        if (vertex == null && !hasVertex(id)) {
            throw new RuntimeException(
                    "Vertex " + id + " not found in zone " + Config.ZONE_ID);
        }
        return vertex;
    }

    public void addEdge(Edge e) {
        if (ZONE_ID != null &&
                !hasVertex(e.src()) &&
                !hasVertex(e.dst())) {
            return;
        }

        edges.put(Pair.of(e.src(), e.dst()), e);
        edgesBySrc.computeIfAbsent(e.src(), i -> new ConcurrentSkipListSet<>()).add(e);
        edgesByDst.computeIfAbsent(e.dst(), i -> new ConcurrentSkipListSet<>()).add(e);
    }

    public void removeEdge(Edge e) {
        edges.remove(e);
        edgesBySrc.get(e.src()).remove(e);
        edgesByDst.get(e.dst()).remove(e);
    }

    public Edge getEdge(VertexId from, VertexId to) {
        return edges.get(Pair.of(from, to));
    }

    public void setPermissions(VertexId from, VertexId to, Permissions permissions) {
        Edge edge = getEdge(from, to);
        if (edge == null) {
            throw new IllegalStateException();
        }

        removeEdge(edge);
        addEdge(new Edge(from, to, permissions));
    }

    public Set<Edge> getEdgesBySource(VertexId source) {
        return edgesBySrc.getOrDefault(source, Collections.emptySet());
    }

    public Set<Edge> getEdgesByDestination(VertexId destination) {
        return edgesByDst.getOrDefault(destination, Collections.emptySet());
    }

    public int edgeCount() {
        return edges.size();
    }

    public Collection<Vertex> allVertices() {
        return vertices.values();
    }

    public Collection<Edge> allEdges() {
        return edges.values();
    }

    @SneakyThrows
    public void serialize(OutputStream os) {
        Json json = new Json();
        json.setEdges(new ArrayList<>(this.edges.values()));
        json.setVertices(new ArrayList<>(this.vertices.values()));
        Config.MAPPER.writeValue(os, json);
    }

    @Override
    public String toString() {
        return "Graph(" + edges.size() + " edges, " + vertices.size() + " vertices)";
    }

    @Getter
    @Setter
    private static class Json {
        private List<Vertex> vertices;
        private List<Edge> edges;
    }
}
