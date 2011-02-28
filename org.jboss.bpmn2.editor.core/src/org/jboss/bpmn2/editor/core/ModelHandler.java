package org.jboss.bpmn2.editor.core;

import java.io.IOException;

import org.eclipse.bpmn2.Artifact;
import org.eclipse.bpmn2.Association;
import org.eclipse.bpmn2.BaseElement;
import org.eclipse.bpmn2.Bpmn2Factory;
import org.eclipse.bpmn2.Collaboration;
import org.eclipse.bpmn2.Definitions;
import org.eclipse.bpmn2.DocumentRoot;
import org.eclipse.bpmn2.FlowElement;
import org.eclipse.bpmn2.FlowElementsContainer;
import org.eclipse.bpmn2.FlowNode;
import org.eclipse.bpmn2.InteractionNode;
import org.eclipse.bpmn2.Lane;
import org.eclipse.bpmn2.LaneSet;
import org.eclipse.bpmn2.MessageFlow;
import org.eclipse.bpmn2.Participant;
import org.eclipse.bpmn2.Process;
import org.eclipse.bpmn2.RootElement;
import org.eclipse.bpmn2.SequenceFlow;
import org.eclipse.bpmn2.TextAnnotation;
import org.eclipse.bpmn2.util.Bpmn2ResourceImpl;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.query.conditions.eobjects.EObjectCondition;
import org.eclipse.emf.query.statements.FROM;
import org.eclipse.emf.query.statements.SELECT;
import org.eclipse.emf.query.statements.WHERE;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.emf.transaction.util.TransactionUtil;
import org.eclipse.graphiti.mm.pictograms.Diagram;

public class ModelHandler {
	public static final Bpmn2Factory FACTORY = Bpmn2Factory.eINSTANCE;

	Bpmn2ResourceImpl resource;

	ModelHandler() {
	}

	void createDefinitionsIfMissing() {
		EList<EObject> contents = resource.getContents();

		if (contents.isEmpty() || !(contents.get(0) instanceof DocumentRoot)) {
			TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(resource);

			if (domain != null) {
				final DocumentRoot docRoot = FACTORY.createDocumentRoot();
				final Definitions definitions = FACTORY.createDefinitions();
				Collaboration collaboration = FACTORY.createCollaboration();
				Participant participant = FACTORY.createParticipant();
				participant.setName("Internal");
				collaboration.getParticipants().add(participant);
				definitions.getRootElements().add(collaboration);

				domain.getCommandStack().execute(new RecordingCommand(domain) {
					@Override
					protected void doExecute() {
						docRoot.setDefinitions(definitions);
						resource.getContents().add(docRoot);
					}
				});
				return;
			}
		}
	}
	
	/**
	 * @param <T>
	 * @param target object that this element is being added to
	 * @param elem flow element to be added
	 * @return
	 */
	public <T extends FlowElement> T addFlowElement(Object target, T elem) {
		FlowElementsContainer container = getFlowElementContainer(target);
		container.getFlowElements().add(elem);
		return elem;
	}
	
	/**
	 * @param <A>
	 * @param target object that this artifact is being added to
	 * @param artifact artifact to be added
	 * @return
	 */
	public <A extends Artifact> A addArtifact(Object target, A artifact) {
		Process process = getOrCreateProcess(getParticipant(target));
		process.getArtifacts().add(artifact);
		return artifact;
	}
	
	public void moveFlowNode(FlowNode node, Object source, Object target) {
		FlowElementsContainer sourceContainer = getFlowElementContainer(source);
		FlowElementsContainer targetContainer = getFlowElementContainer(target);
		sourceContainer.getFlowElements().remove(node);
		targetContainer.getFlowElements().add(node);
		for(SequenceFlow flow : node.getOutgoing()) {
			sourceContainer.getFlowElements().remove(flow);
			targetContainer.getFlowElements().add(flow);
		}
	}
	
	public Participant addParticipant() {
		Collaboration collaboration = getCollaboration();
		Participant participant = FACTORY.createParticipant();
		collaboration.getParticipants().add(participant);
		return participant;
	}

	@Deprecated
	public void moveLane(Lane movedLane, Participant targetParticipant) {
		Participant sourceParticipant = getParticipant(movedLane);
		moveLane(movedLane, sourceParticipant, targetParticipant);
	}

	public void moveLane(Lane movedLane, Participant sourceParticipant, Participant targetParticipant) {
		Process sourceProcess = getOrCreateProcess(sourceParticipant);
		Process targetProcess = getOrCreateProcess(targetParticipant);
		for (FlowNode node : movedLane.getFlowNodeRefs()) {
			moveFlowNode(node, sourceProcess, targetProcess);
		}
		if (movedLane.getChildLaneSet() != null && !movedLane.getChildLaneSet().getLanes().isEmpty()) {
			for (Lane lane : movedLane.getChildLaneSet().getLanes()) {
				moveLane(lane, sourceParticipant, targetParticipant);
			}
		}
	}

