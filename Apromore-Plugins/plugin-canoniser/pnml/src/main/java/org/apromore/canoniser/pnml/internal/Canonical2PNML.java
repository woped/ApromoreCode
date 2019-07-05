/*
 * Copyright © 2009-2018 The Apromore Initiative.
 *
 * This file is part of "Apromore".
 *
 * "Apromore" is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * "Apromore" is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program.
 * If not, see <http://www.gnu.org/licenses/lgpl-3.0.html>.
 */

/**
 * TestCanonical2PNML is a class for converting an CanonicalProcessType
 *  object into a PnmlType object.
 * <p>
 *
 * @author Martin SInger, Niko Waldow
 * @version     %I%, %G%
 * @since 1.0
 */

package org.apromore.canoniser.pnml.internal;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.apromore.anf.AnnotationType;
import org.apromore.anf.AnnotationsType;
import org.apromore.anf.GraphicsType;
import org.apromore.canoniser.pnml.internal.canonical2pnml.*;
import org.apromore.cpf.*;
import org.apromore.cpf.NetType;
import org.apromore.pnml.*;
import org.apromore.pnml.NodeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
// import java.util.logging.Logger;

public class Canonical2PNML {
    //Constants
    private static final BigDecimal MIN_DISTANCE_X = new BigDecimal(70);
    private static final BigDecimal MIN_DISTANCE_Y = new BigDecimal(80);
    private static final BigDecimal MIN_SIZE = new BigDecimal(80);
    // static final private Logger LOGGER = Logger.getLogger(Canonical2PNML.class.getCanonicalName());

    private DataHandler data = new DataHandler();
    private RemoveConnectorTasks removeConnectorTasks = new RemoveConnectorTasks();
    private TranslateAnnotations ta = new TranslateAnnotations();
    private TranslateNet tn = new TranslateNet();
    private long ids = 0;  //System.currentTimeMillis();

    public PnmlType getPNML() {
        return data.getPnml();
    }

    /*
    public Canonical2PNML(CanonicalProcessType cproc) {
        removeConnectorTasks.setValue(data, cproc);
        removeConnectorTasks.remove();
        cproc = removeConnectorTasks.getCanonicalProcess();
        decanonise(cproc, null);
        ta.setValue(data);
    }
    */
    //#2018: called by PNML132Canoniser.java
    public Canonical2PNML(CanonicalProcessType cproc,
                          AnnotationsType      annotations,
                          boolean              isCpfTaskPnmlTransition,
                          boolean              isCpfEdgePnmlPlace) {

        data.setCpfTaskPnmlTransition(isCpfTaskPnmlTransition);
        data.setCpfEdgePnmlPlace(isCpfEdgePnmlPlace);

        removeConnectorTasks.setValue(data, cproc);
        removeConnectorTasks.remove();
        cproc = removeConnectorTasks.getCanonicalProcess();
        decanonise(cproc, annotations);
        ta.setValue(data);

        //If annotation exists, run new ANF-dependent layout algorithm, else run old
        if (annotations != null) {
            // removeElements is not working at this point, because all y-axis-values are 0 after this method
            // and all elements of the model are in one line. This doesn´t look good.
            // If this method work properly, it is useful here, because it delets some unnecessary synthetic elements.
            //removeElements();
            //map ANF and pnml
            ta.mapNodeAnnotations(annotations);
            //position double bended arcs (e. g. loops)
            positionDoubleBendedArcs(annotations);
            //ANF-dependent algorithm
            positionSyntheticElements(createIncomingArcMultimap(), createOutgoingArcMultimap());
            // Define the factor magnification to evenly enlarge the model so that all synthetic elements have enough space.
            factorEnlargementOfModel();
        }else {
            //Not needed anymore since XORs are na translated as Places
            /* Commented out because of new translation-order in decanonise
            // Expand XOR (and OR) routing from from PNML transitions to their complete structures
            AddXorOperators ax = new AddXorOperators();
            ax.setValues(data, ids);
            ax.add(cproc);
            ids = ax.getIds();
            cproc = ax.getCanonicalProcess();
            */
            //Not needed anymore - Replaced by positionSyntheticElements
            // Structural simplifications
            simplify();
        }

    }

    // This method is used only in Canonical2PNMLUnitTest and can be ignored for the transformation process
    public Canonical2PNML(CanonicalProcessType cproc, AnnotationsType annotations, String filename) {
        RemoveEvents removeEvents = new RemoveEvents();
        RemoveState removeState = new RemoveState();
        RemoveSplitJoins removeSplitJoins = new RemoveSplitJoins();

        for (ResourceTypeType res : cproc.getResourceType()) {
            data.put_resourcemap(String.valueOf(res.getId()), res);
        }
        data.setAnno(annotations);
        data.setFilename(filename);

        removeEvents.setValue(annotations, data, cproc);
        removeEvents.remove();
        cproc = removeEvents.getCanonicalProcess();
        if (annotations != null) {
            annotations = removeEvents.getAnnotations();
        }
        removeConnectorTasks.setValue(data, cproc);
        removeConnectorTasks.remove();
        cproc = removeConnectorTasks.getCanonicalProcess();
        removeState.setValue(data, cproc);
        removeState.remove();
        cproc = removeState.getCanonicalProcess();
        removeSplitJoins.setValue(annotations, data, cproc);
        removeSplitJoins.remove();
        cproc = removeSplitJoins.getCanonicalProcess();
        if (annotations != null) {
            annotations = removeSplitJoins.getAnnotations();
        }
        decanonise(cproc, annotations);
        ta.setValue(data);
        if (annotations != null) {
            ta.mapNodeAnnotations(annotations);
        }
        AddXorOperators ax = new AddXorOperators();
        ax.setValues(data, ids);
        ax.add(cproc);
        ids = ax.getIds();
        cproc = ax.getCanonicalProcess();
        UpdateSpecialOperators uso = new UpdateSpecialOperators();
        uso.setValues(data, ids);
        uso.add(cproc);
        ids = uso.getIds();
        if (data.getSubnet() != null) {
            if (data.getSubnet().size() == 1) {
                TransitionType obj = data.getSubnet().get(0);
                TranslateSubnet ts = new TranslateSubnet();

                ts.setValue(data, obj.getId());
                data.getSubnet().remove(0);
                ts.addSubnet();
                data = ts.getdata();
            }
        }
    }

