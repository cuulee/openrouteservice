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
package heigit.ors.routing;

import java.util.List;

import com.graphhopper.GHResponse;
import com.graphhopper.PathWrapper;
import com.graphhopper.util.AngleCalc;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionAnnotation;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.RoundaboutInstruction;
import com.graphhopper.util.shapes.BBox;
import com.vividsolutions.jts.geom.Coordinate;

import heigit.ors.common.ArrivalDirection;
import heigit.ors.common.CardinalDirection;
import heigit.ors.common.DistanceUnit;
import heigit.ors.exceptions.InternalServerException;
import heigit.ors.localization.LocalizationManager;
import heigit.ors.routing.instructions.InstructionTranslator;
import heigit.ors.routing.instructions.InstructionTranslatorsCache;
import heigit.ors.routing.instructions.InstructionType;
import heigit.ors.util.DistanceUnitUtil;
import heigit.ors.util.FormatUtility;
import heigit.ors.util.StringUtility;

public class RouteResultBuilder 
{
	private AngleCalc _angleCalc;
	private DistanceCalc _distCalc;
	private String _nameAppendix;
	private static final CardinalDirection _directions[] = {CardinalDirection.North, CardinalDirection.NorthEast, CardinalDirection.East, CardinalDirection.SouthEast, CardinalDirection.South, CardinalDirection.SouthWest, CardinalDirection.West, CardinalDirection.NorthWest};

	public RouteResultBuilder()
	{
		_angleCalc = new AngleCalc();
		_distCalc = new DistanceCalcEarth();
	}

