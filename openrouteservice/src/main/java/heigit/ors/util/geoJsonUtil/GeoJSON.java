package heigit.ors.util.geoJsonUtil;

import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import org.json.JSONObject;

import java.io.StringWriter;
import java.io.Writer;

public class GeoJSON {
    /**
     * *Not implemented yet*
     * This function creates a Point-GeoJSON
     *
     * @param point Input must be a {@link Point}
     * @return Returns a GeometryJSON as a well formatted {@link String}
     */
    public static String toGeoJSON(Point point) {
        return null;
    }

    /**
     * *Not implemented yet*
     * This function creates a Line-GeoJSON
     *
     * @param lineString Input must be a {@link LineString}
     * @return Returns a GeometryJSON as a well formatted {@link String}
     */
    public static JSONObject toGeoJSON(LineString lineString) throws Exception {
        // Create StringWriter to catch output of GeoJSON
        Writer output = new StringWriter();
        org.geotools.geojson.GeoJSON.write(lineString, output);
        org.geotools.geojson.GeoJSON.write(lineString, new StringWriter());
        return new JSONObject(output.toString());
    }
    // TODO: Integrate all geometry features into the class
}