    /**
     * This main method to be reused by all the constructors for all cases.
     * <p/>
     *
     * @since 1.0
     */
    private void decanonise(CanonicalProcessType cproc, AnnotationsType annotations) {
        for (NetType net : cproc.getNet()) {
            tn.setValues(data, ids, annotations);
            tn.translateNet(net);
            ids = tn.getIds();
        }

        TranslateHumanResources thr = new TranslateHumanResources();
        thr.setValues(data, ids);
        thr.translate(cproc);
        ids = thr.getIds();

        data.getNet().setId("noID");
        data.getNet().setType("http://www.informatik.hu-berlin.de/top/pntd/ptNetb");
        data.getPnml().getNet().add(data.getNet());
    }

    private void removeElements(){
        //LOGGER.info("Performing structural simplifications");

        SetMultimap<NodeType, ArcType> incomingArcMultimap = HashMultimap.create();
        SetMultimap<NodeType, ArcType> outgoingArcMultimap = HashMultimap.create();

        // Index graph connectivity
        // Every arc in pnml-net is saved in IncomingMap with its target object and also in OutgoingMap with its source object.
        //*
        // Jeder Pfeil im PNML-Netz wird jeweils in die IncomingMap mit dem jeweiligen Zielobjekt und
        // in die OutgoingMap mit dem jeweiligen Quellobjekt gespeichert.
        for (ArcType arc: data.getNet().getArc()) {
            incomingArcMultimap.put((NodeType) arc.getTarget(), arc);
            outgoingArcMultimap.put((NodeType) arc.getSource(), arc);
        }

        // When a synthetic place occurs adjacent to a silent transition on a branch, collapse them
        // Every place, which is artificially produced by the system to have a correct pnml-net, will be proved.
        // Jede künstlich hergestellte Stelle wird überprüft
        for (PlaceType place: data.getSynthesizedPlaces()) {
            // When the artificially produced place has one incoming and one outgoing arc, then all of those arcs
            // will be saved in an extra ArcType-Object with its intervening object (place or transition).
            //*
            // Wenn die künstlich hergestellte Stelle einen Eingangs- und einen Ausgangspfeil hat,
            // dann werden Eingangs- und Ausgangspfeil in ein extra ArcType-Objekt und die dazwischenliegende Transition gespeichert.
            if (incomingArcMultimap.get(place).size() == 1 &&
                    outgoingArcMultimap.get(place).size() == 1) {

                // Assign: --incomingArc-> (place) --outgoingArc->
                ArcType incomingArc = incomingArcMultimap.get(place).iterator().next();
                ArcType outgoingArc = outgoingArcMultimap.get(place).iterator().next();

                TransitionType transition = (TransitionType) outgoingArc.getTarget();
                if (incomingArcMultimap.get(transition).size() == 1 && isSilent(transition)) {
                    // Collapse synthesized place followed by silent transition.
                    // ("silent" means that the transition has no name and is likely to be artificially produced.
                    // If the transition has one incoming arc and no name, then the arcs, the place and the transition will be deleted.
                    // Afterwards the previous transition will be connected with follow-up place with an new arc.
                    //*
                    // Wenn die Transition einen Eingangspfeil hat und keinen Namen besitzt, dann werden die Pfeile, die Stelle und die Transition gelöscht.
                    // Anschließend werden die vorherige Transition mit der nachfolgenden Stelle durch einen Pfeil verbunden.
                    // --> silent bedeutet, dass die Transaktion keinen Namen besitzt und vermutlich auch künstlich hergestellt wurde

                    // Delete: --incomingArc-> (place) --outgoingArc-> [transition]
                    data.getNet().getArc().remove(incomingArc);
                    data.getNet().getArc().remove(outgoingArc);
                    data.getNet().getPlace().remove(place);
                    data.getNet().getTransition().remove(transition);

                    // Re-source transition's outgoing arcs to incomingArc.source;
                    assert incomingArc.getSource() instanceof TransitionType;
                    for (ArcType arc: new HashSet<>(outgoingArcMultimap.get(transition))) {
                        arc.setSource(incomingArc.getSource());
                        outgoingArcMultimap.remove(transition, arc);
                        outgoingArcMultimap.put((TransitionType) incomingArc.getSource(), arc);
                    }
                }
                else {
                    transition = (TransitionType) incomingArc.getSource();
                    if (outgoingArcMultimap.get(transition).size() == 1 && isSilent(transition)) {
                        // Collapse silent transition followed by synthesized place
                        // If the transition has one outgoing arc and no name, then all of the incoming and outgoing arc, the place and the transition will be deleted.
                        // Afterwards the previous transition will be connected with follow-up place with an new arc.
                        //*
                        // Wenn die Transition einen Ausgangspfeil hat und keinen Namen besitzt, werden die ein- und ausgehenden Pfeile, die Stelle und die Transition gelöscht.
                        // Anschließend werden die vorherige Stelle und die nachfolgende Transition verbunden.

                        // Delete: [transition] --incomingArc-> (place) --outgoingArc->
                        data.getNet().getArc().remove(incomingArc);
                        data.getNet().getArc().remove(outgoingArc);
                        data.getNet().getPlace().remove(place);
                        data.getNet().getTransition().remove(transition);

                        // Re-target transition's incoming arcs to outgoingArc.target;
                        assert outgoingArc.getTarget() instanceof TransitionType;
                        for (ArcType arc: new HashSet<>(incomingArcMultimap.get(transition))) {
                            arc.setTarget(outgoingArc.getTarget());
                            incomingArcMultimap.remove(transition, arc);
                            incomingArcMultimap.put((TransitionType) outgoingArc.getTarget(), arc);
                        }
                    }
                }
            }
        }
        // All of the artificially produced places, which are saved in an extra Array, will be deleted.
        //*
        // Jetzt werden die künstlich hergestellten Stellen, die in dem gesonderten Array synthesizedPlaces gespeichert sind, geleert.
        data.getSynthesizedPlaces().clear();
        //LOGGER.info("Performed structural simplifications");

    }

