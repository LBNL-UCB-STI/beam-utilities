package beam.utils

class DistributedRandomNumberGenerator(probability: Double) {
  private val distributions: Map[Boolean, Double] = Map[Boolean,Double](true -> probability, false -> (1.0 - probability))

  def getDistributedRandomNumber: Boolean = {
    val rand = Math.random
    var tempDist = 0.0
    distributions.keys.foreach(key => {
      tempDist+=distributions(key)
      if (rand <= tempDist)
        return key
    })
    true
  }
}