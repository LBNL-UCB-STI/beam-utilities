package beam.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DistributedRandomNumberGenerator {

    private final Map<Boolean, Double> distributions = new HashMap<>();
    public DistributedRandomNumberGenerator(double probability){
        distributions.put(true, probability);
        distributions.put(false, 1.0 - probability);
    }

    public boolean getDistributedRandomNumber(){
        double rand = Math.random();
        double tempDist = 0.0;
        Set<Boolean> keys =distributions.keySet();
        for(Boolean key: keys){
            tempDist+=distributions.get(key);
            if (rand <= tempDist)
                return key;
        }
        return true;
    }
}