    private void simplify() {

        removeElements();

      //layout optimization
        ArrayList<NodeType> allNodes = new ArrayList<>();
        ArrayList<ArcType> allArcs = new ArrayList<>();
        List<NodeType> insertedNodes = Collections.synchronizedList(new ArrayList<>());
        ArrayList<NodeType> nodesBeforeFirstNonInserted = new ArrayList<>();
        allNodes.addAll(getPNML().getNet().get(0).getPlace());
        allNodes.addAll(getPNML().getNet().get(0).getTransition());
        Collections.sort(allNodes, new NodeTypeComparator());
        allArcs.addAll(getPNML().getNet().get(0).getArc());


        /*Commented out some parts since simplify() does not support multiple starting nodes

        //Find the first node in the graph
        ArrayList<NodeType> firstNodeList = findStartElement(allNodes, allArcs);

        //Cant find first non inserted Node since aleways annotation = null
        //Find out which is the first node that was not inserted afterwards;
        ArrayList<NodeType> firstNonInsertedNode = findFirstNonInsertedNode(firstNodeList, allArcs, nodesBeforeFirstNonInserted);*/

        NodeType firstNonInsertedNode = allNodes.get(0);

        for(NodeType node : allNodes){
            if(node.getGraphics().getPosition().getX().equals(BigDecimal.ZERO)
                    && node.getGraphics().getPosition().getY().equals(BigDecimal.ZERO)){
                firstNonInsertedNode = node;
                break;
            }
        }

        //get(0) since traverseNodes does not support multiple start elements
        traverseNodes(firstNonInsertedNode, createOutgoingArcMultimap(), createIncomingArcMultimap());

        /* Commented out since incompability with old algorithm (traverse nodes)
        //Correct the position of Nodes before first firstNonInsertedNode since traverseNodes only goes in one direction
        correctBeginningNodesPosition(firstNonInsertedNode, nodesBeforeFirstNonInserted, firstNodeList, allNodes);*/


        //#2018Finger: Change duplicate positions
        //just correcting position if 2 elements are on the same spot.
        //if they're on the same spot it will check the previous element using the arc source.
        // The element with the lower (higher in numbers) source is then moved down.


        List<PlaceType> placeList = data.getNet().getPlace();
        List<TransitionType> transitionList = data.getNet().getTransition();
        List<ArcType> arcList = data.getNet().getArc();
        List<NodeType> editedNodes = new ArrayList<>();
/*
        ArrayList<NodeType> allNode = new ArrayList<>();
        List<NodeType> insertedNode = Collections.synchronizedList(new ArrayList<>());
        allNode.addAll(getPNML().getNet().get(0).getPlace());
        allNode.addAll(getPNML().getNet().get(0).getTransition());
        Collections.sort(allNode, new NodeTypeComparator());*/

        // --> warum kleiner 8???
        for (int i = 0; i < 8; i++){ //hier wäre eine while-schleife mögich, um sicherzustellen, dass alle elemente genügen abstand haben. Da die Elemente in der Liste aber nicht geordnet sind, bricht die Schleife manchmal nicht ab und 2 elemente verschieben sich ins unendliche nach unten. Nicht wünschenswert.
            // Every element of the pnml-net will compared with all the other elements of the same pnml-net.
            //*
            // Jeder Knoten des PNML-Netzes wird mit allen anderen Knoten des gleichen PNML-Netzes verglichen.
            for (NodeType node1 : allNodes) {
                for (NodeType node2 : allNodes) {
                    // The position values of the x-axis and the y-axis will be analysed.
                    //*
                    // Es werden die Positionswerte von der x- und y-Achse untersucht.
                    BigDecimal ntx = node1.getGraphics().getPosition().getX();
                    BigDecimal nty = node1.getGraphics().getPosition().getY();
                    BigDecimal ntsx = node2.getGraphics().getPosition().getX();
                    int iny1 = nty.intValue();
                    BigDecimal ntsy = node2.getGraphics().getPosition().getY();
                    int iny2 = ntsy.intValue();
                 // If both elements are the same element (,so that the element is compared with itself), then do nothing.
                    //*
                    // Wenn beide Knoten derselbe Knoten sind (also der Knoten wird mit sich selbst verglichen), dann tue nichts.
                    if (!node2.equals(node1)) {
                     // If the value of the x-axis and the y-axis of the element has the same values like another element,
                        // then the value of x-axis of the second element will be increased by 80.
                        //*
                        // Wenn der Wert der x-Achse und der y-Achse des Knotens dieselben Werte hat, wie ein anderer Knoten,
                        // dann wird dem zweiten Knoten zum Wert der y-Achse 80 Pixel dazu gerechnet
                        if ((ntx.compareTo(ntsx) == 0) && (nty.compareTo(ntsy) == 0)) {
                            nty = nty.add(MIN_DISTANCE_Y);
                            compareSources(editedNodes, node1, node2, nty, node1, node2, arcList, allNodes);
                        }
                        // In the next 2 if-requests will be proofed, whether the two compared elements, which are vertical arranged,
                        // have a difference of 75 in relation to the y-axis.
                        // If it is not the case, the difference will be corrected to a difference of 80.
                        // Afterwards it will be proofed, whether the relocation of the element has reduced the difference to another element.
                        //*
                        // Die nächsten 2 Abfragen überprüfen, ob die zwei miteinander verglichenen Knoten, die übereinander angeordnet sind,
                        // in der y-Achse mindestens einen senkrechten Abstand von 75 Pixeln hat.
                        // Wenn das nicht der Fall ist, wird der Abstand auf einen Abstand von 80 Pixeln korrigiert.
                        // Anschließend wird überprüft, ob die Verschiebung des Knotens den Abstand zu einem wieder anderen Knoten verkürzt hat.
                        else if ((ntx.compareTo(ntsx) == 0) && iny1 - iny2 > -75 && iny1 - iny2 < 0) {
                            nty = nty.add(MIN_DISTANCE_Y.subtract(BigDecimal.valueOf(Math.abs(iny1 - iny2))));
                            if (checkNewPosition(node2, nty, allNodes)) {
                                node2.getGraphics().getPosition().setY(nty);
                                editedNodes.add(node2);
                            }
                        } else if ((ntx.compareTo(ntsx) == 0) && iny1 - iny2 > 0 && iny1 - iny2 < 75) {
                            nty = nty.add(MIN_DISTANCE_Y.subtract(BigDecimal.valueOf(Math.abs(iny1 - iny2))));
                            if (checkNewPosition(node1, nty, allNodes)) {
                                node1.getGraphics().getPosition().setY(nty);
                                editedNodes.add(node1);
                            }
                        }
                    }
                }
            }
        }
        //correct arc positions
        for (ArcType arc : getPNML().getNet().get(0).getArc()){
            if (arc.getGraphics()!= null)
                if(arc.getGraphics().getPosition() != null)
                    arc.getGraphics().getPosition().clear();
        }
    }

