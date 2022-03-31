/* *********************************************************************** *
 * project: org.matsim.*
 * CalcLinkStats.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package beam.utils;


import org.matsim.analysis.CalcLinkStats;
import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.groups.TravelTimeCalculatorConfigGroup;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class BeamCalcLinkStats {

    private final static Logger log = LoggerFactory.getLogger(CalcLinkStats.class);
    private static final String NEW_LINE_SEPARATOR = "\n";
    private static final String[] statType = {"MIN", "AVG"};
    private static final int NOF_STATS = 2;
    private final Map<Id<Link>, LinkData> linkData;
    private final int nofHours;
    private final Network network;
    private int count = 0;

    @Inject
    public BeamCalcLinkStats(final Network network, final TravelTimeCalculatorConfigGroup ttConfigGroup) {
        this.network = network;
        linkData = new ConcurrentHashMap<>();
        nofHours = (int) TimeUnit.SECONDS.toHours(ttConfigGroup.getMaxTime());
        reset();
    }

    public void addData(final VolumesAnalyzer analyzer, final TravelTime ttimes) {
        addData(analyzer, ttimes, null);
    }

    public void addData(final VolumesAnalyzer analyzer, final TravelTime ttimes, String mode) {
        count++;
        // TODO verify ttimes has hourly timeBin-Settings

        // go through all links
        for (Id<Link> linkId : linkData.keySet()) {

            // retrieve link from link ID
            Link link = network.getLinks().get(linkId);

            // get the volumes for the link ID from the analyzier
            double[] volumes = mode == null ?
                    analyzer.getVolumesPerHourForLink(linkId) : analyzer.getVolumesPerHourForLink(linkId, mode);

            // get the destination container for the data from link data (could have gotten this through iterator right away)
            LinkData data = linkData.get(linkId);

            // prepare the sum variables (for volumes);
            long sumVolumes = 0; // daily (0-24) sum

            // go through all hours:
            for (int hour = 0; hour < nofHours; hour++) {

                // get travel time for hour
                double ttime = ttimes.getLinkTravelTime(link, hour * 3600, null, null);

                // add for daily sum:
                sumVolumes += volumes[hour];

                // the following has something to do with the fact that we are doing this for multiple iterations.  So there are variations.
                // this collects min and max.  There is, however, no good control over how many iterations this is collected.
                if (count == 1) {
                    data.volumes[LinkData.MIN][hour] = volumes[hour];
                    data.ttimes[LinkData.MIN][hour] = ttime;
                } else {
                    if (volumes[hour] < data.volumes[LinkData.MIN][hour]) data.volumes[LinkData.MIN][hour] = volumes[hour];
                    if (ttime < data.ttimes[LinkData.MIN][hour]) data.ttimes[LinkData.MIN][hour] = ttime;
                }

                // this is the regular summing up for each hour
                data.volumes[LinkData.SUM][hour] += volumes[hour];
                data.ttimes[LinkData.SUM][hour] += volumes[hour] * ttime;
            }
            // dataVolumes[.][nofHours] are daily (0-24) values
            if (count == 1) {
                data.volumes[LinkData.MIN][nofHours] = sumVolumes;
                data.volumes[LinkData.SUM][nofHours] = sumVolumes;
            } else {
                if (sumVolumes < data.volumes[LinkData.MIN][nofHours]) data.volumes[LinkData.MIN][nofHours] = sumVolumes;
                data.volumes[LinkData.SUM][nofHours] += sumVolumes;
            }
        }
    }

    public void reset() {
        linkData.clear();
        count = 0;
        log.info(" resetting `count' to zero.  This info is here since we want to check when this" +
                " is happening during normal simulation runs.  kai, jan'11");

        // initialize our data-table
        for (Link link : network.getLinks().values()) {
            LinkData data = new LinkData(new double[NOF_STATS][nofHours + 1], new double[NOF_STATS][nofHours]);
            linkData.put(link.getId(), data);
        }

    }

    public void writeFile(final String filename) {
        BufferedWriter out = null;
        try {
            out = IOUtils.getBufferedWriter(filename);

            // write header
            out.write("link,from,to,hour,length,freespeed,capacity,stat,volume,traveltime");

            out.write(NEW_LINE_SEPARATOR);

            // write data
            for (Map.Entry<Id<Link>, LinkData> entry : linkData.entrySet()) {

                for (int i = 0; i < nofHours; i++) {
                    Id<Link> linkId = entry.getKey();
                    LinkData data = entry.getValue();
                    Link link = network.getLinks().get(linkId);

                    out.write(linkId.toString());
                    writeCommaAndStr(out, link.getFromNode().getId().toString());

                    writeCommaAndStr(out, link.getToNode().getId().toString());

                    //WRITE HOUR
                    writeCommaAndStr(out, Double.toString(i));

                    writeCommaAndStr(out, Double.toString(link.getLength()));

                    writeCommaAndStr(out, Double.toString(link.getFreespeed()));

                    writeCommaAndStr(out, Double.toString(link.getCapacity()));

                    writeCommaAndStr(out, statType[LinkData.SUM]);

                    //WRITE VOLUME
                    writeCommaAndStr(out, Double.toString((data.volumes[LinkData.SUM][i]) / count));

                    //WRITE TRAVELTIME
                    writeCommaAndStr(out, Double.toString(data.calculateAverageTravelTime(i)));

                    out.write(NEW_LINE_SEPARATOR);
                }
            }

            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.warn("Could not close output-stream.", e);
                }
            }
        }
    }

    private void writeCommaAndStr(BufferedWriter out, String str) throws IOException {
        out.write(',');
        out.write(str);
    }

    public Map<Id<Link>, LinkData> getLinkData() {
        return linkData;
    }

    public int getNofHours() {
        return nofHours;
    }

    public static class LinkData {
        static final int MIN = 0;
        static final int SUM = 1;
        final double[][] volumes;
        final double[][] ttimes;

        LinkData(final double[][] linksVolumes, final double[][] linksTTimes) {
            volumes = linksVolumes.clone();
            ttimes = linksTTimes.clone();
        }
        
        public double getMinVolume(int hour) {
            return volumes[MIN][hour];
        }
        
        public double getSumVolume(int hour) {
            return volumes[SUM][hour];
        }
        
        public double getMinTravelTime(int hour) {
            return ttimes[MIN][hour];
        }
        
        public double getSumTravelTime(int hour) {
            return ttimes[SUM][hour];
        }
        
        public double calculateAverageTravelTime(int hour) {
            double ttimesMin = this.ttimes[LinkData.MIN][hour];
            if (this.volumes[LinkData.SUM][hour] == 0) {
                // nobody traveled along the link in this hour, so we cannot calculate an average
                // use the value available or the minimum instead (min and max should be the same, =freespeed)
                double ttsum = this.ttimes[LinkData.SUM][hour];
                if (ttsum != 0.0) {
                    return ttsum;
                } else {
                    return ttimesMin;
                }
            } else {
                double ttsum = this.ttimes[LinkData.SUM][hour];
                if (ttsum == 0) {
                    return ttimesMin;
                } else {
                    return ttsum / this.volumes[LinkData.SUM][hour];
                }
            }
        }
    }
}
