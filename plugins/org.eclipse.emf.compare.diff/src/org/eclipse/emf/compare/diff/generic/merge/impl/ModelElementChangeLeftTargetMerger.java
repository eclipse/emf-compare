/*******************************************************************************
 * Copyright (c) 2006, 2007 Obeo.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.emf.compare.diff.generic.merge.impl;

import java.util.Iterator;

import org.eclipse.emf.compare.EMFComparePlugin;
import org.eclipse.emf.compare.diff.metamodel.ModelElementChangeLeftTarget;
import org.eclipse.emf.compare.diff.metamodel.ReferenceChangeLeftTarget;
import org.eclipse.emf.compare.util.EFactory;
import org.eclipse.emf.compare.util.FactoryException;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Merger for an {@link ModelElementChangeLeftTarget} operation.<br/>
 * <p>
 * Are considered for this merger :
 * <ul>
 * <li>{@link RemoveModelElement}</li>
 * <li>{@link RemoteAddModelElement}</li>
 * </ul>
 * </p>
 * 
 * @author Cedric Brun <a href="mailto:cedric.brun@obeo.fr">cedric.brun@obeo.fr</a>
 */
public class ModelElementChangeLeftTargetMerger extends DefaultMerger {
	/**
	 * {@inheritDoc}
	 *
	 * @see org.eclipse.emf.compare.diff.merge.api.AbstractMerger#applyInOrigin()
	 */
	@Override
	public void applyInOrigin() {
		final ModelElementChangeLeftTarget diff = (ModelElementChangeLeftTarget)this.diff;
		final EObject element = diff.getLeftElement();
		final EObject parent = diff.getLeftElement().eContainer();
		EcoreUtil.remove(element);
		// now removes all the dangling references
		removeDanglingReferences(parent);
		super.applyInOrigin();
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.eclipse.emf.compare.diff.merge.api.AbstractMerger#undoInTarget()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void undoInTarget() {
		final ModelElementChangeLeftTarget diff = (ModelElementChangeLeftTarget)this.diff;
		// we should copy the element to the Origin one.
		final EObject origin = diff.getRightParent();
		final EObject element = diff.getLeftElement();
		final EObject newOne = EcoreUtil.copy(element);
		final EReference ref = element.eContainmentFeature();
		if (ref != null) {
			try {
				EFactory.eAdd(origin, ref.getName(), newOne);
				copyXMIID(element, newOne);
			} catch (FactoryException e) {
				EMFComparePlugin.log(e, true);
			}
		} else {
			findRightResource().getContents().add(newOne);
		}
		// we should now have a look for RemovedReferencesLinks needing elements to apply
		final Iterator siblings = getDiffModel().eAllContents();
		while (siblings.hasNext()) {
			final Object op = siblings.next();
			if (op instanceof ReferenceChangeLeftTarget) {
				final ReferenceChangeLeftTarget link = (ReferenceChangeLeftTarget)op;
				// now if I'm in the target References I should put my copy in the origin
				if (link.getLeftRemovedTarget().equals(element)) {
					link.setRightRemovedTarget(newOne);
				}
			}
		}
		super.undoInTarget();
	}
}
