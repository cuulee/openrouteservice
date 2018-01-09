/*
 *  Licensed to GIScience Research Group, Heidelberg University (GIScience)
 *
 *   http://www.giscience.uni-hd.de
 *   http://www.heigit.org
 *
 *  under one or more contributor license agreements. See the NOTICE file
 *  distributed with this work for additional information regarding copyright
 *  ownership. The GIScience licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package heigit.ors.geojson;

import org.geotools.geojson.GeoJSON;
import org.geotools.geometry.jts.coordinatesequence.CoordinateSequences;
import org.json.JSONArray;
import org.json.JSONObject;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

import heigit.ors.util.FormatUtility;

import java.io.StringWriter;
import java.io.Writer;


public class GeometryJSON {

    private final static int COORDINATE_PRECISION = 6;
    private final static GeometryFactory factory = new GeometryFactory();

    public static JSONArray toJSON(Geometry geom, StringBuffer buffer) throws Exception {
        if (geom instanceof Polygon) {
            return toJSON((Polygon) geom);
        } else if (geom instanceof LineString) {
            return toJSON((LineString) geom, false);
        } else if (geom instanceof Point) {
            return toJSON((Point) geom);
        } else if (geom instanceof MultiPolygon) {
            return toJSON((MultiPolygon) geom);
        } else {
            throw new Exception("toJSON function is not implemented for " + geom.getGeometryType());
        }
    }

    public static JSONArray toJSON(MultiPolygon multiPoly) {
        int size = multiPoly.getNumGeometries();
        JSONArray coords = new JSONArray(size);

        for (int i = 0; i < size; i++) {
            Polygon poly = (Polygon) multiPoly.getGeometryN(i);
            coords.put(toJSON(poly));
        }

        return coords;
    }

    public static JSONArray toJSON(Polygon poly) {
        JSONArray coords = new JSONArray(1 + poly.getNumInteriorRing());

        LineString shell = poly.getExteriorRing();

        boolean inverse = shell.getNumPoints() > 1 ? !CoordinateSequences.isCCW(shell.getCoordinateSequence()) : false;
        coords.put(toJSON(shell, inverse));

        if (poly.getNumInteriorRing() > 0) {
            int nRings = poly.getNumInteriorRing();

            for (int j = 0; j < nRings; ++j) {
                LineString ring = poly.getInteriorRingN(j);
                inverse = ring.getNumPoints() > 1 ? CoordinateSequences.isCCW(ring.getCoordinateSequence()) : false;
                coords.put(toJSON(ring, inverse));
            }
        }

        return coords;
    }

    public static JSONArray toJSON(LineString line, boolean inverseSeq) {
        // "coordinates": [ [100.0, 0.0], [101.0, 1.0] ]
        int size = line.getNumPoints();

        JSONArray arrCoords = new JSONArray(size);

        CoordinateSequence seq = line.getCoordinateSequence();
        Coordinate coord = null;

        for (int i = 0; i < size; ++i) {
            coord = seq.getCoordinate(inverseSeq ? size - i - 1 : i);

            arrCoords.put(toJSON(coord));
        }

        return arrCoords;
    }

    private static JSONArray toJSON(Point point) {
        return toJSON(point.getCoordinate());
    }

    public static JSONArray toJSON(Coordinate c) {
        JSONArray arrCoords = new JSONArray(2);
        arrCoords.put(FormatUtility.roundToDecimals(c.x, COORDINATE_PRECISION));
        arrCoords.put(FormatUtility.roundToDecimals(c.y, COORDINATE_PRECISION));

        return arrCoords;
    }


    public static JSONArray toJSON(Coordinate[] coords, boolean includeElevation) {
        int size = coords.length;
        JSONArray arrCoords = new JSONArray(size);

        for (int i = 0; i < size; ++i) {
            Coordinate c = coords[i];
            JSONArray coord = new JSONArray(includeElevation ? 3 : 2);
            coord.put(FormatUtility.roundToDecimals(c.x, COORDINATE_PRECISION));
            coord.put(FormatUtility.roundToDecimals(c.y, COORDINATE_PRECISION));
            if (includeElevation)
                coord.put(FormatUtility.roundToDecimals(c.z, 1));

            arrCoords.put(coord);
        }

        return arrCoords;
    }

    public static JSONArray toJSON(double minX, double minY, double maxX, double maxY) {
        JSONArray bbox = new JSONArray(4);

        bbox.put(FormatUtility.roundToDecimals(minX, COORDINATE_PRECISION));
        bbox.put(FormatUtility.roundToDecimals(minY, COORDINATE_PRECISION));
        bbox.put(FormatUtility.roundToDecimals(maxX, COORDINATE_PRECISION));
        bbox.put(FormatUtility.roundToDecimals(maxY, COORDINATE_PRECISION));

        return bbox;
    }

    public static Geometry parse(JSONObject json) throws Exception {
        if (!json.has("type"))
            throw new Exception("type element is missing.");

        if (!json.has("coordinates"))
            throw new Exception("coordinates element is missing.");

        String type = json.getString("type");
        JSONArray arrCoords = json.getJSONArray("coordinates");
        Geometry geom = null;

        switch (type) {
            case "Point":
                geom = readPoint(arrCoords);
                break;
            case "MultiPoint":
                geom = readMultiPoint(arrCoords);
                break;
            case "LineString":
                geom = readLineString(arrCoords);
                break;
            case "MultiLineString":
                geom = readMultiLineString(arrCoords);
                break;
            case "Polygon":
                geom = readPolygon(arrCoords);
                break;
            case "MultiPolygon":
                geom = readMultiPolygon(arrCoords);
                break;
        }

        return geom;
    }

    private static Point readPoint(JSONArray value) {
        Coordinate c = new Coordinate(value.getDouble(0), value.getDouble(1));
        return factory.createPoint(c);
    }

    private static MultiPoint readMultiPoint(JSONArray value) {
        return factory.createMultiPoint(readCoordinates(value));
    }

    private static LineString readLineString(JSONArray value) {
        return factory.createLineString(readCoordinates(value));
    }

    private static MultiLineString readMultiLineString(JSONArray value) {
        int n = value.length();
        LineString[] lineStrings = new LineString[n];

        for (int i = 0; i < n; i++) {
            JSONArray arrLineString = value.getJSONArray(i);
            lineStrings[i] = readLineString(arrLineString);
        }

        return factory.createMultiLineString(lineStrings);
    }

    private static MultiPolygon readMultiPolygon(JSONArray value) {
        int n = value.length();
        Polygon[] polys = new Polygon[n];

        for (int i = 0; i < n; i++) {
            JSONArray arrPoly = value.getJSONArray(i);
            polys[i] = readPolygon(arrPoly);
        }

        return factory.createMultiPolygon(polys);
    }

    private static Polygon readPolygon(JSONArray value) {
        int n = value.length();

        LinearRing shell = null;
        LinearRing[] holes = new LinearRing[n - 1];

        for (int i = 0; i < n; i++) {
            JSONArray arrLineString = value.getJSONArray(i);
            if (i == 0)
                shell = factory.createLinearRing(readCoordinates(arrLineString));
            else
                holes[i - 1] = factory.createLinearRing(readCoordinates(arrLineString));
        }

        if (holes == null || holes.length == 0)
            return factory.createPolygon(shell);
        else
            return factory.createPolygon(shell, holes);
    }

    private static Coordinate[] readCoordinates(JSONArray value) {
        int n = value.length();

        Coordinate[] coords = new Coordinate[n];

        for (int i = 0; i < n; i++) {
            JSONArray arrCoord = value.getJSONArray(i);
            coords[i] = new Coordinate(arrCoord.getDouble(0), arrCoord.getDouble(1));
        }

        return coords;
    }

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
        GeoJSON.write(lineString, output);
        GeoJSON.write(lineString, new StringWriter());
        return new JSONObject(output.toString());
    }
    // TODO: Integrate all geometry features into the class

}