	/**
	 * createRouteResult creates the desired route between two coordinates.
	 * @param routes An {@link java.util.List<GHResponse>}of type GHResponse
	 * @param request Holds the initial {@link RoutingRequest} from the user
	 * @param extras Holds information of what {@link RouteExtraInfo} to include
	 * @return Returns the {@link RouteResult}
	 * @throws Exception
	 */
	public RouteResult createRouteResult(List<GHResponse> routes, RoutingRequest request, List<RouteExtraInfo> extras) throws Exception
	{
		RouteResult result = new RouteResult(request.getExtraInfo());

		if (routes.isEmpty())
			return result;

		if(!LocalizationManager.getInstance().isLanguageSupported(request.getLanguage()))
			throw new Exception("Specified language '" +  request.getLanguage() + "' is not supported.");

		InstructionTranslator instrTranslator = InstructionTranslatorsCache.getInstance().getTranslator(request.getLanguage());

		boolean formatInstructions = request.getInstructionsFormat() == RouteInstructionsFormat.HTML;
		int nRoutes = routes.size();
		double distance = 0.0;
		double duration = 0.0;
		double ascent = 0.0;
		double descent = 0.0;
		double distanceActual = 0.0;
		double durationTraffic = 0.0;

		double lon0 = 0, lat0 = 0, lat1 = 0, lon1 = 0;
		boolean includeDetourFactor = request.hasAttribute("detourfactor");
		boolean includeElev = request.getIncludeElevation();
		DistanceUnit units = request.getUnits();
		int unitDecimals = FormatUtility.getUnitDecimals(units);
		PointList prevSegPoints = null, segPoints, nextSegPoints;

		BBox bbox = null; 
		int[] routeWayPoints = null;

		if (request.getIncludeGeometry())
		{
			routeWayPoints = new int[nRoutes + 1]; 
			routeWayPoints[0] = 0;
		}

		if (extras != null)
			result.addExtraInfo(extras);

		for (int ri = 0; ri < nRoutes; ++ri)
		{
			GHResponse resp = routes.get(ri);

			if (resp.hasErrors())
				throw new InternalServerException(RoutingErrorCodes.UNKNOWN, String.format("Unable to find a route between points %d (%s) and %d (%s)", ri, FormatUtility.formatCoordinate(request.getCoordinates()[ri]), ri + 1, FormatUtility.formatCoordinate(request.getCoordinates()[ri+1])));

			PathWrapper path = resp.getBest();
			PointList routePoints = path.getPoints();

			if (bbox == null)
				bbox = new BBox(routePoints.getLon(0), routePoints.getLon(0), routePoints.getLat(0), routePoints.getLat(0));
			bbox = path.calcRouteBBox(bbox);

			if (request.getIncludeGeometry())
			{
				result.addPoints(routePoints, ri > 0, includeElev);

				routeWayPoints[ri + 1] = result.getGeometry().length - 1;

				if (request.getIncludeInstructions())
				{
					InstructionList instructions = path.getInstructions();
					int startWayPointIndex = routeWayPoints[ri];
					int nInstructions = instructions.size(); 
					//if (nInstructions > 1) // last is finishinstruction
					//	nInstructions -= 1;

					Instruction instr, prevInstr = null;
					InstructionType instrType, prevInstrType = InstructionType.UNKNOWN;
					RouteSegment seg = new RouteSegment(path, units);

					if (includeDetourFactor)
					{
						lat0 = routePoints.getLat(0);
						lon0 = routePoints.getLon(0);

						lat1 = routePoints.getLat(routePoints.getSize() - 1);
						lon1 = routePoints.getLon(routePoints.getSize() - 1);

						double dist = _distCalc.calcDist(lat0, lon0, lat1, lon1);
						seg.setDetourFactor((dist == 0) ? 0 : FormatUtility.roundToDecimals(path.getDistance()/dist, 2));
					}

					RouteStep prevStep = null;
					String instrText = "";
					double stepDistance, stepDuration;

					for (int ii = 0; ii < nInstructions; ++ii) 
					{
						instr = instructions.get(ii);
						InstructionAnnotation instrAnnotation = instr.getAnnotation();
						instrType = getInstructionType(ii == 0, instr); 
						segPoints = instr.getPoints();
						nextSegPoints = (ii + 1 < nInstructions) ? instructions.get(ii + 1).getPoints() : getNextSegPoints(routes, ri + 1, 0);
						
						String roadName = formatInstructions && !Helper.isEmpty(instr.getName()) ? "<b>" + instr.getName() + "</b>" : instr.getName();
						instrText = "";

						stepDistance = FormatUtility.roundToDecimals(DistanceUnitUtil.convert(instr.getDistance(), DistanceUnit.Meters, units), unitDecimals);
						stepDuration = FormatUtility.roundToDecimals(instr.getTime()/1000.0, 1); 

						RouteStep step = new RouteStep();

						if (ii == 0)
						{
							if (segPoints.size() == 1)
							{
								if (ii + 1 < nInstructions)
								{
									lat1 = nextSegPoints.getLat(0);
									lon1 = nextSegPoints.getLon(0);
								}
								else
								{
									lat1 = segPoints.getLat(ii);
									lon1 = segPoints.getLon(ii);
								}
							}
							else
							{
								lat1 = segPoints.getLat(ii+1);
								lon1 = segPoints.getLon(ii+1);
							}

							CardinalDirection dir = calcDirection(segPoints.getLat(ii), segPoints.getLon(ii), lat1, lon1);
							instrText = instrTranslator.getDepart(dir, roadName);
						}
						else
						{
							if (instr instanceof RoundaboutInstruction)
							{
								RoundaboutInstruction raInstr = (RoundaboutInstruction)instr;
								step.setExitNumber(raInstr.getExitNumber());
								instrText = instrTranslator.getRoundabout(raInstr.getExitNumber(), roadName);
								if (raInstr.getRoundaboutExitBearings() != null)
								{
									step.setRoundaboutExitBearings(raInstr.getRoundaboutExitBearings());
								}
							}
							else
							{
								if (isTurnInstruction(instrType))
									instrText = instrTranslator.getTurn(instrType, roadName);
								else if (instrType == InstructionType.CONTINUE)
									instrText = instrTranslator.getContinue(instrType, roadName);
								else if (instrType == InstructionType.FINISH)
								{
									instrText = instrTranslator.getArrive(getArrivalDirection(routePoints, request.getDestination()), prevInstr.getName());
								}
								else
									instrText = "Oops! Fix me";
							}
						}


						// merge route steps with similar names 
						// example: http://localhost:8082/openrouteservice-4.0.0/routes?profile=driving-car&coordinates=8.690614,49.38365|8.7007,49.411699|8.7107,49.4516&prettify_instructions=true
						if (prevStep != null && instrType == prevInstrType && canMergeInstructions(instr.getName(), prevInstr.getName()))
						{
							String mergedRoadName = mergeInstructions(instr.getName(), prevInstr.getName());

							int[] wayPoints = prevStep.getWayPoints();
							wayPoints[1] =  wayPoints[1] + instr.getPoints().size();

							stepDuration = FormatUtility.roundToDecimals(instr.getTime()/1000.0, 1); 

							prevStep.setDistance(FormatUtility.roundToDecimals(DistanceUnitUtil.convert(prevStep.getDistance() +  stepDistance, DistanceUnit.Meters, units), unitDecimals));
							prevStep.setDuration(FormatUtility.roundToDecimals(prevStep.getDuration() +  stepDuration, 1));
							prevStep.setName(mergedRoadName);
							
							if (_nameAppendix != null)
								mergedRoadName += " ("+ _nameAppendix + ")";
							if (formatInstructions)
								mergedRoadName = "<b>" + mergedRoadName + "</b>";

							prevStep.setInstruction(instrTranslator.getContinue(instrType, mergedRoadName));

							//if (request.getIncludeManeuvers())
							//	prevStep.setManeuver(computeManeuver(prevSegPoints, segPoints));
						}
						else
						{
							_nameAppendix = null;

							step.setDistance(stepDistance);
							step.setDuration(stepDuration);
							step.setInstruction(instrText);
							step.setName(instr.getName());
							step.setType(instrType.ordinal());
							step.setWayPoints(new int[] { startWayPointIndex, getWayPointEndIndex(startWayPointIndex, instrType, instr)});

							if (request.getIncludeManeuvers())
								step.setManeuver(calcManeuver(instrType, prevSegPoints, segPoints, nextSegPoints));

							seg.addStep(step);

							prevStep = step;
						}

						// step.setMessage(message);
						// add message and message type

						startWayPointIndex += instr.getPoints().size();
						//step.setMode // walking, cycling, etc. for multimodal routing

						if (instrAnnotation != null && instrAnnotation.getWayType() != 1) // Ferry, Steps as pushing sections
							distanceActual += stepDistance;

						prevInstr = instr;
						prevInstrType = instrType;
						prevSegPoints = segPoints;
					}

					result.addSegment(seg);

					distance += seg.getDistance();
					duration += seg.getDuration();
				}
				else
				{
					distance += FormatUtility.roundToDecimals(DistanceUnitUtil.convert(path.getDistance(), DistanceUnit.Meters, units), FormatUtility.getUnitDecimals(units));
					duration += FormatUtility.roundToDecimals(path.getTime()/1000.0, 1);
				}
			}
			else
			{
				InstructionList instructions = path.getInstructions();
				int nInstructions = instructions.size(); 
				if (nInstructions > 1) 
					nInstructions -= 1;

				for (int j = 0; j < nInstructions; ++j) 
				{
					Instruction instr = instructions.get(j);
					InstructionAnnotation instrAnnotation = instr.getAnnotation();

					if (instrAnnotation != null && instrAnnotation.getWayType() != 1) // Ferry, Steps as pushing sections
						distanceActual += FormatUtility.roundToDecimals(DistanceUnitUtil.convert(instr.getDistance(), DistanceUnit.Meters, units), unitDecimals);
				}

				distance += FormatUtility.roundToDecimals(DistanceUnitUtil.convert(path.getDistance(), DistanceUnit.Meters, units), unitDecimals);
				duration += FormatUtility.roundToDecimals(path.getTime()/1000.0, 1);
			}

			if (includeElev)
			{
				ascent += path.getAscend();
				descent += path.getDescend();
			}

			durationTraffic += path.getRouteWeight();
		}

		RouteSummary routeSummary = result.getSummary();

		routeSummary.setDuration(request.getSearchParameters().getConsiderTraffic() ? durationTraffic : duration);
		routeSummary.setDistance(FormatUtility.roundToDecimals(distance, unitDecimals));
		routeSummary.setDistanceActual(FormatUtility.roundToDecimals(distanceActual, unitDecimals));
		routeSummary.setAverageSpeed(FormatUtility.roundToDecimals(distance/(units == DistanceUnit.Meters ? 1000 : 1)/(routeSummary.getDuration() / 3600), 1));
		routeSummary.setAscent(FormatUtility.roundToDecimals(ascent, 1));
		routeSummary.setDescent(FormatUtility.roundToDecimals(descent, 1));

		if (routeWayPoints != null)
			result.setWayPointsIndices(routeWayPoints);

		if (bbox != null)
			routeSummary.setBBox(bbox);

		return result;
	}