	private Process getOrCreateProcess(Participant participant) {
		if (participant.getProcessRef() == null) {
			Process process = FACTORY.createProcess();
			process.setName("Process for " + participant.getName());
			getDefinitions().getRootElements().add(process);
			participant.setProcessRef(process);
		}
		return participant.getProcessRef();
	}

	public Lane createLane(Lane targetLane) {
		Lane lane = FACTORY.createLane();

		if (targetLane.getChildLaneSet() == null) {
			targetLane.setChildLaneSet(ModelHandler.FACTORY.createLaneSet());
		}

		LaneSet targetLaneSet = targetLane.getChildLaneSet();
		targetLaneSet.getLanes().add(lane);

		lane.getFlowNodeRefs().addAll(targetLane.getFlowNodeRefs());
		targetLane.getFlowNodeRefs().clear();

		return lane;
	}
	
	public Lane createLane(Object target) {
		Lane lane = FACTORY.createLane();
		FlowElementsContainer container = getFlowElementContainer(target);
		if (container.getLaneSets().isEmpty()) {
			container.getLaneSets().add(FACTORY.createLaneSet());
		}
		container.getLaneSets().get(0).getLanes().add(lane);
		return lane;
	}

	public void laneToTop(Lane lane) {
		LaneSet laneSet = FACTORY.createLaneSet();
		laneSet.getLanes().add(lane);
		Process process = getOrCreateProcess(getInternalParticipant());
		process.getLaneSets().add(laneSet);
	}

	public SequenceFlow createSequenceFlow(FlowNode source, FlowNode target) {
		SequenceFlow flow = addFlowElement(source, FACTORY.createSequenceFlow());
		flow.setSourceRef(source);
		flow.setTargetRef(target);
		return flow;
	}

	public MessageFlow createMessageFlow(InteractionNode source, InteractionNode target) {
		MessageFlow messageFlow = FACTORY.createMessageFlow();
		messageFlow.setSourceRef(source);
		messageFlow.setTargetRef(target);
		getCollaboration().getMessageFlows().add(messageFlow);
		return messageFlow;
	}

	public Association createAssociation(TextAnnotation annotation, BaseElement element) {
		Association association = addArtifact(element, FACTORY.createAssociation());
		association.setSourceRef(element);
		association.setTargetRef(annotation);
		return association;
	}

	public Collaboration getCollaboration() {
		for (RootElement element : getDefinitions().getRootElements()) {
			if (element instanceof Collaboration) {
				return (Collaboration) element;
			}
		}
		return null;
	}

	public Bpmn2ResourceImpl getResource() {
		return resource;
	}

	public Definitions getDefinitions() {
		return (Definitions) resource.getContents().get(0).eContents().get(0);
	}

	public void save() {
		TransactionalEditingDomain domain = TransactionUtil.getEditingDomain(resource);
		if (domain != null) {
			domain.getCommandStack().execute(new RecordingCommand(domain) {
				@Override
				protected void doExecute() {
					saveResource();
				}
			});
		} else {
			saveResource();
		}
	}

	private void saveResource() {
		try {
			resource.save(null);
		} catch (IOException e) {
			Activator.logError(e);
		}
	}

	void loadResource() {
		try {
			resource.load(null);
		} catch (IOException e) {
			Activator.logError(e);
		}
	}

	public Participant getInternalParticipant() {
		return getCollaboration().getParticipants().get(0);
	}
	
	public FlowElementsContainer getFlowElementContainer(Object o) {
		if (o == null) {
			return getOrCreateProcess(getInternalParticipant());
		}
		if (o instanceof Participant) {
			return getOrCreateProcess((Participant) o);
		}
		return findElementOfType(FlowElementsContainer.class, o);
	}

	public Participant getParticipant(Object o) {
		if (o == null || o instanceof Diagram) {
			return getInternalParticipant();
		}
		
		if (o instanceof Participant) {
			return (Participant) o;
		}
		
		Process process = findElementOfType(Process.class, o);
		
		for (Participant p : getCollaboration().getParticipants()) {
			if(p.getProcessRef().equals(process)) {
				return p;
			}
		}
		
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public <T extends BaseElement> T findElementOfType(Class<T> clazz, Object from) {
		if (!(from instanceof BaseElement)) {
			return null;
		}

		if (clazz.isAssignableFrom(from.getClass())) {
			return (T) from;
		}

		return findElementOfType(clazz, ((BaseElement) from).eContainer());
	}

	@SuppressWarnings("rawtypes")
	public Object[] getAll(final Class class1) {
		SELECT select = new SELECT(new FROM(resource.getContents()), new WHERE(new EObjectCondition() {

			@Override
			public boolean isSatisfied(EObject eObject) {
				return class1.isInstance(eObject);
			}
		}));
		return select.execute().toArray();
	}
}
