package heigit.ors.services.routing.requestprocessors.geojson;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.graphhopper.util.shapes.BBox;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import heigit.ors.routing.RouteResult;
import heigit.ors.routing.RoutingRequest;
import heigit.ors.services.optimization.requestprocessors.json.JsonOptimizationRequestProcessor;
import heigit.ors.services.routing.requestprocessors.json.JsonRoutingResponseWriter;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geojson.feature.FeatureCollectionHandler;
import org.geotools.geojson.feature.FeatureJSON;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.json.simple.parser.JSONParser;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GeoJsonResponseWriter {
    private static RouteResult[] routeResults;
    private static RoutingRequest request;

    public static String toGeoJson(RoutingRequest rreq, RouteResult[] routeResults) throws Exception {
        GeoJsonResponseWriter.routeResults = routeResults;
        GeoJsonResponseWriter.request = rreq;
        // The GEOJSON Tool cant handle the JSONArray's properly and malformes them
        // So the RouteResult properties are stored in a HashMap with its id as an identifier
        // The values are added later
        HashMap<String, List<JSONArray>> featurePropertiesMap= new HashMap<>();
        // create GeometryFactory for reuse purposes
        GeometryFactory geometryFactory = new GeometryFactory();
        // TODO: create FeatureCollection to add the single SimpleFeature's recursively
        FeatureCollectionHandler featureCollectionHandler = new FeatureCollectionHandler();
        // Create a new SimpleFeatureType to create a SimpleFeature from it
        // its written capital because a custom SimpleFeatureType is a static and immutable object
        SimpleFeatureType ROUTINGFEATURETYPE = FeatureParser.createRouteFeatureType(rreq);
        DefaultFeatureCollection defaultFeatureCollection = new DefaultFeatureCollection("routing", ROUTINGFEATURETYPE);
        JSONObject temporaryJsonRoute = JsonRoutingResponseWriter.toJson(request, routeResults);
        SimpleFeature routingFeature = null;
        for (RouteResult route : routeResults) {
            List<JSONArray> featureProperties = new ArrayList<>();
            // Get the route as LineString
            LineString lineString = geometryFactory.createLineString(route.getGeometry());
            // Create a SimpleFeature from the ROUTINGFEATURETYPE template
            SimpleFeatureBuilder routingFeatureBuilder = new SimpleFeatureBuilder(ROUTINGFEATURETYPE);
            // Add content to the SimpleFeature
            //Geometry
            routingFeatureBuilder.set("geometry", lineString);
            // BBox
            routingFeatureBuilder.set("bbox", temporaryJsonRoute.getJSONArray("routes").getJSONObject(0).getJSONArray("bbox"));
            //Way Points
            routingFeatureBuilder.set("way_points",temporaryJsonRoute.getJSONArray("routes").getJSONObject(0).getJSONArray("way_points"));
            // Segments
            routingFeatureBuilder.set("segments", temporaryJsonRoute.getJSONArray("routes").getJSONObject(0).getJSONArray("segments"));
            //routingFeature.set("segments", );
            routingFeature = routingFeatureBuilder.buildFeature(null);
            defaultFeatureCollection.add(routingFeature);
            // TODO get the values in the featureProperties List and remove them from the routingFeatureBuilder first, add them later -->
            featurePropertiesMap.put(routingFeature.getID(), featureProperties);
            /* // Convert the linestring to handy JSONObject to edit it easier
            JSONObject pureGeoJson = GeometryJSON.toGeoJSON(lineString);*/
        }
        FeatureJSON fjson = new FeatureJSON();
        StringWriter stringWriter = new StringWriter();
        fjson.writeFeature(routingFeature, stringWriter);
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = new JSONObject(stringWriter.toString());
        org.json.simple.JSONObject json = (org.json.simple.JSONObject) parser.parse(stringWriter.toString());

        //String json = stringWriter.toString();
        System.out.print(stringWriter);

        // LineString lineString = jsonTest.readLine(reader);
        // Integrate creation of GeoJSONs into GeometryJSON.class. Can be done for each object to provide generified conversions
        // JSONObject internalGeoJson = GeometryJSON.toGeoJSON(lineString);

        //
        // TODO: Write function to add "properties" to the geojson as an optional route feature that is only available for routes
        return null;
        // GeoJSON geoJSON = (GeoJSON) GeoJSON.read(output);
        // GeoJSON.read(output);
        // StringBuffer buffer = new StringBuffer();
        // JSONArray fgeojson = GeometryJSON.toJSON(geometry, new StringBuffer());

    }


    private SimpleFeatureType createSimpleFeatureType() {

        return null;
    }
}
