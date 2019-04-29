package beam.analysis.via;


import org.matsim.api.core.v01.events.Event;
import org.matsim.core.events.algorithms.EventWriterXML;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

// for some reason via is expecting wait2link event, instead of vehicle enters traffic event (dec. 2017)
// perhaps also has to do with the fact, that we are not using most uptodate matsim version
// this class can be replaced by default EventWriterXML, as this issue gets resolved.

public class EventWriterXML_viaCompatible extends EventWriterXML {
    private static final String TNC = "ride";
    private static final String BUS = "SF";
    private static final String CAR = "car";
    private boolean eventsForFullVersionOfVia;
    HashMap<String, HashSet<String>> filterPeopleForViaDemo = new HashMap<>();
    HashMap<String, Integer> maxPeopleForViaDemo = new HashMap<>();

    public EventWriterXML_viaCompatible(final String outFileName, boolean eventsForFullVersionOfVia) {
        super(outFileName);
        this.eventsForFullVersionOfVia = eventsForFullVersionOfVia;

        filterPeopleForViaDemo.put(CAR, new HashSet<>());
        filterPeopleForViaDemo.put(BUS, new HashSet<>());
        filterPeopleForViaDemo.put(TNC, new HashSet<>());

        maxPeopleForViaDemo.put(CAR, 420);
        maxPeopleForViaDemo.put(BUS, 50);
        maxPeopleForViaDemo.put(TNC, 30);

    }

    private boolean addPersonToEventsFile(String person) {

        if (eventsForFullVersionOfVia){
            return true;
        }

        String personLabel;

        if (person.contains(BUS)) {
            personLabel = BUS;
        } else if (person.contains(TNC)) {
            personLabel = TNC;
        } else {
            personLabel = CAR;
        }

        if (filterPeopleForViaDemo.get(personLabel).size() < maxPeopleForViaDemo.get(personLabel) || filterPeopleForViaDemo.get(personLabel).contains(person)) {
            filterPeopleForViaDemo.get(personLabel).add(person);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void handleEvent(final Event event) {

        // select 500 agents for sf-light demo in via
        //if (outFileName.contains("sf-light")){
        Map<String, String> eventAttributes = event.getAttributes();
        String person = eventAttributes.get("person");
        String vehicle = eventAttributes.get("vehicle");

        if (person != null) {
            if (!addPersonToEventsFile(person)) return;
        } else {
            if (!addPersonToEventsFile(vehicle)) return;
        }
        //}

        if (eventAttributes.get("type").equalsIgnoreCase("vehicle enters traffic")) {
            eventAttributes.put("type", "wait2link");
        }
        super.handleEvent(event);
    }
}

