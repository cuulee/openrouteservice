package heigit.ors.services.routing.requestprocessors.geojson;

import com.graphhopper.util.shapes.BBox;
import com.vividsolutions.jts.geom.LineString;
import heigit.ors.config.AppConfig;
import heigit.ors.routing.RouteResult;
import heigit.ors.routing.RoutingRequest;
import heigit.ors.services.routing.RoutingServiceSettings;
import heigit.ors.services.routing.requestprocessors.json.JsonRoutingResponseWriter;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opengis.feature.simple.SimpleFeatureType;

import javax.sound.sampled.Line;

class FeatureParser {
    private static RoutingRequest request;

    public static SimpleFeatureType createRouteFeatureType(RoutingRequest rreq) throws Exception {
        // make routeResults and request accessible class wide
        FeatureParser.request = rreq;
        // create SimpleFeatureType template
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        // set the name --> The result looks weird but a name is required!! https://go.openrouteservice.org/:openrouteservice routing
        builder.setName(RoutingServiceSettings.getParameter("routing_name"));
        // check for elevation and if found set WGS84_3D
        if (request.getIncludeElevation()) {
            // add geometry and crs
            builder.setCRS(DefaultGeographicCRS.WGS84_3D);
            builder.add("geometry", LineString.class);
        } else {
            // add geometry and crs
            builder.setCRS(DefaultGeographicCRS.WGS84);
            builder.add("geometry", LineString.class);
        }

        // set the URI for author purposes
        builder.setNamespaceURI(AppConfig.Global().getParameter("info", "base_url"));
        builder.add("bbox", JSONArray.class);
        builder.add("way_points", JSONArray.class);
        builder.add("segments", JSONArray.class);
        return builder.buildFeatureType();
    }

    public static SimpleFeatureCollection creatureRouteFeatureCollection(SimpleFeatureType feature) {
        //featureBuilder.add("info", JSONObject.class);
        //featureBuilder.add("extras", JSONObject.class);

        return null;
    }
}