    private void compareSources(List<NodeType> editedNodes, NodeType source1, NodeType source2, BigDecimal y, NodeType node1, NodeType node2, List<ArcType> arcList, List<NodeType> allNodes){
        NodeType tempSource1;
        NodeType tempSource2;
        int tempSource1Y;
        int tempSource2Y;

        // All arcs, which has the second element as target, will be compared with all arcs.
        //*
        // Es werden diejenigen Pfeile, die als Ziel den zweiten Knoten (source2) haben, mit allen Pfeilen verglichen.
        for (ArcType arc : arcList){
            if (arc.getTarget().equals(source2)){
                tempSource2 = (NodeType) arc.getSource();
                for (ArcType arc2 : arcList){
                    if (arc2.getTarget().equals(source1)) {
                        tempSource1 = (NodeType) arc2.getSource();
                        tempSource1Y = tempSource1.getGraphics().getPosition().getY().toBigInteger().intValue();
                        tempSource2Y = tempSource2.getGraphics().getPosition().getY().toBigInteger().intValue();

                        //if (((source1.getGraphics().getPosition().getY().toBigInteger().intValue() - source2.getGraphics().getPosition().getY().toBigInteger().intValue()) < -20) || ((source1.getGraphics().getPosition().getY().toBigInteger().intValue() - source2.getGraphics().getPosition().getY().toBigInteger().intValue()) > 20)) {
                            //do nothing
                        //}
                        if (( tempSource1Y - tempSource2Y) < 0) {
                            if (checkNewPosition(node2, y, allNodes)) {
                                node2.getGraphics().getPosition().setY(y);
                                editedNodes.add(node2);
                            }
                        } else if ((tempSource1Y - tempSource2Y) > 0 ) {
                            if (checkNewPosition(node1, y, allNodes)) {
                                node1.getGraphics().getPosition().setY(y);
                                editedNodes.add(node1);
                            }
                        }
                        else if(!tempSource1.equals(tempSource2) || !node1.equals(node2)){
                            if ((tempSource1Y - tempSource2Y) == 0) {
                                compareSources(editedNodes, node1, node2, y, tempSource1, tempSource2, arcList, allNodes);
                            }
                        }
                    }
                }
            }
        }
    }

    // Method: correction of the values of the y-axis
    //*
    // Methode: Korrektur der y-Achse
    private boolean checkNewPosition(NodeType node, BigDecimal y, List<NodeType> allNodes){
        // x- and y-axis values of the node element
        BigDecimal nodeX = node.getGraphics().getPosition().getX();
        int        nodeY = y.toBigInteger().intValue();

        for (NodeType tempNode : allNodes) {
            BigDecimal tempNodeX = tempNode.getGraphics().getPosition().getX();
            int        tempNodeY = tempNode.getGraphics().getPosition().getY().toBigInteger().intValue();

            if (!tempNode.equals(node)){
                if (tempNodeX.compareTo(nodeX) == 0 && (nodeY - tempNodeY) > -50 && (nodeY - tempNodeY) < 50) {
                    tempNode.getGraphics().getPosition().setY(y.add(new BigDecimal(50)));
                    return false;
                }
            }
        }
        return true;
    }

    // hier liegt das Problem für die Formatierungsfehler komplexer Verzweigungen
    // Problem liegt beim Join
    // Method: Relocate all nodes, so that the layout of the pnml-net looks good
    private void traverseNodes(NodeType node, SetMultimap<org.apromore.pnml.NodeType, ArcType> outgoingArcMultimap,SetMultimap<org.apromore.pnml.NodeType, ArcType> incomingArcMultimap){

        Set<ArcType> tempOutArcs = outgoingArcMultimap.get(node);
    	Set<ArcType> tempInArcs = incomingArcMultimap.get(node);

    	if(!tempOutArcs.isEmpty()){
    		for(ArcType arc: tempOutArcs){
    			NodeType nextNode = (NodeType) arc.getTarget();
    			BigDecimal biggestX = new BigDecimal(0);

    			//if(nextNode.getGraphics().getPosition().isInsertedNode()){ --> isInsertedNode sometimes wrong?
    			if(nextNode.getGraphics().getPosition().getX().equals(BigDecimal.ZERO)
                        && nextNode.getGraphics().getPosition().getY().equals(BigDecimal.ZERO)){
    				//x-axis
    				Set<ArcType> inGoingArcs = incomingArcMultimap.get(nextNode);
    				// --> diese for-Schleife sinnvoll? BiggestX wird später nicht mehr verwendet und es wird auch nicht zum Werte-Setzen verwendet.
                    // Oder hab ich was übersehen?
    				for(ArcType inArc : inGoingArcs){
    					NodeType inNode = (NodeType) inArc.getSource();
    					if(inNode.getGraphics().getPosition().getX().compareTo(biggestX) > 0){
    						biggestX = inNode.getGraphics().getPosition().getX();
    					}
    				}

    				//set X of inserted nodes
    				nextNode.getGraphics().getPosition().setX(node.getGraphics().getPosition().getX().add(MIN_DISTANCE_X));

    				//y-axis
    				if(tempInArcs.size() > 1){

    					BigDecimal averageY = new BigDecimal(0);

    					for(ArcType a: tempInArcs){
    						averageY = averageY.add(((NodeType) a.getSource()).getGraphics().getPosition().getY());
    					}
    					node.getGraphics().getPosition().setY(averageY.divide(new BigDecimal(tempInArcs.size()), 2, RoundingMode.HALF_UP));
    				}

    				double y;
    				BigDecimal nodeY = node.getGraphics().getPosition().getY();

    				// If there are Splits, then move target element down
    				if(tempOutArcs.size() >1){

    				    // If there is an even number of outgoing arcs
    					if (tempOutArcs.size()%2 == 0){

    						y = tempOutArcs.size()/2;

    						for (ArcType a: tempOutArcs){
    							NodeType n = (NodeType)a.getTarget();
    							n.getGraphics().getPosition().setY(nodeY.add(MIN_DISTANCE_Y.multiply(new BigDecimal(y-0.5))));
    							y--;
    						}

    					} else {

    						y = Math.floor(tempOutArcs.size()/2);

    						for (ArcType a: tempOutArcs){
    							NodeType n = (NodeType)a.getTarget();
    							n.getGraphics().getPosition().setY(nodeY.add(MIN_DISTANCE_Y.multiply(new BigDecimal(y))));
    							y--;
    						}

    					}

    				}else{
    					nextNode.getGraphics().getPosition().setY(nodeY);
    				}
    			}

				//move next nodes, if mindistance is greater
				Set<ArcType> outgoingArcs = outgoingArcMultimap.get(nextNode);
				for(ArcType outArc : outgoingArcs){
					NodeType outNode = (NodeType) outArc.getTarget();

					 // minDistance + 40 means the minimum distance with the width of the element (= 40)
                    // (x-position of outNode) - (x-position of nextNode) will be compared with the minimum distance incl. the width of the element.
                    // If the distance value is smaller, then the x-position of nextNode + minimum distance + 40 will be added to the x-position of outNode
                    //*
                    // minDistance + 40 bedeutet die Mindestdistanz mit der Breite des Elements (= 40)
                    // (x-Position von outNode) - (x-Position von nextNode) wird verglichen mit der Mindestdistanz inkl. der Breite des Elements
                    // Wenn der Distanz-Wert kleiner ist, wird die x-Position von nextNode + Mindestdistanz + 40 zur neuen x-Position von outNode
					if(outNode.getGraphics().getPosition().getX().subtract(nextNode.getGraphics().getPosition().getX()).compareTo(

					        MIN_DISTANCE_X.add(new BigDecimal(40))) < 0){
						outNode.getGraphics().getPosition().setX(nextNode.getGraphics().getPosition().getX().add(MIN_DISTANCE_X.add(new BigDecimal(40))));
					}
				}
    		}

    		for(ArcType arc: tempOutArcs){
    			traverseNodes((NodeType) arc.getTarget(), outgoingArcMultimap, incomingArcMultimap);
    		}
    	}
    }

