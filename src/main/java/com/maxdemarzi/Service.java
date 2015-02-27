package com.maxdemarzi;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.codehaus.jackson.map.ObjectMapper;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.schema.Schema;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.helpers.collection.IteratorUtil;


@Path("/v1")
public class Service {

    private static GraphDatabaseService db;

    public Service(@Context GraphDatabaseService graphDatabaseService) {
        db = graphDatabaseService;
    }

    static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LogManager.getLogger("HelloWorld");
    private static final LoadingCache<Integer, Long> segments = CacheBuilder.newBuilder()
            .maximumSize(200000)
            .build(
                    new CacheLoader<Integer, Long>() {
                        public Long load(Integer segmentId) {
                            return getSegmentNodeId(segmentId);
                        }
                    });

    private static final Long getSegmentNodeId(Integer segmentId){
        Node node = IteratorUtil.singleOrNull(db.findNodesByLabelAndProperty(Labels.Segment, "segmentId", segmentId));
        return node.getId();
    }

    @GET
    @Path("/migrate")
    public String migrate(@Context GraphDatabaseService db) {
        try (Transaction tx = db.beginTx()) {
            Schema schema = db.schema();
            schema.constraintFor(Labels.Segment)
                    .assertPropertyIsUnique("segmentId")
                    .create();
            tx.success();
        }
        // Wait for indexes to come online
        try (Transaction tx = db.beginTx()) {
            Schema schema = db.schema();
            schema.awaitIndexesOnline(1, TimeUnit.DAYS);
        }
        return "Migrated!";
    }

    @POST
    @Path("/lookup")
    public String Lookup(String body, @Context GraphDatabaseService db) throws IOException {
        ArrayList<Long> idsFound = new ArrayList<>();
        HashMap<String, List<Integer>> input;
        input = objectMapper.readValue(body, HashMap.class);
        List<Integer> segmentIds = input.get("segmentIds");

        try (Transaction tx = db.beginTx()) {
            for (int segmentId : segmentIds) {
                final ResourceIterable<Node> nodes =
                        db.findNodesByLabelAndProperty(Labels.Segment, "segmentId", segmentId);

                final Node node;

                // assume only one node can be found for a given internal segment id,
                // our db has a unique index on this property
                try (ResourceIterator<Node> nodeIter = nodes.iterator()) {
                    node = (nodeIter.hasNext() ? nodeIter.next() : null);
                }

                if (node != null) {
                    idsFound.add(node.getId());
                }
            }
            tx.success();
        }
        logger.info(idsFound.size());
        return "Looked up";
    }

    @POST
    @Path("/lookup2")
    public String Lookup2(String body, @Context GraphDatabaseService db) throws IOException {
        ArrayList<Long> idsFound = new ArrayList<>();
        HashMap<String, List<Integer>> input;
        input = objectMapper.readValue(body, HashMap.class);
        List<Integer> segmentIds = input.get("segmentIds");

        try (Transaction tx = db.beginTx()) {
            for (int segmentId : segmentIds) {
                final Node node = IteratorUtil.singleOrNull(db.findNodesByLabelAndProperty(Labels.Segment, "segmentId", segmentId));

                if (node != null) {
                    idsFound.add(node.getId());
                }
            }
            tx.success();
        }
        logger.info(idsFound.size());
        return "Looked up";
    }

    @POST
    @Path("/cachedlookup")
    public String CachedLookup(String body, @Context GraphDatabaseService db) throws IOException, ExecutionException {
        ArrayList<Long> idsFound = new ArrayList<>();
        HashMap<String, List<Integer>> input;
        input = objectMapper.readValue(body, HashMap.class);
        List<Integer> segmentIds = input.get("segmentIds");

        try (Transaction tx = db.beginTx()) {
            for (int segmentId : segmentIds) {
                final Node node = db.getNodeById(segments.get(segmentId));

                if (node != null) {
                    idsFound.add(node.getId());
                }
            }
            tx.success();
        }
        logger.info(idsFound.size());
        return "Looked up";
    }
}
