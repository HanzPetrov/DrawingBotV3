package drawingbot.plotting;

import drawingbot.DrawingBotV3;
import drawingbot.api.IGeometryFilter;
import drawingbot.geom.basic.IGeometry;
import drawingbot.javafx.observables.ObservableDrawingPen;
import drawingbot.javafx.observables.ObservableDrawingSet;
import drawingbot.pfm.PFMFactory;
import drawingbot.utils.EnumDistributionOrder;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.canvas.GraphicsContext;

import java.awt.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

public class PlottedDrawing {

    public List<IGeometry> geometries;
    public long vertexCount;

    public SimpleIntegerProperty displayedShapeMin = new SimpleIntegerProperty(-1);
    public SimpleIntegerProperty displayedShapeMax = new SimpleIntegerProperty(-1);
    public boolean ignoreWeightedDistribution = false; //used for disabling distributions within sub tasks, will use the pfms default

    public final Map<Integer, PlottedGroup> groups;
    public PlottedGroup defaultGroup;
    private int groupID = 0;
    public Map<ObservableDrawingPen, Integer> perPenStats = new HashMap<>();

    public PlottedDrawing(){
        this.geometries = Collections.synchronizedList(new ArrayList<>());
        this.groups = Collections.synchronizedMap(new HashMap<>());
    }

    public PlottedDrawing(ObservableDrawingSet penSet, PFMFactory<?> pfmFactory){
        this.geometries = Collections.synchronizedList(new ArrayList<>());
        this.groups = Collections.synchronizedMap(new HashMap<>());
        this.defaultGroup = createPlottedGroup(penSet, pfmFactory);
    }

    /**
     * Copies the base groups of the plotted drawing only
     */
    public void copyBase(PlottedDrawing reference){
        for(PlottedGroup group : reference.groups.values()){
            PlottedGroup newGroup = new PlottedGroup(group.groupID, group.drawingSet, group.pfmFactory);
            groups.put(newGroup.groupID, newGroup);
        }
        defaultGroup = groups.get(0);
    }

