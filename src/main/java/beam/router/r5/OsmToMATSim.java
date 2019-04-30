package beam.router.r5;

import com.conveyal.osmlib.Way;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.io.OsmNetworkReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by Andrew A. Campbell on 7/25/17.
 * This class is based off of MATSim's OsmNetworkReader. Particularly, it is used to generate all the link
 * attributes in the MATSim network based on the OSM way's tags the same way OsmNetworkReader does.
 */

public class OsmToMATSim extends OsmNetworkReader {

    private final static Logger log = LoggerFactory.getLogger(OsmToMATSim.class);

    private final static String TAG_LANES = "lanes";
    private final static String TAG_HIGHWAY = "highway";
    private final static String TAG_MAXSPEED = "maxspeed";
    private final static String TAG_JUNCTION = "junction";
    private final static String TAG_ONEWAY = "oneway";

    private final static double MOTORWAY_LINK_RATIO = 80.0/120;
    private final static double PRIMARY_LINK_RATIO = 60.0/80;
    private final static double TRUNK_LINK_RATIO = 50.0/80;
    private final static double SECONDARY_LINK_RATIO = 0.66;
    private final static double TERTIARY_LINK_RATIO = 0.66;

    public final Map<String, OsmHighwayDefaults> highwayDefaults = new HashMap<>();
    private final Set<String> unknownMaxspeedTags = new HashSet<>();
    private final Set<String> unknownLanesTags = new HashSet<>();
    private final Network mNetwork;

    public OsmToMATSim(final Network mNetwork, boolean useBEAMHighwayDefaults) {
        super(mNetwork, null, false);
        this.mNetwork = mNetwork;

        if (useBEAMHighwayDefaults) {
            log.info("Falling back to default values.");
            this.setHighwayDefaults(1, "motorway", 2, toMetersPerSecond(75), 1.0, 2000, true);
            this.setHighwayDefaults(1, "motorway_link", 1, MOTORWAY_LINK_RATIO * toMetersPerSecond(75), 1.0, 1500, true);
            this.setHighwayDefaults(3, "primary", 1, toMetersPerSecond(65), 1.0, 1500);
            this.setHighwayDefaults(3, "primary_link", 1, PRIMARY_LINK_RATIO * toMetersPerSecond(65), 1.0, 1500);
            this.setHighwayDefaults(2, "trunk", 1, toMetersPerSecond(60), 1.0, 2000);
            this.setHighwayDefaults(2, "trunk_link", 1, TRUNK_LINK_RATIO * toMetersPerSecond(60), 1.0, 1500);

            this.setHighwayDefaults(4, "secondary", 1, toMetersPerSecond(60), 1.0, 1000);
            this.setHighwayDefaults(4, "secondary_link", 1, SECONDARY_LINK_RATIO * toMetersPerSecond(60), 1.0, 1000);
            this.setHighwayDefaults(5, "tertiary", 1, toMetersPerSecond(55), 1.0, 600);
            this.setHighwayDefaults(5, "tertiary_link", 1, TERTIARY_LINK_RATIO * toMetersPerSecond(55), 1.0, 600);

            this.setHighwayDefaults(6, "minor", 1, toMetersPerSecond(25), 1.0, 600);
            this.setHighwayDefaults(6, "residential", 1, toMetersPerSecond(25), 1.0, 600);
            this.setHighwayDefaults(6, "living_street", 1, toMetersPerSecond(25), 1.0, 300);

            this.setHighwayDefaults(6, "unclassified", 1, toMetersPerSecond(28), 1.0, 600);
        }
    }