	private ArrivalDirection getArrivalDirection(PointList points, Coordinate destination)
	{
		if (points.size() < 2)
			return ArrivalDirection.Unknown;

		int lastIndex = points.size() - 1;
		double lon0 = points.getLon(lastIndex - 1);
		double lat0 = points.getLat(lastIndex - 1);
		double lon1 = points.getLon(lastIndex);
		double lat1 = points.getLat(lastIndex);

		double dist = _distCalc.calcDist(lat1, lon1, destination.y, destination.x);

		if (dist < 1)
			return ArrivalDirection.StraightAhead; 
		else
		{
			double sign = Math.signum((lon1 - lon0) * (destination.y - lat0) - (lat1 - lat0) * (destination.x - lon0));
			if (sign == 0)
				return ArrivalDirection.StraightAhead;
			else if (sign == 1)
				return ArrivalDirection.Left;
			else
				return ArrivalDirection.Right;
		}
	}

	private int getWayPointEndIndex(int startIndex, InstructionType instrType, Instruction instr)
	{
		if (instrType == InstructionType.FINISH)
			return startIndex;
		else
			return startIndex + instr.getPoints().size();
	}

	private PointList getNextSegPoints(List<GHResponse> routes, int routeIndex, int segIndex)
	{
		if (routeIndex >= 0 && routeIndex < routes.size())
		{
			GHResponse resp = routes.get(routeIndex);
			InstructionList instructions = resp.getBest().getInstructions();
			if (segIndex < instructions.size())
				return instructions.get(segIndex).getPoints();
		}

		return null;
	}

