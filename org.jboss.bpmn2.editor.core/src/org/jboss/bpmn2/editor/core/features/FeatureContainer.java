package org.jboss.bpmn2.editor.core.features;

import org.eclipse.bpmn2.BaseElement;
import org.eclipse.graphiti.features.IAddFeature;
import org.eclipse.graphiti.features.ICreateFeature;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.features.IUpdateFeature;

public interface FeatureContainer {
	
	boolean canApplyTo(BaseElement element);
	
	ICreateFeature getCreateFeature(IFeatureProvider fp);
	
	IAddFeature getAddFeature(IFeatureProvider fp);
	
	IUpdateFeature getUpdateFeature(IFeatureProvider fp);
}