    //Method: Define the factor magnification to evenly enlarge the model so that all synthetic elements have enough space.
    private void factorEnlargementOfModel(){

        // find the shortest arc and its length
        ArcType shortestArc = findShortestEdge();
        double lengthOfShortestArc = absoluteLengthOfArc(shortestArc);

        // enlarge the shortest edge to an reasonable size of MIN_SIZE
        if (lengthOfShortestArc < MIN_SIZE.toBigInteger().intValue()){
            double relation = MIN_SIZE.toBigInteger().intValue() / lengthOfShortestArc;

            // get all nodes in once
            ArrayList<NodeType> allNodes = new ArrayList<>();
            allNodes.addAll(getPNML().getNet().get(0).getPlace());
            allNodes.addAll(getPNML().getNet().get(0).getTransition());

            // find those nodes, which are nearest on the right side and on the top side
            NodeType nodeWithSmallestX = findNodeWithSmallestPositionValue(allNodes,true);
            NodeType nodeWithSmallestY = findNodeWithSmallestPositionValue(allNodes,false);

            // Calculate the correction values for the positions of the nodes, which are nearest on the right side and on the top side,
            // so that the model is not so far away from the beginning of the window
            int xSmallestPosition = nodeWithSmallestX.getGraphics().getPosition().getX().toBigInteger().intValue();
            int ySmallestPosition = nodeWithSmallestY.getGraphics().getPosition().getY().toBigInteger().intValue();

            // the default point in the graphics is P(100/100), where the model shall begin to be drawn
            double correctionXPosition = xSmallestPosition * relation - 100;
            double correctionYPosition = ySmallestPosition * relation - 100;

            // iterate all nodes to reposition the nodes, so that the nodes will have more distance,
            // but it still has the old appearance and relation between the nodes
            // also the correctionPosition corrects the position of the model, so that the model is positioned at the beginning of the file
            for(NodeType node : allNodes){
                double nodeX = node.getGraphics().getPosition().getX().toBigInteger().intValue();
                double nodeY = node.getGraphics().getPosition().getY().toBigInteger().intValue();
                node.getGraphics().getPosition().setX(new BigDecimal(nodeX * relation - correctionXPosition));
                node.getGraphics().getPosition().setY(new BigDecimal(nodeY * relation - correctionYPosition));
            }
        }
    }

    // Method: find the shortest arc in the whole model by absolute value of edge length
    private ArcType findShortestEdge(){
        ArcType arc = data.getNet().getArc().get(0);

        // Initialize variable absoluteLength and help with the same value
        // absoluteLength shall save the shortest length of all arcs
        // help shall iterate all lengths of the arcs to find a length with is smaller than the length, which is saved in absoluteLength
        // If it is smaller, the value of help will be saved in absoluteLength.
        double absoluteLength = absoluteLengthOfArc(data.getNet().getArc().get(0));
        double help = absoluteLength;

        for (ArcType tempArc: data.getNet().getArc()) {
            help = absoluteLengthOfArc(tempArc);

            if(help < absoluteLength){
                arc = tempArc;
                absoluteLength = help;
            }
        }

        return arc;
    }

    // Method: Calculation of vector length of the arc
    private double absoluteLengthOfArc (ArcType arc){
        NodeType source = (NodeType) arc.getSource();
        NodeType target = (NodeType) arc.getTarget();

        // get the position of the source and the target of the arc
        int sourceX = source.getGraphics().getPosition().getX().toBigInteger().intValue();
        int sourceY = source.getGraphics().getPosition().getY().toBigInteger().intValue();
        int targetX = target.getGraphics().getPosition().getX().toBigInteger().intValue();
        int targetY = target.getGraphics().getPosition().getY().toBigInteger().intValue();

        // Calculate vector components
        int vectorArcX = targetX - sourceX;
        int vectorArcY = targetY - sourceY;

        // Calculate absolute value of vector
        return Math.sqrt(vectorArcX * vectorArcX + vectorArcY * vectorArcY);
    }

    // Method: finds the node, which is nearest to the right side, when boolean is set true
    //         or finds the node, which is nearest to the top, when boolean is set false
    private NodeType findNodeWithSmallestPositionValue(ArrayList<NodeType> allNodes, boolean xValueNotYValue){
        // get a default node to have a comparison value to find the searched node
        NodeType node = allNodes.get(0);
        int smallestPositionValue = 0;
        // it depends on, whether the algorithm shall find the node with smallest x-position or with smallest y-position
        if (xValueNotYValue){
            smallestPositionValue = node.getGraphics().getPosition().getX().toBigInteger().intValue();
        } else {
            smallestPositionValue = node.getGraphics().getPosition().getY().toBigInteger().intValue();
        }
        int help = smallestPositionValue;

        // iterate all nodes, so that x-positions or y-positions will be compared to those of the other nodes to find the smallest value
        for (NodeType tempNode : allNodes) {
            if (xValueNotYValue) {
                help = tempNode.getGraphics().getPosition().getX().toBigInteger().intValue();
            }
            else {
                help = tempNode.getGraphics().getPosition().getY().toBigInteger().intValue();
            }

            if (help < smallestPositionValue) {
                node = tempNode;
                smallestPositionValue = help;
            }
        }

        return node;
    }

