package nl.doonline.ZSCompetitions;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Point;
import net.runelite.api.Perspective;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.Constants;

import javax.inject.Inject;
import java.awt.Rectangle;
import java.awt.Polygon;
import java.awt.Shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class VisionTrackerService {

    private final Client client;
    private final ClientThread clientThread;
    private final EventTrackerConfig config;

    @Inject
    public VisionTrackerService(Client client, ClientThread clientThread, EventTrackerConfig config) {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
    }

    public void start() {
        log.info("Vision Tracker Service started!");
        // No internal scheduler needed, EventTrackerService will call getUnifiedVisionData()
    }

    public void stop() {
        log.info("Vision Tracker Service stopped!");
        // No internal scheduler to shut down
    }

    public Map<String, Object> getUnifiedVisionData() throws InterruptedException, ExecutionException {
        Callable<Map<String, Object>> callable = () -> {
            Map<String, Object> unifiedData = new ConcurrentHashMap<>();
            Map<String, Object> newVisibleTiles = new ConcurrentHashMap<>();
            List<Map<String, Object>> newVisibleNpcs = new ArrayList<>();
            List<Map<String, Object>> newVisibleObjects = new ArrayList<>();
            List<Map<String, Object>> newVisibleGroundItems = new ArrayList<>();

            WorldView wv = client.getTopLevelWorldView();
            if (wv == null) {
                return unifiedData; // Return empty data if not available
            }

            Scene scene = wv.getScene();
            int plane = wv.getPlane();

            WorldPoint playerLocation = client.getLocalPlayer() != null ? client.getLocalPlayer().getWorldLocation() : null;
            int renderRadius = config.tileRenderRadius();

            // Collect NPCs
            for (NPC npc : wv.npcs().stream().filter(Objects::nonNull).collect(Collectors.toList())) {
                Map<String, Object> npcData = new HashMap<>();
                npcData.put("npcId", npc.getId());
                npcData.put("npcName", npc.getName());
                npcData.put("boundingBox", getBoundingBox(npc));
                npcData.put("worldPosition", Map.of("x", npc.getWorldLocation().getX(), "y", npc.getWorldLocation().getY(), "plane", npc.getWorldLocation().getPlane()));
                newVisibleNpcs.add(npcData);
            }
            unifiedData.put("visibleNpcs", newVisibleNpcs);

            // Collect GameObjects and Tiles (including GroundItems)
            for (int x = 0; x < Constants.SCENE_SIZE; x++) {
                for (int y = 0; y < Constants.SCENE_SIZE; y++) {
                    Tile tile = scene.getTiles()[plane][x][y];
                    if (tile != null) {
                        WorldPoint tileWorldPoint = tile.getWorldLocation();
                        // Filter by radius if configured
                        if (renderRadius > 0 && playerLocation != null && playerLocation.distanceTo(tileWorldPoint) > renderRadius) {
                            continue;
                        }

                        Polygon clickbox = Perspective.getCanvasTilePoly(client, tile.getLocalLocation());
                        if (clickbox != null && clickbox.npoints >= 4) {
                            WorldPoint worldPoint = tile.getWorldLocation();
                            String tileKey = worldPoint.getX() + "," + worldPoint.getY() + "," + worldPoint.getPlane();
                            Map<String, Object> tileInfo = new ConcurrentHashMap<>();

                            tileInfo.put("sceneCoordinates", Map.of("x", tile.getSceneLocation().getX(), "y", tile.getSceneLocation().getY()));
                            
                            List<List<Integer>> vertices = new ArrayList<>();
                            for (int i = 0; i < clickbox.npoints; i++) {
                                vertices.add(List.of(clickbox.xpoints[i], clickbox.ypoints[i]));
                            }
                            tileInfo.put("vertices", vertices);

                            Rectangle bounds = clickbox.getBounds();
                            tileInfo.put("clickbox", Map.of("x", bounds.x, "y", bounds.y, "width", bounds.width, "height", bounds.height));

                            List<Map<String, Object>> entitiesOnTile = new ArrayList<>();
                            // Players
                            for (Player player : wv.players().stream().filter(Objects::nonNull).collect(Collectors.toList())) {
                                if (player.getLocalLocation().distanceTo(tile.getLocalLocation()) == 0) {
                                    Map<String, Object> entity = new HashMap<>();
                                    entity.put("type", "PLAYER");
                                    entity.put("id", player.getId());
                                    entitiesOnTile.add(entity);
                                }
                            }

                            // NPCs (already collected globally, but can be added here for tile-specific context if needed)
                            // GameObjects
                            for (GameObject gameObject : tile.getGameObjects()) {
                                if (gameObject != null) {
                                    Map<String, Object> entity = new HashMap<>();
                                    entity.put("type", "OBJECT");
                                    entity.put("id", gameObject.getId());
                                    entitiesOnTile.add(entity);
                                    
                                    // Also add to global objects list
                                    Map<String, Object> objectData = new HashMap<>();
                                    objectData.put("id", gameObject.getId());
                                    objectData.put("boundingBox", getBoundingBox(gameObject));
                                    newVisibleObjects.add(objectData);
                                }
                            }

                            // GroundItems
                            List<net.runelite.api.TileItem> groundItems = tile.getGroundItems();
                            if (groundItems != null) {
                                for (net.runelite.api.TileItem item : groundItems) {
                                    Map<String, Object> entity = new HashMap<>();
                                    entity.put("type", "GROUND_ITEM");
                                    entity.put("id", item.getId());
                                    entity.put("quantity", item.getQuantity());
                                    entitiesOnTile.add(entity);
                                    
                                    // Also add to global ground items list
                                    Map<String, Object> groundItemData = new HashMap<>();
                                    groundItemData.put("id", item.getId());
                                    groundItemData.put("quantity", item.getQuantity());
                                    groundItemData.put("tileWorldPoint", Map.of("x", tileWorldPoint.getX(), "y", tileWorldPoint.getY(), "plane", tileWorldPoint.getPlane()));
                                    newVisibleGroundItems.add(groundItemData);
                                }
                            }

                            tileInfo.put("entities", entitiesOnTile);
                            CollisionData[] collisionData = client.getCollisionMaps();
                            if (collisionData != null && plane < collisionData.length) {
                                tileInfo.put("isWalkable", (collisionData[plane].getFlags()[tile.getSceneLocation().getX()][tile.getSceneLocation().getY()] & 0x1) == 0);
                            } else {
                                tileInfo.put("isWalkable", false); // Default to not walkable if collision data is unavailable
                            }
                            newVisibleTiles.put(tileKey, tileInfo);
                        }
                    }
                }
            }
            unifiedData.put("visibleTiles", newVisibleTiles);
            unifiedData.put("visibleObjects", newVisibleObjects);
            unifiedData.put("visibleGroundItems", newVisibleGroundItems);

            return unifiedData;
        };
        FutureTask<Map<String, Object>> task = new FutureTask<>(callable);
        clientThread.invoke(task);
        return task.get();
    }

    // Helper to get bounding box for Actors (NPCs, Players)
    private Map<String, Object> getBoundingBox(net.runelite.api.Actor actor) {
        if (actor == null || actor.getConvexHull() == null) {
            return null;
        }
        Shape shape = actor.getConvexHull();
        Rectangle bounds = shape.getBounds();
        return Map.of("x", bounds.x, "y", bounds.y, "width", bounds.width, "height", bounds.height);
    }

    // Helper to get bounding box for GameObjects
    private Map<String, Object> getBoundingBox(net.runelite.api.GameObject gameObject) {
        if (gameObject == null || gameObject.getClickbox() == null) {
            return null;
        }
        Shape shape = gameObject.getClickbox();
        Rectangle bounds = shape.getBounds();
        return Map.of("x", bounds.x, "y", bounds.y, "width", bounds.width, "height", bounds.height);
    }
}