	private RouteStepManeuver calcManeuver(InstructionType instrType, PointList prevSegPoints, PointList segPoints, PointList nextSegPoints)
	{
		RouteStepManeuver maneuver = new RouteStepManeuver();
		int bearingBefore = 0;
		int bearingAfter = 0;

		if (nextSegPoints != null)
		{
			if (instrType == InstructionType.DEPART)
			{
				double lon0 = segPoints.getLon(0);
				double lat0 = segPoints.getLat(0);
				double lon1, lat1;
				maneuver.setLocation(new Coordinate(lon0, lat0));

				if (segPoints.size() == 1)
				{
					lon1  = nextSegPoints.getLon(0);
					lat1  = nextSegPoints.getLat(0);
				}				
				else
				{
					lon1  = segPoints.getLon(1);
					lat1  = segPoints.getLat(1);
				}

				bearingAfter = (int)Math.round(_angleCalc.calcAzimuth(lat0, lon0, lat1, lon1));
			}
			else
			{
				int locIndex = prevSegPoints.size() - 1;
				double lon0 = prevSegPoints.getLon(locIndex);
				double lat0 = prevSegPoints.getLat(locIndex);
				double lon1 = segPoints.getLon(0);
				double lat1 = segPoints.getLat(0);

				if (instrType != InstructionType.FINISH)
				{
					if (segPoints.size() == 1)
					{ 
						if (nextSegPoints != null)
						{
							double lon2 = nextSegPoints.getLon(0);
							double lat2 = nextSegPoints.getLat(0);

							bearingAfter = (int)Math.round(_angleCalc.calcAzimuth(lat1, lon1, lat2, lon2));
						}
					}
					else
					{
						double lon2 = segPoints.getLon(1);
						double lat2 = segPoints.getLat(1);

						bearingAfter = (int)Math.round(_angleCalc.calcAzimuth(lat1, lon1, lat2, lon2));
					}
				}

				bearingBefore  = (int)Math.round(_angleCalc.calcAzimuth(lat0, lon0, lat1, lon1));
				maneuver.setLocation(new Coordinate(lon1, lat1));
			}
		}

		maneuver.setBearingBefore(bearingBefore);
		maneuver.setBearingAfter(bearingAfter);

		return maneuver;
	}