    private ArrayList<NodeType> findStartElement(List<NodeType> allNodes, List<ArcType> allArcs){

        ArrayList<NodeType> helperListAllNodes = new ArrayList<>(allNodes);
        ArrayList<NodeType> helperListRemovedNodes = new ArrayList<>();
        ArrayList<NodeType> helperListRetainedNodes = new ArrayList<>();

        //remove all nodes that do have an incoming arc
        for (ArcType arc : allArcs) {
            for (NodeType node : helperListAllNodes) {
                if(arc.getTarget().equals(node))
                    helperListRemovedNodes.add(node);
            }
        }
        helperListAllNodes.removeAll(helperListRemovedNodes);

        //remove all nodes that do not have an outgoing arc
        if(helperListAllNodes.size() > 1) {
            for (ArcType arc : allArcs) {
                for (NodeType node : helperListAllNodes) {
                    if (arc.getSource().equals(node))
                        helperListRetainedNodes.add(node);
                }
            }
            helperListAllNodes.retainAll(helperListRetainedNodes);
        }

        //return last remaining
        return helperListAllNodes;
    }

    private ArrayList<NodeType> findFirstNonInsertedNode(ArrayList<NodeType> startElementList,
                                                         ArrayList<ArcType> allArcs,
                                                         ArrayList<NodeType> nodesBeforeFirstNonInserted){

        ArrayList<NodeType> firstNonInsertedNodeList = new ArrayList<>();

        for(NodeType startElement : startElementList) {

            NodeType followingNode;
            NodeType firstNonInsertedNode = startElement;

            //by checking if position-data are not 0, 0 - isInsertedNode does not work trustworthy for this case
            while (firstNonInsertedNode.getGraphics().getPosition().getX().equals(BigDecimal.ZERO)
                    && firstNonInsertedNode.getGraphics().getPosition().getY().equals(BigDecimal.ZERO)) {
                for (ArcType arc : allArcs) {
                    if (arc.getSource().equals(firstNonInsertedNode)) {
                        followingNode = (NodeType) arc.getTarget();
                        nodesBeforeFirstNonInserted.add(firstNonInsertedNode);
                        firstNonInsertedNode = followingNode;
                        firstNonInsertedNodeList.add(firstNonInsertedNode);
                        break;
                    }
                }
            }
        }
        return  firstNonInsertedNodeList;
    }

    private void correctBeginningNodesPosition(ArrayList<NodeType> firstNonInsertedNodeList,
                                               ArrayList<NodeType> nodesBeforeFirstNonInsertedList,
                                               ArrayList<NodeType> firstNodeList,
                                               ArrayList<NodeType> allNodes,
                                               SetMultimap<NodeType, ArcType> outgoingArcMultimap){

        int sizeNodesBeforeNonInsertedList = nodesBeforeFirstNonInsertedList.size();

        //First, set positions of all nodesBeforeFirstNonInserted
        for(int i=0; i<sizeNodesBeforeNonInsertedList; i++) {

            ArrayList<ArcType> firstNonInsertedOutArcs = new ArrayList<>(outgoingArcMultimap.get(firstNonInsertedNodeList.get(i)));
            NodeType firstNonInsertedFollowingNode = (NodeType) firstNonInsertedOutArcs.get(0).getTarget();

            BigDecimal differenceX = firstNonInsertedNodeList.get(i).getGraphics().getPosition().getX()
                    .subtract(firstNonInsertedFollowingNode.getGraphics().getPosition().getX());
            BigDecimal differenceY = firstNonInsertedNodeList.get(i).getGraphics().getPosition().getY()
                    .subtract(firstNonInsertedFollowingNode.getGraphics().getPosition().getY());

            if(differenceX.compareTo(BigDecimal.ZERO) > 0 && differenceX.abs().compareTo(differenceY.abs()) >= 0){
                nodesBeforeFirstNonInsertedList.get(i).getGraphics().getPosition().setY(
                        firstNonInsertedNodeList.get(i).getGraphics().getPosition().getY());
                nodesBeforeFirstNonInsertedList.get(i).getGraphics().getPosition().setX(
                        firstNonInsertedNodeList.get(i).getGraphics().getPosition().getX().add(MIN_DISTANCE_X));
            }else if(differenceX.compareTo(BigDecimal.ZERO) < 0 && differenceX.abs().compareTo(differenceY.abs()) >= 0){
                nodesBeforeFirstNonInsertedList.get(i).getGraphics().getPosition().setY(
                        firstNonInsertedNodeList.get(i).getGraphics().getPosition().getY());
                nodesBeforeFirstNonInsertedList.get(i).getGraphics().getPosition().setX(
                        firstNonInsertedNodeList.get(i).getGraphics().getPosition().getX().subtract(MIN_DISTANCE_X));
            }else if(differenceY.compareTo(BigDecimal.ZERO) > 0 && differenceY.abs().compareTo(differenceX.abs()) > 0){
                nodesBeforeFirstNonInsertedList.get(i).getGraphics().getPosition().setY(
                        firstNonInsertedNodeList.get(i).getGraphics().getPosition().getY().add(MIN_DISTANCE_Y));
                nodesBeforeFirstNonInsertedList.get(i).getGraphics().getPosition().setX(
                        firstNonInsertedNodeList.get(i).getGraphics().getPosition().getX());
            }else if(differenceY.compareTo(BigDecimal.ZERO) < 0 && differenceY.abs().compareTo(differenceX.abs()) > 0){
                nodesBeforeFirstNonInsertedList.get(i).getGraphics().getPosition().setY(
                        firstNonInsertedNodeList.get(i).getGraphics().getPosition().getY().subtract(MIN_DISTANCE_Y));
                nodesBeforeFirstNonInsertedList.get(i).getGraphics().getPosition().setX(
                        firstNonInsertedNodeList.get(i).getGraphics().getPosition().getX());
            }

            /*nodesBeforeFirstNonInsertedList.get(i).getGraphics().getPosition().setY(
                    firstNonInsertedNodeList.get(i).getGraphics().getPosition().getY());
            nodesBeforeFirstNonInsertedList.get(i).getGraphics().getPosition().setX(
                    firstNonInsertedNodeList.get(i).getGraphics().getPosition().getX().subtract(MIN_DISTANCE_X));*/
        }

        //Second, check if there are nodes that have negative x-coordinates
        for(NodeType firstNode : firstNodeList) {
            BigDecimal firstNodePosX = firstNode.getGraphics().getPosition().getX();
            if (firstNodePosX.compareTo(BigDecimal.ZERO) < 0) {
                //correct x-coordinate of all nodes, so that there isn't any node with a negative x-coordinate
                for (NodeType node : allNodes) {
                    BigDecimal posX = node.getGraphics().getPosition().getX();
                    node.getGraphics().getPosition().setX(posX.add(firstNodePosX.abs()));
                }
            }
            BigDecimal firstNodePosY = firstNode.getGraphics().getPosition().getY();
            if (firstNodePosY.compareTo(BigDecimal.ZERO) < 0) {
                //correct y-coordinate of all nodes, so that there isn't any node with a negative y-coordinate
                for (NodeType node : allNodes) {
                    BigDecimal posY = node.getGraphics().getPosition().getY();
                    node.getGraphics().getPosition().setY(posY.add(firstNodePosY.abs()));
                }
            }
        }
    }

