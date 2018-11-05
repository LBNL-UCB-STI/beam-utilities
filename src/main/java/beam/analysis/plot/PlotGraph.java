package beam.analysis.plot;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.utils.io.UncheckedIOException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class PlotGraph {


    public void writeGraphic( OutputDirectoryHierarchy CONTROLLER_IO, Integer iteration, String mode , String fileName , Map<String, TreeMap<Integer, Integer>> personEnterCount , Map<String, TreeMap<Integer, Integer>> personExitCount , Map<String, TreeMap<Integer, Integer>> onRoutes  , String xAxisLabel , int binSize) {
        try {
            String filename = fileName + "_" + mode + ".png";
            String path = CONTROLLER_IO.getIterationFilename(iteration, filename);
            int index = path.lastIndexOf("/");
            File outDir = new File(path.substring(0, index) + "/tripHistogram");
            if (!outDir.isDirectory()) Files.createDirectories(outDir.toPath());
            String newPath = outDir.getPath() + path.substring(index);
            ChartUtilities.saveChartAsPNG(new File(newPath), getGraphic(mode, iteration , personEnterCount , personExitCount, onRoutes , xAxisLabel , binSize), 1024, 768);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public JFreeChart getGraphic(String mode, int iteration , Map<String, TreeMap<Integer, Integer>> personEnterCount , Map<String, TreeMap<Integer, Integer>> personExitCount , Map<String, TreeMap<Integer, Integer>> onRoutes , String xAxisLabel , int binSize ) {

        final XYSeriesCollection xyData = new XYSeriesCollection();
        final XYSeries enterSeries = new XYSeries("Enter", false, true);
        final XYSeries exitSeries = new XYSeries("Leave", false, true);
        final XYSeries onRouteSeries = new XYSeries("en route", false, true);

        Map<Integer, Integer> personEnter = personEnterCount.get(mode);
        if (personEnter != null && personEnter.size() > 0) {
            Set<Integer> enterKeys = personEnter.keySet();
            for (Integer key : enterKeys) {
                enterSeries.add(key, personEnter.get(key));
            }
        }


        Map<Integer, Integer> personExit = personExitCount.get(mode);
        if (personExit != null && personExit.size() > 0) {
            Set<Integer> exitKeys = personExit.keySet();
            for (Integer key : exitKeys) {
                exitSeries.add(key, personExit.get(key));
            }
        }


        Map<Integer, Integer> indexCount = onRoutes.get(mode);
        if (indexCount != null && indexCount.size() > 0) {
            Set<Integer> indexKeys = indexCount.keySet();
            for (Integer key : indexKeys) {
                onRouteSeries.add(key, indexCount.get(key));
            }
        }

        xyData.addSeries(enterSeries);
        xyData.addSeries(exitSeries);
        xyData.addSeries(onRouteSeries);

        final JFreeChart chart = ChartFactory.createXYLineChart(
                "Trip Histogram, " + mode + ", it." + iteration,
                xAxisLabel.replace("<?>", String.valueOf(binSize)), "# persons",
                xyData,
                PlotOrientation.VERTICAL,
                true,   // legend
                false,   // tooltips
                false   // urls
        );


        XYPlot plot = chart.getXYPlot();
        plot.getRangeAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        final CategoryAxis axis1 = new CategoryAxis("sec");
        axis1.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 7));
        plot.setDomainAxis(new NumberAxis(xAxisLabel.replace("<?>",String.valueOf(binSize))));

        plot.getRenderer().setSeriesStroke(0, new BasicStroke(2.0f));
        plot.getRenderer().setSeriesStroke(1, new BasicStroke(2.0f));
        plot.getRenderer().setSeriesStroke(2, new BasicStroke(2.0f));
        plot.setBackgroundPaint(Color.white);
        plot.setRangeGridlinePaint(Color.gray);
        plot.setDomainGridlinePaint(Color.gray);

        return chart;
    }


    public int getBinIndex(final double time , int binSize , int numOfBins) {
        int bin = (int) (time / binSize);
        if (bin >= numOfBins) {
            return numOfBins;
        }
        return bin;
    }


}