	private boolean canMergeInstructions(String name, String prevName)
	{
		if (prevName == null)
			return false;

		if (name.length() > prevName.length())
		{
			int pos = name.indexOf(prevName);
			if (pos >= 0 && !Helper.isEmpty(prevName))
				return true;
		}
		else
		{
			int pos = prevName.indexOf(name);
			if (pos >= 0 && !Helper.isEmpty(name))
				return true;
		}

		return false;
	}

	private String mergeInstructionsOrdered(String name, String prevName)
	{
		int pos = name.indexOf(prevName);
		if (pos >= 0)
		{
			pos = pos + prevName.length() + 1;
			if (pos < name.length())
			{
				String appendix = name.substring(pos, name.length());

				if (appendix.length() > 1 && appendix.startsWith(","))
					appendix = appendix.substring(1);

				appendix = appendix.trim();

				if (isValidAppendix(appendix))
				{
					if (_nameAppendix != null)
						_nameAppendix += ", ";
					else
						_nameAppendix = "";

					_nameAppendix += appendix;
				}

				return prevName;
			}
		}

		return name;
	}

	private String mergeInstructions(String name, String prevName)
	{
		if (name.length() > prevName.length())
			return mergeInstructionsOrdered(name, prevName);
		else
			return mergeInstructionsOrdered(prevName, name);
	}

	private boolean isValidAppendix(String name)
	{
		if (name == null)
			return false;

		if (_nameAppendix == null)
			return StringUtility.containsDigit(name);
		else
			return _nameAppendix.indexOf(name) == -1 && StringUtility.containsDigit(name);   
	}


	private boolean isTurnInstruction(InstructionType instrType) {
		if (instrType == InstructionType.TURN_LEFT || instrType == InstructionType.TURN_SLIGHT_LEFT
				|| instrType == InstructionType.TURN_SHARP_LEFT || instrType == InstructionType.TURN_RIGHT
				|| instrType == InstructionType.TURN_SLIGHT_RIGHT || instrType == InstructionType.TURN_SHARP_RIGHT)
			return true;
		else
			return false;
	}

	private InstructionType getInstructionType(boolean isDepart, Instruction instr)
	{
		if (isDepart)
			return InstructionType.DEPART;

		int sign = instr.getSign();
		if (sign == Instruction.CONTINUE_ON_STREET)
			return InstructionType.CONTINUE;
		else if (sign == Instruction.TURN_LEFT)
			return InstructionType.TURN_LEFT;
		else if (sign == Instruction.TURN_RIGHT)
			return InstructionType.TURN_RIGHT;
		else if (sign == Instruction.TURN_SHARP_LEFT)
			return InstructionType.TURN_SHARP_LEFT;
		else if (sign == Instruction.TURN_SHARP_RIGHT)
			return InstructionType.TURN_SHARP_RIGHT;
		else if (sign == Instruction.TURN_SLIGHT_LEFT)
			return InstructionType.TURN_SLIGHT_LEFT;
		else if (sign == Instruction.TURN_SLIGHT_RIGHT)
			return InstructionType.TURN_SLIGHT_RIGHT;
		else if (sign == Instruction.TURN_SLIGHT_RIGHT)
			return InstructionType.TURN_SLIGHT_RIGHT;
		else if (sign == Instruction.USE_ROUNDABOUT)
			return InstructionType.ENTER_ROUNDABOUT;
		else if (sign == Instruction.LEAVE_ROUNDABOUT)
			return InstructionType.EXIT_ROUNDABOUT;
		else if (sign == Instruction.FINISH)
			return InstructionType.FINISH;

		return InstructionType.CONTINUE;
	}

	private CardinalDirection calcDirection(double lat1, double lon1, double lat2, double lon2 )
	{
		double orientation = - _angleCalc.calcOrientation(lat1, lon1, lat2, lon2);
		orientation = Helper.round4(orientation + Math.PI / 2);
		if (orientation < 0)
			orientation += 2 * Math.PI;

		double degree = Math.toDegrees(orientation);
		return _directions[(int)Math.floor(((degree+ 22.5) % 360) / 45)];
	}
}
