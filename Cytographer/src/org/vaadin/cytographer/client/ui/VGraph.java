package org.vaadin.cytographer.client.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.vaadin.gwtgraphics.client.Group;
import org.vaadin.gwtgraphics.client.Shape;
import org.vaadin.gwtgraphics.client.VectorObject;
import org.vaadin.gwtgraphics.client.shape.Rectangle;

import com.vaadin.terminal.gwt.client.UIDL;
import com.vaadin.terminal.gwt.client.VConsole;

public class VGraph extends VectorObject {

	private Map<String, VEdge> edges = new HashMap<String, VEdge>();
	private Map<String, VNode> nodes = new HashMap<String, VNode>();
	private Map<VNode, Set<VEdge>> shapeToEdgesMap = new HashMap<VNode, Set<VEdge>>();
	private final Set<VNode> paintedShapes = new HashSet<VNode>();
	private final Set<VNode> selectedShapes = new HashSet<VNode>();
	private final Set<VEdge> selectedEdges = new HashSet<VEdge>();

	private VNode movedShape = null;
	private final VVisualStyle style;
	private final VFocusDrawingArea canvas;
	private final VCytographer cytographer;

	private final int gwidth;
	private final int gheight;

	public VGraph(final VCytographer cytographer, final VVisualStyle style, final VFocusDrawingArea canvas, final int gwidth,
			final int gheight) {
		this.cytographer = cytographer;
		this.style = style;
		this.canvas = canvas;
		this.gwidth = gwidth;
		this.gheight = gheight;
	}

	public void repaintGraph(final UIDL uidl) {
		parseGraphFromUIDL(uidl, style);
		paintGraph();
	}

	public void refreshGraphFromUIDL(final UIDL uidl) {
		for (int i = 0; i < uidl.getChildCount(); i++) {
			final UIDL child = uidl.getChildUIDL(i);
			final String name = child.getStringAttribute("name");
			if (name == null || name.isEmpty()) {
				continue;
			}
			final String node1name = child.getStringAttribute("node1");
			final String node2name = child.getStringAttribute("node2");

			final VNode node1 = nodes.get(node1name);
			final VNode node2 = nodes.get(node2name);

			node1.refreshNodeData(child, style);
			node2.refreshNodeData(child, style);

			final VEdge edge = edges.get(name);
			edge.refreshEdgeData(child, style);
		}
		paintGraph();
	}

	public void parseGraphFromUIDL(final UIDL uidl, final VVisualStyle style) {
		edges = new HashMap<String, VEdge>();
		nodes = new HashMap<String, VNode>();
		shapeToEdgesMap = new HashMap<VNode, Set<VEdge>>();

		for (int i = 0; i < uidl.getChildCount(); i++) {
			final UIDL child = uidl.getChildUIDL(i);
			final String name = child.getStringAttribute("name");
			if (name == null || name.isEmpty()) {
				continue;
			}
			final String node1name = child.getStringAttribute("node1");
			final String node2name = child.getStringAttribute("node2");

			VNode node1 = nodes.get(node1name);
			VNode node2 = nodes.get(node2name);
			if (node1 == null && node1name != null) {
				node1 = VNode.createANode(child, cytographer, this, node1name, true, style);
				updateEdges(node1, false);
				nodes.put(node1name, node1);
			}
			if (node2 == null && node2name != null) {
				node2 = VNode.createANode(child, cytographer, this, node2name, false, style);
				updateEdges(node2, false);
				nodes.put(node2name, node2);
			}
			if (node1 != null && node2 != null) {
				final VEdge edge = VEdge.createAnEdge(child, cytographer, this, name, node1, node2, style);
				createEdgeConnections(edge);
				edges.put(name, edge);
			}
		}
	}

