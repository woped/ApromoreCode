package org.apromore.canoniser.pnml.internal.canonical2pnml;

import org.apromore.pnml.NodeType;

import java.util.Comparator;

public class NodeTypeComparator implements Comparator<NodeType> {
    @Override
    public int compare(NodeType node1, NodeType node2) {

        if (node1.getGraphics().getPosition().getX().compareTo(node2.getGraphics().getPosition().getX()) == 0) {
            return node1.getGraphics().getPosition().getY().compareTo(node2.getGraphics().getPosition().getY());
        } else {
            return node1.getGraphics().getPosition().getX().compareTo(node2.getGraphics().getPosition().getX());
        }
    }
}
