package heigit.ors.services.routing.requestprocessors.geojson;

import com.graphhopper.util.shapes.BBox;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import heigit.ors.config.AppConfig;
import heigit.ors.routing.RouteResult;
import heigit.ors.routing.RoutingRequest;
import heigit.ors.services.routing.RoutingServiceSettings;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.crs.DefaultGeocentricCRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;

public class GeoJsonResponseWriter {
    private static RouteResult[] routeResults;
    private static RoutingRequest request;

    public static String toGeoJson(RoutingRequest rreq, RouteResult[] routeResults) throws Exception {
        GeoJsonResponseWriter.routeResults = routeResults;
        GeoJsonResponseWriter.request = rreq;
        GeometryFactory geometryFactory = new GeometryFactory();
        // GeometryJSON.parse(json = JsonRoutingResponseWriter.toJson(rreq, new RouteResult[]{result}));
        DefaultFeatureCollection featureCollection = new DefaultFeatureCollection();
        for (RouteResult route : routeResults) {
            // Get the calculated route as linestring
            LineString lineString = geometryFactory.createLineString(route.getGeometry());
            // Add the LineString to a Feature and add additional information
            SimpleFeatureTypeBuilder feature = createFeature(lineString, route);





            /* // Convert the linestring to handy JSONObject to edit it easier
            JSONObject pureGeoJson = GeometryJSON.toGeoJSON(lineString);*/
        }

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

    private static SimpleFeatureTypeBuilder createFeature(LineString lineString, RouteResult route) {
        SimpleFeatureTypeBuilder feature = new SimpleFeatureTypeBuilder();
        // set the name
        feature.setName(RoutingServiceSettings.getParameter("routing_name"));
        // check for elevation and if found set WGS84_3D
        if (request.getIncludeElevation()) {
            // add geometry and crs
            feature.setCRS(DefaultGeographicCRS.WGS84_3D);
            feature.add("route", LineString.class);
        } else {
            // add geometry and crs
            feature.setCRS(DefaultGeographicCRS.WGS84);
            // TODO find out how to put the actual LineString object inside
            feature.add("route", LineString.class);
        }

        // set the URI for author purposes
        feature.setNamespaceURI(AppConfig.Global().getParameter("info", "base_url"));
        feature.add("bbox", BBox.class);
        feature.setDefaultGeometry(lineString.getCoordinates().toString());
        return null;
    }

    private String addFeatureClassInformation() {

        return null;
    }
}