	public void paintGraph(final Shape... updatedShapes) {
		if (updatedShapes == null || updatedShapes.length == 0) {
			canvas.clear();
			paintedShapes.clear();
			final Rectangle bg = new Rectangle(0, 0, gwidth * 2, gheight);
			bg.setFillColor(style.getBgColor());
			bg.setStrokeColor(style.getBgColor());
			canvas.add(bg);
			for (final Map.Entry<String, VEdge> entry : edges.entrySet()) {
				final VEdge edge = entry.getValue();
				canvas.add(edge);
				if (style.isTextsVisible()) {
					canvas.add(edge.getText());
				}
			}
			for (final Map.Entry<String, VEdge> entry : edges.entrySet()) {
				final VEdge edge = entry.getValue();
				final VNode n1 = edge.getSecondNode();
				if (!paintedShapes.contains(n1) && isInPaintedArea(n1)) {
					canvas.add(n1);
					paintedShapes.add(n1);
				}
				final VNode n2 = edge.getFirstNode();
				if (!paintedShapes.contains(n2) && isInPaintedArea(n1)) {
					canvas.add(n2);
					paintedShapes.add(n2);
				}
			}
			// paint nodes that doesn't have edges
			for (final Map.Entry<String, VNode> entry : nodes.entrySet()) {
				final VNode n = entry.getValue();
				if (!paintedShapes.contains(n) && isInPaintedArea(n)) {
					canvas.add(n);
					paintedShapes.add(n);
				}
			}
		} else {
			for (final Shape s : updatedShapes) {
				canvas.remove(s);
				canvas.add(s);
			}
		}
	}

	public void updateGraphProperties(final VVisualStyle style) {
		for (final VNode n : getNodes().values()) {
			n.setTextVisible(style.isTextsVisible());
			n.setRadius(style.getNodeSize());
			updateEdges(n, true);
		}
	}

	public void updateNode(final UIDL uidl, final String id) {
		final VNode node = getNodes().get(id);
		// TODO
	}

	public void moveGraph(final float x, final float y) {
		for (final VNode shape : getPaintedShapes()) {
			shape.setX(shape.getX() - x);
			shape.setY(shape.getY() - y);
			updateEdges(shape, false);
		}
		paintGraph();
	}

	public void updateEdges(final VNode node, final boolean repaint) {
		final Set<VEdge> edgs = shapeToEdgesMap.get(node);

		if (edgs == null || edgs.isEmpty()) {
			// single nodes
			if (paintedShapes.contains(node)) {
				canvas.remove(node);
			}
			canvas.add(node);
			paintedShapes.add(node);
			return;
		}
		for (final VEdge e : edgs) {
			// update edge positions
			if (e.getFirstNode().equals(node)) {
				e.setX1((int) node.getX());
				e.setY1((int) node.getY());
				e.getText().setX((int) (node.getX() + e.getSecondNode().getX()) / 2);
				e.getText().setY((int) (node.getY() + e.getSecondNode().getY()) / 2);
			} else {
				e.setX2((int) node.getX());
				e.setY2((int) node.getY());
				e.getText().setX((int) (e.getFirstNode().getX() + node.getX()) / 2);
				e.getText().setY((int) (e.getFirstNode().getY() + node.getY()) / 2);
			}
			if (repaint) {
				updateEdgeIntoCanvas(e, node, false);
			}
		}
		if (movedShape != null) {
			canvas.remove(movedShape);
			canvas.add(movedShape);
		}
	}

	public void updateEdgeIntoCanvas(final VEdge e, final VNode node, final boolean bothNodes) {
		canvas.remove(e);
		if (style.isTextsVisible()) {
			canvas.remove(e.getText());
		}
		canvas.add(e);
		if (style.isTextsVisible()) {
			canvas.add(e.getText());
		}
		if (!bothNodes) {
			if (e.getFirstNode().equals(node)) {
				canvas.remove(e.getSecondNode());
				canvas.add(e.getSecondNode());
			} else {
				canvas.remove(e.getFirstNode());
				canvas.add(e.getFirstNode());
			}
		} else {
			canvas.remove(e.getSecondNode());
			canvas.remove(e.getFirstNode());
			canvas.add(e.getSecondNode());
			canvas.add(e.getFirstNode());
		}
	}

