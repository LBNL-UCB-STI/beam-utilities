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

public class BeamCalcLinkStats extends CalcLinkStats{

    private final static Logger log = LoggerFactory.getLogger(BeamCalcLinkStats.class);
    private static final int MIN = 0;
    private static final int MAX = 1;
    private static final int SUM = 2;
    private static final String[] statType = {"MIN", "MAX", "AVG"};
    private int count = 0;
    private final int nofHours;

    @Inject
    public BeamCalcLinkStats(final Network network, final TravelTimeCalculatorConfigGroup ttConfigGroup) {
        super(network);
        nofHours = (int)TimeUnit.SECONDS.toHours(ttConfigGroup.getMaxTime());
        setNofHours(nofHours);
    }

    public void writeFile(final String filename) {
        BufferedWriter out = null;
        try {
            out = IOUtils.getBufferedWriter(filename);

            // write header
            out.write("link,from,to,hour,length,freespeed,capacity,stat,volume,traveltime");

            out.write("\n");

            Map<Id<Link>, ? extends Link> links = getNetwork().getLinks();
            // write data
            for (Map.Entry<Id<Link>, LinkData> entry : getLinkData().entrySet()) {

                for (int i = 0; i <= this.nofHours; i++) {
                    for (int j = MIN; j <= SUM; j++) {
                        Id<Link> linkId = entry.getKey();
                        LinkData data = entry.getValue();
                        Link link = links.get(linkId);

                        out.write(linkId.toString());
                        writeCommaAndStr(out, link.getFromNode().getId().toString());

                        writeCommaAndStr(out, link.getToNode().getId().toString());

                        //WRITE HOUR
                        if (i < this.nofHours) {
                            writeCommaAndStr(out, Double.toString(i));
                        } else {
                            out.write(",");
                            out.write( Double.toString(0));
                            out.write(" - ");
                            out.write(Double.toString(this.nofHours));
                        }

                        writeCommaAndStr(out, Double.toString(link.getLength()));

                        writeCommaAndStr(out, Double.toString(link.getFreespeed()));

                        writeCommaAndStr(out, Double.toString(link.getCapacity()));

                        //WRITE STAT_TYPE
                        writeCommaAndStr(out, statType[j]);

                        //WRITE VOLUME
                        if (j == SUM) {
                            writeCommaAndStr(out, Double.toString((data.volumes[j][i]) / this.count));
                        } else {
                            writeCommaAndStr(out, Double.toString(data.volumes[j][i]));
                        }

                        //WRITE TRAVELTIME

                        if (j == MIN && i < this.nofHours) {
                            String ttimesMin = Double.toString(data.ttimes[MIN][i]);
                            writeCommaAndStr(out, ttimesMin);

                        } else if (j == SUM && i < this.nofHours) {
                            String ttimesMin = Double.toString(data.ttimes[MIN][i]);
                            if (data.volumes[SUM][i] == 0) {
                                // nobody traveled along the link in this hour, so we cannot calculate an average
                                // use the value available or the minimum instead (min and max should be the same, =freespeed)
                                double ttsum = data.ttimes[SUM][i];
                                if (ttsum != 0.0) {
                                    writeCommaAndStr(out, Double.toString(ttsum));
                                } else {
                                    writeCommaAndStr(out, ttimesMin);
                                }
                            } else {
                                double ttsum = data.ttimes[SUM][i];
                                if (ttsum == 0) {
                                    writeCommaAndStr(out, ttimesMin);
                                } else {
                                    writeCommaAndStr(out, Double.toString(ttsum / data.volumes[SUM][i]));
                                }
                            }
                        } else if (j == MAX && i < this.nofHours) {
                            writeCommaAndStr(out, Double.toString(data.ttimes[MAX][i]));
                        }

                        out.write("\n");
                    }
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
    private void writeCommaAndStr(BufferedWriter out, String str) throws IOException{
        out.write(',');
        out.write(str);
    }
}
