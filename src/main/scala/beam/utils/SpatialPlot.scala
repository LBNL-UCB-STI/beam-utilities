package beam.utils

import java.awt.image.BufferedImage
import java.io.File

import javax.imageio.ImageIO

// frame is good for text lables as they can be outside of the area otherwise
class PlotImage() {

  def writeImage(path: String, bufferedImage: BufferedImage): Unit = {
    ImageIO.write(bufferedImage, "PNG", new File(path))
  }
}