	public Map<String, VNode> getNodes() {
		return nodes;
	}

	private boolean isInPaintedArea(final VNode n1) {
		// TODO
		return true;
	}

	public Set<VNode> getSelectedShapes() {
		return selectedShapes;
	}

	public Set<VEdge> getSelectedEdges() {
		return selectedEdges;
	}

	public Set<VNode> getPaintedShapes() {
		return paintedShapes;
	}

	@Override
	protected Class<? extends VectorObject> getType() {
		return Group.class;
	}

	public void setEdgeSelected(final VEdge edge, final boolean selected) {
		if (selected) {
			edge.setStrokeColor(style.getEdgeSelectionColor());
			getSelectedEdges().add(edge);
		} else {
			edge.setStrokeColor(edge.getOrginalStrokeColor());
			getSelectedEdges().remove(edge);
		}
	}

	public void setNodeSelected(final VNode node, final boolean selected) {
		if (selected) {
			node.setFillColor(style.getNodeSelectionColor());
			getSelectedShapes().add(node);
		} else {
			node.setFillColor(node.getOriginalFillColor());
			getSelectedShapes().remove(node);
		}
	}

	public void setMovedShape(final VNode vNode) {
		movedShape = vNode;
	}

	public VNode getMovedShape() {
		return movedShape;
	}

	public void addNode(final VNode node) {
		canvas.add(node);
		nodes.put(node.getName(), node);
		paintedShapes.add(node);
	}

	public void addEdge(final VEdge edge) {
		canvas.add(edge);
		createEdgeConnections(edge);
		edges.put(edge.getName(), edge);
		updateEdgeIntoCanvas(edge, null, true);
	}

	private void createEdgeConnections(final VEdge edge) {
		Set<VEdge> edgs1 = shapeToEdgesMap.get(edge.getFirstNode());
		if (edgs1 == null) {
			edgs1 = new HashSet<VEdge>();
			edgs1.add(edge);
			shapeToEdgesMap.put(edge.getFirstNode(), edgs1);
		} else {
			edgs1.add(edge);
		}

		Set<VEdge> edgs2 = shapeToEdgesMap.get(edge.getSecondNode());
		if (edgs2 == null) {
			edgs2 = new HashSet<VEdge>();
			edgs2.add(edge);
			shapeToEdgesMap.put(edge.getSecondNode(), edgs2);
		} else {
			edgs2.add(edge);
		}
	}

	public void removeNode(final VNode node) {
		canvas.remove(node);
		paintedShapes.remove(node);
		if (nodes.remove(node.getName()) == null) {
			VConsole.log("node not found" + node.getName());
		}
		selectedShapes.remove(node);

		final Set<VEdge> edgs = shapeToEdgesMap.get(node);
		for (final VEdge edge : edgs) {
			if (edges.remove(edge.getName()) == null) {
				VConsole.log("edge not found " + edge.toString());
			}
			canvas.remove(edge);
			selectedEdges.remove(edge);
		}
		if (shapeToEdgesMap.remove(node) == null) {
			VConsole.log("edgeset not found" + node.getName());
		}
	}

	public void removeEdge(final VEdge edge) {
		canvas.remove(edge);
		edges.remove(edge.getName());
		selectedEdges.remove(edge);
		final VNode node1 = edge.getFirstNode();
		final VNode node2 = edge.getSecondNode();
		removeEdgeFromMap(edge, node1);
		removeEdgeFromMap(edge, node2);
	}

	private void removeEdgeFromMap(final VEdge edge, final VNode node) {
		if (node != null) {
			final Set<VEdge> vedges = shapeToEdgesMap.get(node);
			if (vedges != null) {
				vedges.remove(edge);
			}
		}
	}

	public Map<String, VEdge> getEdges() {
		return edges;
	}
}