    public Link createLink(final Way way, long osmID, Integer r5ID, final Node fromMNode, final Node toMNode,
                           final double length, HashSet<String> flagStrings) {
        String highway = way.getTag(TAG_HIGHWAY);
        if (highway == null) {
            highway = "unclassified";
        }
        OsmHighwayDefaults defaults = this.highwayDefaults.get(highway);

        if (defaults == null) {
            defaults = this.highwayDefaults.get("unclassified");
        }

        double nofLanes = defaults.lanesPerDirection;
        double laneCapacity = defaults.laneCapacity;
        double freespeed = defaults.freespeed;
        double freespeedFactor = defaults.freespeedFactor;
        boolean oneway = defaults.oneway;
        boolean onewayReverse = false;

        // check if there are tags that overwrite defaults
        // - check tag "junction"
        if ("roundabout".equals(way.getTag(TAG_JUNCTION))) {
            // if "junction" is not set in tags, get() returns null and equals() evaluates to false
            oneway = true;
        }

        // check tag "oneway"
        String onewayTag = way.getTag(TAG_ONEWAY);
        if (onewayTag != null) {
            if ("yes".equals(onewayTag)) {
                oneway = true;
            } else if ("true".equals(onewayTag)) {
                oneway = true;
            } else if ("1".equals(onewayTag)) {
                oneway = true;
            } else if ("-1".equals(onewayTag)) {
                onewayReverse = true;
                oneway = false;
            } else if ("no".equals(onewayTag)) {
                oneway = false; // may be used to overwrite defaults
            } else {
                log.warn("Could not interpret oneway tag:" + onewayTag + ". Ignoring it.");
            }
        }

        // In case trunks, primary and secondary roads are marked as oneway,
        // the default number of lanes should be two instead of one.
        if (highway.equalsIgnoreCase("trunk") || highway.equalsIgnoreCase("primary") || highway.equalsIgnoreCase("secondary")) {
            if ((oneway || onewayReverse) && nofLanes == 1.0) {
                nofLanes = 2.0;
            }
        }

        String maxspeedTag = way.getTag(TAG_MAXSPEED);
        if (maxspeedTag != null) {
            try {
                freespeed = Double.parseDouble(maxspeedTag) / 3.6; // convert km/h to m/s
            } catch (NumberFormatException e) {
                if (!this.unknownMaxspeedTags.contains(maxspeedTag)) {
                    this.unknownMaxspeedTags.add(maxspeedTag);
                    log.warn("Could not parse maxspeed tag:" + e.getMessage() + ". Ignoring it.");
                }
            }
        }

        // check tag "lanes"
        String lanesTag = way.getTag(TAG_LANES);
        if (lanesTag != null) {
            try {
                double totalNofLanes = Double.parseDouble(lanesTag);
                if (totalNofLanes > 0) {
                    nofLanes = totalNofLanes;

                    //By default, the OSM lanes tag specifies the total number of lanes in both directions.
                    //So if the road is not oneway (onewayReverse), let's distribute them between both directions
                    //michalm, jan'16
                    if (!oneway && !onewayReverse) {
                        nofLanes /= 2.;
                    }
                }
            } catch (Exception e) {
                if (!this.unknownLanesTags.contains(lanesTag)) {
                    this.unknownLanesTags.add(lanesTag);
                    log.warn("Could not parse lanes tag:" + e.getMessage() + ". Ignoring it.");
                }
            }
        }

        // create the link(s)
        double capacity = nofLanes * laneCapacity;

        boolean scaleMaxSpeed = false;
        if (scaleMaxSpeed) {
            freespeed = freespeed * freespeedFactor;
        }

        // only create link, if both nodes were found, node could be null, since nodes outside a layer were dropped
        Id<Node> fromId = fromMNode.getId();
        Id<Node> toId = toMNode.getId();
        if (this.mNetwork.getNodes().get(fromId) != null && this.mNetwork.getNodes().get(toId) != null) {
            Link l = this.mNetwork.getFactory().createLink(Id.create(r5ID, Link.class), this.mNetwork.getNodes().get(fromId), this.mNetwork.getNodes().get(toId));
            l.setLength(length);
            l.setFreespeed(freespeed);
            l.setCapacity(capacity);
            l.setNumberOfLanes(nofLanes);
            l.setAllowedModes(flagStrings);
            NetworkUtils.setOrigId(l, Long.toString(osmID));
            NetworkUtils.setType(l, highway);
            return l;
        } else {
            throw new RuntimeException();
        }
    }

    public static double toMetersPerSecond(double milesPerHour) {
        return milesPerHour * 1.60934 * 1000 / 3600;
    }

}
