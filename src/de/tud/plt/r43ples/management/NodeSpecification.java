package de.tud.plt.r43ples.management;

import java.util.ArrayList;

/**
 * This class saves the predecessors and the successors of a node.
 * 
 * @author Stephan Hensel
 * 
 */
public class NodeSpecification {

	/** The predecessors of the node */
	private ArrayList<NodeSpecification> predecessor;
	/** The successors of the node */
	private ArrayList<NodeSpecification> successor;
	/** The full graph of a tag or branch if available */
	private String fullGraph = null;
	/** The revision number of the node */
	private String revisionNumber;
	
	/**
	 * The constructor.
	 */
	public NodeSpecification(String node) {
		predecessor = new ArrayList<NodeSpecification>();
		successor = new ArrayList<NodeSpecification>();
		revisionNumber = node;
	}

	
	/**
	 * @return the first predecessor
	 */
	public NodeSpecification getFirstPredecessor() {
		return predecessor.get(0);
	}

	
	/**
	 * @param predecessor the predecessor to add
	 */
	public void addPredecessor(NodeSpecification predecessor) {
		this.predecessor.add(predecessor);
	}

	
	/**
	 * @return the first successor
	 */
	public NodeSpecification getFirstSuccessor() {
		return successor.get(0);
	}
	
	
	/**
	 * @return the successors
	 */
	public ArrayList<NodeSpecification> getSuccessors() {
		return successor;
	}
	
	/**
	 * @return the successors
	 */
	public boolean hasSuccessor() {
		return (successor.size()>0);
	}

	
	/**
	 * @param successor the successor to add
	 */
	public void addSuccessor(NodeSpecification successor) {
		this.successor.add(successor);
	}


	/**
	 * @return the fullGraph
	 */
	public String getFullGraph() {
		return fullGraph;
	}


	/**
	 * @param fullGraph the fullGraph to set
	 */
	public void setFullGraph(String fullGraph) {
		this.fullGraph = fullGraph;
	}


	/**
	 * @return the revisionNumber
	 */
	public String getRevisionNumber() {
		return revisionNumber;
	}
}