    public boolean hasXor(){
        return data.getxorconnectors().size()==1?true:false;
    }

    //creates multimap with all nodes an their corresponding incoming arcs
    private SetMultimap<NodeType, ArcType> createIncomingArcMultimap(){
        SetMultimap<NodeType, ArcType> incomingArcMultimap = HashMultimap.create();
        for (ArcType arc: data.getNet().getArc()) {
            incomingArcMultimap.put((NodeType) arc.getTarget(), arc);
        }
        return incomingArcMultimap;
    }

    //creates multimap with all nodes an their corresponding outgoing arcs
    private SetMultimap<NodeType, ArcType> createOutgoingArcMultimap(){
        SetMultimap<NodeType, ArcType> outgoingArcMultimap = HashMultimap.create();
        for (ArcType arc: data.getNet().getArc()) {
            outgoingArcMultimap.put((NodeType) arc.getSource(), arc);
        }
        return outgoingArcMultimap;
    }

    private void positionSyntheticElements(SetMultimap<NodeType, ArcType> incomingArcMultimap,
                                           SetMultimap<NodeType, ArcType> outgoingArcMultimap){
        ArrayList<NodeType> allNodes = new ArrayList<>();
        allNodes.addAll(getPNML().getNet().get(0).getPlace());
        allNodes.addAll(getPNML().getNet().get(0).getTransition());

        /*//Multimap with all nodes an their corresponding arcs
        SetMultimap<NodeType, ArcType> incomingArcMultimap = HashMultimap.create();
        SetMultimap<NodeType, ArcType> outgoingArcMultimap = HashMultimap.create();

        for (ArcType arc: data.getNet().getArc()) {
            incomingArcMultimap.put((NodeType) arc.getTarget(), arc);
            outgoingArcMultimap.put((NodeType) arc.getSource(), arc);
        }*/

        //Traverse each node (no specific order)
        for(org.apromore.pnml.NodeType node : allNodes){

            //All incoming and outgoing arcs for current node
            ArrayList<ArcType> inArcs = new ArrayList<>(incomingArcMultimap.get(node));
            ArrayList<ArcType> outArcs = new ArrayList<>(outgoingArcMultimap.get(node));

            if(node.getGraphics().getPosition().getX() != null && node.getGraphics().getPosition().getY() != null) {

                //Only position nodes that do not yet have a position (meaning x,y = 0,0)
                if (node.getGraphics().getPosition().getX().equals(BigDecimal.ZERO)
                        && node.getGraphics().getPosition().getY().equals(BigDecimal.ZERO)
                        && inArcs.size() != 0 && outArcs.size() != 0) {

                    //get incoming and outgoing node from current node
                    org.apromore.pnml.NodeType inNode = (org.apromore.pnml.NodeType) inArcs.get(0).getSource();
                    org.apromore.pnml.NodeType outNode = (org.apromore.pnml.NodeType) outArcs.get(0).getTarget();

                    //get positions of incoming and outgoing node
                    BigDecimal posXInNode = inNode.getGraphics().getPosition().getX();
                    BigDecimal posYInNode = inNode.getGraphics().getPosition().getY();
                    BigDecimal posXOutNode = outNode.getGraphics().getPosition().getX();
                    BigDecimal posYOutNode = outNode.getGraphics().getPosition().getY();

                    BigDecimal centeredX;
                    BigDecimal centeredY;

                    //Calculate center of incoming and outgoing node
                    if ((posXInNode.equals(BigDecimal.ZERO)
                            && posYInNode.equals(BigDecimal.ZERO))) {
                        /*
                        * Case if incoming node has position x,y = 0,0.
                        * Formula:
                        * C = O + (1/3) * OP
                        * OP = P-O
                        *
                        * C -> current node position
                        * O -> outgoing node position
                        * P -> incoming node of the incoming node
                        * OP -> distance between O and P
                        * */

                        ArrayList<ArcType> inNodeInArcs = new ArrayList<>(incomingArcMultimap.get(inNode));
                        inNode = (org.apromore.pnml.NodeType) inNodeInArcs.get(0).getSource();
                        posXInNode = inNode.getGraphics().getPosition().getX();
                        posYInNode = inNode.getGraphics().getPosition().getY();

                        centeredX = posXOutNode.add((posXInNode.subtract(posXOutNode)).divide(BigDecimal.valueOf(3), BigDecimal.ROUND_UP));
                        centeredY = posYOutNode.add((posYInNode.subtract(posYOutNode)).divide(BigDecimal.valueOf(3), BigDecimal.ROUND_UP));

                    } else if (posXOutNode.equals(BigDecimal.ZERO)
                            && posYOutNode.equals(BigDecimal.ZERO)) {
                        /*
                         * Case if outgoing node has position x,y = 0,0.
                         * Formula:
                         * C = I + (1/3) * IA
                         * IA = A-I
                         *
                         * C -> current node position
                         * I -> incoming node position
                         * A -> outgoing node of the outgoing node
                         * IA -> distance between I and A
                         * */

                        ArrayList<ArcType> outNodeOutArcs = new ArrayList<>(outgoingArcMultimap.get(outNode));
                        outNode = (org.apromore.pnml.NodeType) outNodeOutArcs.get(0).getTarget();
                        posXOutNode = outNode.getGraphics().getPosition().getX();
                        posYOutNode = outNode.getGraphics().getPosition().getY();

                        centeredX = posXInNode.add((posXOutNode.subtract(posXInNode)).divide(BigDecimal.valueOf(3), BigDecimal.ROUND_UP));
                        centeredY = posYInNode.add((posYOutNode.subtract(posYInNode)).divide(BigDecimal.valueOf(3), BigDecimal.ROUND_UP));

                    } else {
                        /*
                         * Case if incoming and outgoing nodes have a valid position.
                         * Formula:
                         * C = (I + O) / 2
                         *
                         * C -> current node position (center point)
                         * I -> incoming node position
                         * O -> outgoing node position
                         * */

                        centeredX = (posXInNode.add(posXOutNode)).divide(BigDecimal.valueOf(2), BigDecimal.ROUND_UP);
                        centeredY = (posYInNode.add(posYOutNode)).divide(BigDecimal.valueOf(2), BigDecimal.ROUND_UP);

                    }
                    //Finally, set the position as the calculated center
                    node.getGraphics().getPosition().setX(centeredX);
                    node.getGraphics().getPosition().setY(centeredY);
                }
            }
        }

        //correct arc positions
        for (ArcType arc : getPNML().getNet().get(0).getArc()) {
            if (arc.getGraphics() != null)
                if (arc.getGraphics().getPosition() != null)
                    arc.getGraphics().getPosition().clear();
        }

        //Finally, position synthetic nodes at the very beginnig of the graph (e. g. in case of Message / Timer Event)
        ArrayList<ArcType> allArcs = new ArrayList<>(getPNML().getNet().get(0).getArc());
        ArrayList<NodeType> nodesBeforeFirstNonInserted = new ArrayList<>();

        //Find the first node in the graph
        ArrayList<NodeType> firstNodeList = findStartElement(allNodes, allArcs);

        //Find out which is the first node that was not inserted afterwards
        ArrayList<NodeType> firstNonInsertedNodeList = findFirstNonInsertedNode(firstNodeList, allArcs, nodesBeforeFirstNonInserted);

        //Correct the position of Nodes before first firstNonInsertedNode since traverseNodes only goes in one direction
        correctBeginningNodesPosition(firstNonInsertedNodeList, nodesBeforeFirstNonInserted, firstNodeList, allNodes, outgoingArcMultimap);
    }
    /**
     * @param transition transition, which should be checked
     * @return whether <var>transition</var> is silent
     */
    private boolean isSilent(TransitionType transition) {
        return transition.getName() == null;
    }

