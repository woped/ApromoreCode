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
import org.apromore.anf.AnnotationsType;
import org.apromore.canoniser.pnml.internal.canonical2pnml.*;
import org.apromore.cpf.CPFSchema;
import org.apromore.cpf.CanonicalProcessType;
import org.apromore.cpf.NetType;
import org.apromore.cpf.ResourceTypeType;
import org.apromore.pnml.PositionType;
import org.apromore.pnml.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class Canonical2PNML {

    static final private Logger LOGGER = Logger.getLogger(Canonical2PNML.class.getCanonicalName());

    private DataHandler data = new DataHandler();
    private RemoveConnectorTasks removeConnectorTasks = new RemoveConnectorTasks();
    private RemoveEvents removeEvents = new RemoveEvents();
    private RemoveState removeState = new RemoveState();
    private RemoveSplitJoins removeSplitJoins = new RemoveSplitJoins();
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
        if (annotations != null) {
            ta.mapNodeAnnotations(annotations);
        }

        // Expand XOR (and OR) routing from from PNML transitions to their complete structures
        AddXorOperators ax = new AddXorOperators();
        ax.setValues(data, ids);
        ax.add(cproc);
        ids = ax.getIds();
        cproc = ax.getCanonicalProcess();

        // Structural simplifications
        simplify();
    }

    //This method is used only in Canonical2PNMLUnitTest
    public Canonical2PNML(CanonicalProcessType cproc, AnnotationsType annotations, String filename) {
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
        cproc = uso.getCanonicalProcess();
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

    private void simplify() {
        //LOGGER.info("Performing structural simplifications"); 

        SetMultimap<org.apromore.pnml.NodeType, ArcType> incomingArcMultimap = HashMultimap.create();
        SetMultimap<org.apromore.pnml.NodeType, ArcType> outgoingArcMultimap = HashMultimap.create();

        // Index graph connectivity
        for (ArcType arc: data.getNet().getArc()) {
            incomingArcMultimap.put((org.apromore.pnml.NodeType) arc.getTarget(), arc);
            outgoingArcMultimap.put((org.apromore.pnml.NodeType) arc.getSource(), arc);
        }

        // When a synthetic place occurs adjacent to a silent transition on a branch, collapse them
        for (PlaceType place: data.getSynthesizedPlaces()) {
            if (incomingArcMultimap.get(place).size() == 1 &&
                outgoingArcMultimap.get(place).size() == 1) {

                // Assign: --incomingArc-> (place) --outgoingArc->
                ArcType incomingArc = incomingArcMultimap.get(place).iterator().next();
                ArcType outgoingArc = outgoingArcMultimap.get(place).iterator().next();

                TransitionType transition = (TransitionType) outgoingArc.getTarget();
                if (incomingArcMultimap.get(transition).size() == 1 && isSilent(transition)) {
                    // Collapse synthesized place followed by silent transition

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
        data.getSynthesizedPlaces().clear();
        //LOGGER.info("Performed structural simplifications");      


      //layout optimization
        ArrayList<NodeType> allNodes = new ArrayList<>();
        java.util.List<NodeType> insertedNodes = Collections.synchronizedList(new ArrayList<>());
        allNodes.addAll(getPNML().getNet().get(0).getPlace());
        allNodes.addAll(getPNML().getNet().get(0).getTransition());
        Collections.sort(allNodes, new NodeTypeComparator());
        final int value1 = 80;

        int firstNonInsertedNode = 0;
        //find first non inserted node
        while(firstNonInsertedNode < allNodes.size()-1 && allNodes.get(firstNonInsertedNode).getGraphics().getPosition().isInsertedNode()){
        	firstNonInsertedNode += 1;
        }

        if(firstNonInsertedNode != 0){
            //search for inserted nodes in correct order
            NodeType nonInsertedNode = allNodes.get(firstNonInsertedNode);
            traverseNodes(nonInsertedNode, insertedNodes, outgoingArcMultimap, incomingArcMultimap);
        }

        //correct arc positions
        for(int i = 0; i< getPNML().getNet().get(0).getArc().size(); i++){
        	if(getPNML().getNet().get(0).getArc().get(i).getGraphics() != null)
        		if(getPNML().getNet().get(0).getArc().get(i).getGraphics().getPosition() != null)
        			getPNML().getNet().get(0).getArc().get(i).getGraphics().getPosition().clear();
        }

	}

    private void traverseNodes(NodeType node,java.util.List<NodeType> insertedNodesList, SetMultimap<org.apromore.pnml.NodeType, ArcType> outgoingArcMultimap,SetMultimap<org.apromore.pnml.NodeType, ArcType> incomingArcMultimap){

    	final BigDecimal minDistance = new BigDecimal(70);
    	final BigDecimal minDistanceY = new BigDecimal(80);
    	Set<ArcType> tempArcs = outgoingArcMultimap.get(node);
    	Set<ArcType> tempInArcs = incomingArcMultimap.get(node);

    	if(!tempArcs.isEmpty()){
    		for(ArcType arc: tempArcs){
    			NodeType nodeTemp = (NodeType) arc.getTarget();
    			BigDecimal biggestX = new BigDecimal(0);

    			if(nodeTemp.getGraphics().getPosition().isInsertedNode()){
    				//x
    				Set<ArcType> inGoingArcs = incomingArcMultimap.get(nodeTemp);
    				for(ArcType inArc : inGoingArcs){
    					NodeType inNode = (NodeType) inArc.getSource();
    					if(inNode.getGraphics().getPosition().getX().compareTo(biggestX) == 1){
    						biggestX = inNode.getGraphics().getPosition().getX();
    					}
    				}

    				//set X of inserted nodes
    				nodeTemp.getGraphics().getPosition().setX(node.getGraphics().getPosition().getX().add(minDistance));

    				//y
    				if(tempInArcs.size() > 1){

    					BigDecimal averageY = new BigDecimal(0);

    					for(ArcType a: tempInArcs){
    						averageY = averageY.add(((NodeType) a.getSource()).getGraphics().getPosition().getY());
    					}
    					node.getGraphics().getPosition().setY(averageY.divide(new BigDecimal(tempInArcs.size()), 2, RoundingMode.HALF_UP));
    				}

    				double x = 0;

    				if(tempArcs.size() >1){

    					if (tempArcs.size()%2 == 0){

    						x = tempArcs.size()/2;

    						for (ArcType a: tempArcs){
    							NodeType n = (NodeType)a.getTarget();
    							n.getGraphics().getPosition().setY(node.getGraphics().getPosition().getY().add(minDistanceY.multiply(new BigDecimal(x-0.5))));
    							x--;
    						}

    					} else {

    						x = Math.floor(tempArcs.size()/2);

    						for (ArcType a: tempArcs){
    							NodeType n = (NodeType)a.getTarget();
    							n.getGraphics().getPosition().setY(node.getGraphics().getPosition().getY().add(minDistanceY.multiply(new BigDecimal(x))));
    							x--;
    						}

    					}


    				}else{
    					nodeTemp.getGraphics().getPosition().setY(node.getGraphics().getPosition().getY());
    				}

    			}

				//move next nodes if mindistance is greater
				Set<ArcType> outgoingArcs = outgoingArcMultimap.get(nodeTemp);
				for(ArcType outArc : outgoingArcs){
					NodeType outNode = (NodeType) outArc.getTarget();

					if(outNode.getGraphics().getPosition().getX().subtract(nodeTemp.getGraphics().getPosition().getX()).compareTo(minDistance.add(new BigDecimal(40))) == -1){
						outNode.getGraphics().getPosition().setX(nodeTemp.getGraphics().getPosition().getX().add(minDistance.add(new BigDecimal(40))));
					}
				}
    		}

    		for(ArcType arc: tempArcs){
    			traverseNodes((NodeType) arc.getTarget(), insertedNodesList, outgoingArcMultimap, incomingArcMultimap);
    		}
    	}
    }
    /**
     * @param transition
     * @return whether <var>transition</var> is silent
     */
    private boolean isSilent(TransitionType transition) {
        return transition.getName() == null;
    }

    public static void main(String[] args) throws Exception {

        final String HELP_TEXT = "A document in CPF format is read from standard input.\n" +
                                 "The PNML conversion is written to standard output.\n" +
                                 "Options:\n" +
                                 "-e  CPF edges treated as having a duration, converted tp PNML places\n" +
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