    /**
     * Creates a full clone of the plotted drawing and all of the geometries used by it
     */
    public void copyAll(PlottedDrawing reference){
        copyBase(reference);
        reference.geometries.forEach(g -> addGeometry(g.copyGeometry()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    public int getDisplayedShapeMin(){
        if(displayedShapeMin.get() == -1){
            return 0;
        }
        return displayedShapeMin.get();
    }

    public int getDisplayedShapeMax(){
        if(displayedShapeMax.get() == -1){
            return getGeometryCount();
        }
        return displayedShapeMax.get();
    }

    public int getGeometryCount(){
        return geometries.size();
    }

    public long getVertexCount(){
        return vertexCount;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    public void addGeometry(IGeometry geometry) {
        geometry.setGeometryIndex(geometries.size());
        geometries.add(geometry);
        vertexCount += geometry.getVertexCount();

        addGeometryToGroups(geometry);
    }

    public void addGeometry(IGeometry geometry, PlottedGroup group) {
        assert groups.containsValue(group);

        geometry.setGroupID(group.getGroupID());
        addGeometry(geometry);
    }

    public void addGeometry(List<IGeometry> orderedGeometries) {
        orderedGeometries.forEach(this::addGeometry);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    public int getNextGroupID(){
        groupID++;
        return groups.get(groupID) == null ? groupID : getNextGroupID();
    }

    public PlottedGroup getDefaultGroup(){
        return defaultGroup;
    }

    public PlottedGroup getPlottedGroup(int groupID){
        return groups.getOrDefault(groupID, defaultGroup);
    }

    public PlottedGroup createPlottedGroup(ObservableDrawingSet penSet, PFMFactory<?> pfmFactory){
        PlottedGroup plottedGroup = new PlottedGroup(getNextGroupID(), penSet, pfmFactory);
        groups.put(plottedGroup.groupID, plottedGroup);
        return plottedGroup;
    }

    public PlottedGroup getMatchingPlottedGroup(PlottedGroup group, boolean forExport){
        for(PlottedGroup plottedGroup : groups.values()){
            if(plottedGroup.canMerge(group, forExport)){
                return plottedGroup;
            }
        }
        return null;
    }

    public void reorderGroups(List<PlottedGroup> newOrder){
        int index = 0;
        for(PlottedGroup plottedGroup : newOrder){
            if(plottedGroup.getGroupID() != index){
                plottedGroup.changeGroupID(index);
            }
            index++;
        }
    }

    /**
     * This is a destructive action and invalidates the provided plotted group
     */
    public void mergePlottedGroup(PlottedGroup plottedGroup, boolean simplify, boolean forExport){
        if(simplify){
            for(PlottedGroup group : groups.values()){
                if(group.canMerge(plottedGroup, forExport)){
                    plottedGroup.geometries.forEach(g -> addGeometry(g, group));
                    return;
                }
            }
        }

        PlottedGroup newGroup = createPlottedGroup(plottedGroup.drawingSet, plottedGroup.pfmFactory);
        for(IGeometry geometry : plottedGroup.geometries){
            addGeometry(geometry, newGroup);
        }
    }

    /**
     * This is a destructive action and invalidates the provided plotted drawing and all of its plotted groups
     */
    public void mergePlottedDrawing(PlottedDrawing drawing, boolean simplify, boolean forExport){
        drawing.groups.values().forEach(g -> mergePlottedGroup(g, simplify, forExport));
    }

    public void addGeometryToGroups(IGeometry geometry){
        getPlottedGroup(geometry.getGroupID()).addGeometry(geometry);
    }

    public void addGroupPFMType(int groupID, PFMFactory<?> factory){
        groups.get(groupID).setPFMFactory(factory);
    }

    public PFMFactory<?> getGroupPFMType(int groupID){
        return groups.get(groupID).pfmFactory;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    public void clearGeometries(){
        geometries.clear();
        vertexCount = 0;
        groups.values().forEach(PlottedGroup::clearGeometries);
    }


    public void reset(){
        clearGeometries();
        displayedShapeMax = null;
        displayedShapeMin = null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////


    public void updatePenDistribution(){
        if(!ignoreWeightedDistribution){
            for(PlottedGroup group : groups.values()){
                group.updateDistribution(this);
            }
            perPenStats = getPerPenGeometryStats(this);
            updatePerPenGeometryStats(this, perPenStats);
        }
    }

    /**
     * The order in which pens are being displayed to the user
     * If multiple drawing sets are active it will be ordered in ascending order of 'slot number'
     * Note: This may contain inactive pens
     */
    public List<ObservableDrawingPen> getGlobalDisplayOrder(){
        List<ObservableDrawingSet> drawingSets = new ArrayList<>();
        for(PlottedGroup group : groups.values()){
            if(!drawingSets.contains(group.drawingSet)){
                drawingSets.add(group.drawingSet);
            }
        }

        drawingSets.sort(Comparator.comparingInt(set -> DrawingBotV3.INSTANCE.drawingSetSlots.indexOf(set)));

        List<ObservableDrawingPen> globalOrder = new ArrayList<>();
        drawingSets.forEach(drawingSet -> globalOrder.addAll(drawingSet.pens));

        return globalOrder;
    }

    /**
     * The logical order in which pens should be rendered
     * If multiple drawing set are active they will be ordered using the most dominate distribution type
     * Note: This may contain inactive pens
     */
    public List<ObservableDrawingPen> getGlobalRenderOrder(){
        List<ObservableDrawingSet> drawingSets = new ArrayList<>();
        List<ObservableDrawingPen> globalOrder = new ArrayList<>();
        for(PlottedGroup group : groups.values()){
            if(!drawingSets.contains(group.drawingSet)){
                drawingSets.add(group.drawingSet);
            }

            for(ObservableDrawingPen drawingPen : group.drawingSet.pens){
                if(!globalOrder.contains(drawingPen)){
                    globalOrder.add(drawingPen);
                }
            }
        }

        int highestTally = 0;
        EnumDistributionOrder order = EnumDistributionOrder.DARKEST_FIRST;

        for(EnumDistributionOrder distributionOrder : EnumDistributionOrder.values()){
            int tally = 0;
            for(ObservableDrawingSet drawingSet : drawingSets){
                if(drawingSet.distributionOrder.get() == distributionOrder){
                    tally++;
                }
            }
            if(tally > highestTally){
                highestTally = tally;
                order = distributionOrder;
            }
        }

        globalOrder.sort(order.comparator);

        //TODO - THERE IS SOME INCONSISTENCY GOING ON WITH DISTRIBUTION ORDER, SURELY IT SHOULD ONLY BE USED FOR DISTRIBUTION AND NOT RENDER ORDER???
        Collections.reverse(globalOrder);

        return globalOrder;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void updatePenNumbers(PlottedGroup plottedGroup){
        for(int i = 0; i < plottedGroup.drawingSet.pens.size(); i++){
            ObservableDrawingPen pen = plottedGroup.drawingSet.pens.get(i);
            pen.penNumber.set(i); //update pens number based on position
        }
    }


    public static Map<ObservableDrawingPen, Integer> getPerPenGeometryStats(PlottedDrawing plottedDrawing){
        Map<Integer, int[]> perGroupStats = new HashMap<>();

        //create a tally for each group
        for(PlottedGroup group : plottedDrawing.groups.values()){
            if(group.getPenCount() > 0){
                perGroupStats.put(group.groupID, new int[group.getPenCount()]);
            }
        }

        //tally all the geometries per group / per pen
        for(IGeometry geometry : plottedDrawing.geometries){
            if(geometry.getPenIndex() >= 0){
                int[] stats = perGroupStats.get(geometry.getGroupID());
                if(stats != null){
                    stats[geometry.getPenIndex()]++;
                }
            }
        }

        //combine the tallies into pen stats per unique pen
        Map<ObservableDrawingPen, Integer> perPenStats = new HashMap<>();
        for(Map.Entry<Integer, int[]> groupStats : perGroupStats.entrySet()){
            PlottedGroup group = plottedDrawing.getPlottedGroup(groupStats.getKey());
            int[] tally = groupStats.getValue();
            for(ObservableDrawingPen drawingPen : group.drawingSet.pens){
                perPenStats.putIfAbsent(drawingPen, 0);
                perPenStats.put(drawingPen, perPenStats.get(drawingPen)+tally[drawingPen.penNumber.get()]);
            }
        }

        return perPenStats;
    }

    public static void updatePerPenGeometryStats(PlottedDrawing plottedDrawing, Map<ObservableDrawingPen, Integer> perPenStats){
        //apply the stats to the pen
        for(Map.Entry<ObservableDrawingPen, Integer> groupStats : perPenStats.entrySet()){
            groupStats.getKey().currentGeometries.set(groupStats.getValue());
            groupStats.getKey().currentPercentage.set(NumberFormat.getPercentInstance().format((float)groupStats.getValue() / plottedDrawing.geometries.size()));
        }
    }

    /**updates every pen's unique number, and sets the correct pen number for every line based on their weighted distribution*/
    public static void updateEvenDistribution(PlottedGroup plottedGroup, boolean weighted, boolean random){
        updatePenNumbers(plottedGroup);

        int currentGeometry = 0;
        int totalWeight = 0;
        int[] weights = new int[plottedGroup.drawingSet.pens.size()];
        for(int i = 0; i < plottedGroup.drawingSet.pens.size(); i++){
            ObservableDrawingPen pen = plottedGroup.drawingSet.pens.get(i);
            pen.penNumber.set(i); //update pens number based on position
            if(pen.isEnabled()){
                weights[i] = weighted ? pen.distributionWeight.get() : 100;
                totalWeight += weights[i];
            }
        }

        int[] renderOrder = plottedGroup.drawingSet.calculateRenderOrder();

        if(!random){
            for(int i = 0; i < renderOrder.length; i++){
                int penNumber = renderOrder[i];
                ObservableDrawingPen pen = plottedGroup.drawingSet.getPen(penNumber);
                if(pen.isEnabled()){

                    //update percentage
                    float percentage = (weighted ? (float)pen.distributionWeight.get() : 100) / totalWeight;
                    //update geometry count
                    int geometriesPerPen = (int)(percentage * plottedGroup.getGeometryCount());

                    //set pen references
                    int end = i == renderOrder.length-1 ? plottedGroup.geometries.size() : currentGeometry + geometriesPerPen;
                    for (; currentGeometry < end; currentGeometry++) {
                        IGeometry geometry = plottedGroup.geometries.get(currentGeometry);
                        geometry.setPenIndex(penNumber);
                    }
                }
            }
        }else{
            Random rand = new Random(0);

            for(IGeometry geometry : plottedGroup.geometries){
                int weightedRand = rand.nextInt(totalWeight);
                int currentWeight = 0;
                for(int w = 0; w < weights.length; w++){
                    currentWeight += weights[w];
                    if(weightedRand < currentWeight){
                        geometry.setPenIndex(w);
                        break;
                    }
                }
            }
        }
    }

    public static void updatePreConfiguredPenDistribution(PlottedGroup plottedGroup){
        updatePenNumbers(plottedGroup);

        for(IGeometry geometry : plottedGroup.geometries){
            int originalIndex = geometry.getPFMPenIndex();
            ObservableDrawingPen drawingPen = plottedGroup.originalDrawingSetOrder.get(originalIndex);
            int currentIndex = plottedGroup.drawingSet.pens.indexOf(drawingPen);

            geometry.setPenIndex(currentIndex);
        }
    }

    public static void updateSinglePenDistribution(PlottedGroup plottedGroup){
        updatePenNumbers(plottedGroup);

        int[] renderOrder = plottedGroup.drawingSet.calculateRenderOrder();

        for (int penNumber : renderOrder) {
            ObservableDrawingPen pen = plottedGroup.drawingSet.pens.get(penNumber);
            if (pen.isEnabled()) {
                ///add all the geometries to the first enabled pen
                plottedGroup.geometries.forEach(g -> g.setPenIndex(penNumber));
                return;
            }
        }

    }

    /**sets all the pens to default distribution / even*/
    public static void resetWeightedDistribution(PlottedGroup plottedGroup){
        for(ObservableDrawingPen pen : plottedGroup.drawingSet.getPens()){
            pen.distributionWeight.setValue(100);
        }
    }

    /**increases the distribution weight of a given pen*/
    public static void adjustWeightedDistribution(PlottedGroup plottedGroup, ObservableDrawingPen selected, int adjust){
        int current = selected.distributionWeight.get();
        selected.distributionWeight.set(Math.max(0, current + adjust));
    }
}