    //Method: Returns a Map containing ANF-PositionTypes of original ANF-Arcs that have two Arc-Points (e.g. loops) mapped with their original CPF-ID
    private Map<String, List<org.apromore.anf.PositionType>> findDoubleBendedArcs(AnnotationsType annotations){

        Map<String, List<org.apromore.anf.PositionType>> doubleBendedArcs = new HashMap();

        //Iterate all annotations and add to map if it has 4 Position-information
        //First position -> start point of arc; second -> arc point 1; third -> arc point 2; fourth -> end point of arc
        for(AnnotationType annotation : annotations.getAnnotation()){
            GraphicsType annotationGraphics = (GraphicsType) annotation;
            List<org.apromore.anf.PositionType> positionList = annotationGraphics.getPosition();

            if(positionList.size() == 4){
                ArrayList<org.apromore.anf.PositionType> arcPoints = new ArrayList<>();
                //Add 1 and 2 since they are the arc points
                arcPoints.add(positionList.get(1));
                arcPoints.add(positionList.get(2));
                doubleBendedArcs.put(annotation.getCpfId(), arcPoints);
            }
        }
        return doubleBendedArcs;
    }

    //Method: Position pnml nodes that are synthetic and between double bended anf-arcs
    private void positionDoubleBendedArcs(AnnotationsType annotations){

        //find all double bended arcs in anf
        Map<String, List<org.apromore.anf.PositionType>> doubleBendedArcs = findDoubleBendedArcs(annotations);
        List<ArcType> allArcs = getPNML().getNet().get(0).getArc();

        //iterate all pnml-arcs
        for(ArcType arc : allArcs){
            String arcId = arc.getId();
            Map<String, String> idMap = data.get_id_map();
            String arcOriginalId = getKeyByValue(idMap, arcId);

            //Check if pnml-arc is one replacing a double bended anf-arc
            if(doubleBendedArcs.containsKey(arcOriginalId)){
                List<org.apromore.anf.PositionType> arcPoints = doubleBendedArcs.get(arcOriginalId);
                org.apromore.anf.PositionType firstArcPoint = arcPoints.get(0);
                org.apromore.anf.PositionType secondArcPoint = arcPoints.get(1);

                //Calculate center between arc-points
                BigDecimal centeredX = (firstArcPoint.getX().add(secondArcPoint.getX())).divide(BigDecimal.valueOf(2), BigDecimal.ROUND_UP).subtract(BigDecimal.valueOf(20));
                BigDecimal centeredY = (firstArcPoint.getY().add(secondArcPoint.getY())).divide(BigDecimal.valueOf(2), BigDecimal.ROUND_UP).subtract(BigDecimal.valueOf(20));

                //find Node that was synthesized for double bended arc
                NodeType arcNode = (NodeType) arc.getTarget();

                if(!arcNode.getGraphics().getPosition().getY().equals(BigDecimal.ZERO)
                        && !arcNode.getGraphics().getPosition().getX().equals(BigDecimal.ZERO)){
                    arcNode = (NodeType) arc.getSource();
                }

                //finally, position node in center of arc points
                arcNode.getGraphics().getPosition().setX(centeredX);
                arcNode.getGraphics().getPosition().setY(centeredY);
            }
        }


    }

    public static <T, E> T getKeyByValue(Map<T, E> map, E value) {
        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (Objects.equals(value, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {

        final String HELP_TEXT = "A document in CPF format is read from standard input.\n" +
                                 "The PNML conversion is written to standard output.\n" +
                                 "Options:\n" +
                                 "-e  CPF edges treated as having a duration, converted to PNML places\n" +
                                 "-h  this help text\n" +
                                 "-t  CPF tasks treated as instantaneous, converted to PNML transitions\n" +
                                 "-v  validate input against the CPF XML schema";

        boolean validate = false;
        boolean isCpfTaskPnmlTransition = false;
        boolean isCpfEdgePnmlPlace = false;

        for(String arg: args) {
            switch(arg) {
            case "-e":
                isCpfEdgePnmlPlace = true;
                break;
            case "-h": case "-?": case "-help": case "--help":
                System.out.println(HELP_TEXT);
                System.exit(0);
            case "-t":
                isCpfTaskPnmlTransition = true;
                break;
            case "-v":
                validate = true;
                break;
            default:
                System.err.println(arg + " is not a supported option\n" + HELP_TEXT);
                System.exit(-1);
            }
        }

        CanonicalProcessType cpf = CPFSchema.unmarshalCanonicalFormat(System.in, validate).getValue();
        PNMLSchema.marshalPNMLFormat(System.out, (new Canonical2PNML(cpf, null, isCpfTaskPnmlTransition, isCpfEdgePnmlPlace)).getPNML(), false);
    }
}