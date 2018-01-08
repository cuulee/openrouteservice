package heigit.ors.services.routing.requestprocessors.geojson;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import heigit.ors.geojson.GeometryJSON;
import heigit.ors.routing.RouteResult;
import heigit.ors.routing.RoutingRequest;
import org.geotools.geojson.GeoJSON;
import org.geotools.geojson.feature.FeatureJSON;

import java.io.*;

public class GeoJsonResponseWriter {
    public static String toGeoJson(RoutingRequest rreq, RouteResult result) throws Exception {
        GeometryFactory geometryFactory = new GeometryFactory();
        // GeometryJSON.parse(json = JsonRoutingResponseWriter.toJson(rreq, new RouteResult[]{result}));
        Geometry geometry = geometryFactory.createGeometry(geometryFactory.createLineString(result.getGeometry()));
        // Create StringWriter to catch output of GeoJSON
        Writer output = new StringWriter();
        GeoJSON.write(geometry, output);
        GeoJSON.write(geometry, new StringWriter());
        org.geotools.geojson.geom.GeometryJSON jsonTest = new org.geotools.geojson.geom.GeometryJSON();
        Reader reader = new StringReader(output.toString());
        LineString lineString = jsonTest.readLine(reader);
        FeatureJSON featureJSON = new FeatureJSON();
        featureJSON.readFeature(geometry);
        // Integrate creation of GeoJSONs into GeometryJSON.class. Can be done for each object to provide generified conversions
        String internalGeoJson = GeometryJSON.toGeoJSON(lineString);
        // TODO: Write function to add "properties" to the geojson as an optional route feature that is only available for routes
        return null;
        // GeoJSON geoJSON = (GeoJSON) GeoJSON.read(output);
        // GeoJSON.read(output);
        // StringBuffer buffer = new StringBuffer();
        // JSONArray fgeojson = GeometryJSON.toJSON(geometry, new StringBuffer());

    }
}
