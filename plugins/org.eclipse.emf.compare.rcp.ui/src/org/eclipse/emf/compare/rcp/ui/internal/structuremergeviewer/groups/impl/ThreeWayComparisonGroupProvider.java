/*******************************************************************************
 * Copyright (c) 2013, 2016 Obeo and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Obeo - initial API and implementation
 *     Stefan Dirix - bug 488941
 *     Simon Delisle, Edgar Mueller - bug 486923
 *     Tanja Mayerhofer - bug 501864
 *******************************************************************************/
package org.eclipse.emf.compare.rcp.ui.internal.structuremergeviewer.groups.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.and;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterators.concat;
import static com.google.common.collect.Iterators.filter;
import static com.google.common.collect.Iterators.transform;
import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.emf.compare.utils.EMFComparePredicates.fromSide;
import static org.eclipse.emf.compare.utils.EMFComparePredicates.hasConflict;
import static org.eclipse.emf.compare.utils.EMFComparePredicates.hasState;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.UnmodifiableIterator;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Conflict;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.DifferenceState;
import org.eclipse.emf.compare.impl.ConflictImpl;
import org.eclipse.emf.compare.provider.utils.ComposedStyledString;
import org.eclipse.emf.compare.provider.utils.IStyledString;
import org.eclipse.emf.compare.provider.utils.IStyledString.IComposedStyledString;
import org.eclipse.emf.compare.provider.utils.IStyledString.Style;
import org.eclipse.emf.compare.rcp.ui.internal.EMFCompareRCPUIMessages;
import org.eclipse.emf.compare.rcp.ui.internal.configuration.SideLabelProvider;
import org.eclipse.emf.compare.rcp.ui.internal.structuremergeviewer.nodes.ConflictNode;
import org.eclipse.emf.compare.rcp.ui.structuremergeviewer.groups.AbstractDifferenceGroupProvider;
import org.eclipse.emf.compare.rcp.ui.structuremergeviewer.groups.IDifferenceGroup;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.ECrossReferenceAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * This implementation of a
 * {@link org.eclipse.emf.compare.rcp.ui.structuremergeviewer.groups.IDifferenceGroupProvider} will be used to
 * group the differences by their {@link DifferenceSource side} : left, right and conflicts.
 * 
 * @author <a href="mailto:axel.richard@obeo.fr">Axel Richard</a>
 * @since 4.0
 */
public class ThreeWayComparisonGroupProvider extends AbstractDifferenceGroupProvider {

	/**
	 * {@inheritDoc}
	 * 
	 * @see org.eclipse.emf.compare.rcp.ui.structuremergeviewer.groups.IDifferenceGroupProvider#isEnabled(org
	 *      .eclipse.emf.compare.scope.IComparisonScope, org.eclipse.emf.compare.Comparison)
	 */
	@Override
	public boolean isEnabled(IComparisonScope scope, Comparison comparison) {
		if (comparison != null && comparison.isThreeWay()) {
			return true;
		}
		return false;
	}

	/**
	 * Specialized {@link BasicDifferenceGroupImpl} for Conflicts.
	 * 
	 * @author <a href="mailto:axel.richard@obeo.fr">Axel Richard</a>
	 */
	public static class ConflictsGroupImpl extends BasicDifferenceGroupImpl {

		/**
		 * {@inheritDoc}.
		 * 
		 * @see org.eclipse.emf.compare.rcp.ui.internal.structuremergeviewer.groups.impl.BasicDifferenceGroupImpl#BasicDifferenceGroupImpl(org.eclipse.emf.compare.Comparison,
		 *      java.lang.Iterable, com.google.common.base.Predicate, java.lang.String)
		 */
		public ConflictsGroupImpl(Comparison comparison, Predicate<? super Diff> filter, String name,
				ECrossReferenceAdapter crossReferenceAdapter) {
			super(comparison, filter, name, crossReferenceAdapter);
		}

		/**
		 * In conflicts, a special case must be handled for refining diffs: If they are not part of the same
		 * conflict then they should not be in the same group as the refined diff.
		 * 
		 * @param diff
		 *            The difference
		 * @return <code>true</code> if the diff refines nothing or if its conflict does not contain all the
		 *         diffs it refines.
		 */
		@Override
		protected boolean mustDisplayAsDirectChildOfMatch(Diff diff) {
			return diff.getRefines().isEmpty() || (diff.getConflict() != null
					&& !diff.getConflict().getDifferences().containsAll(diff.getRefines()));
		}

		@Override
		protected void doBuildSubTrees() {
			for (Conflict conflict : getComparison().getConflicts()) {
				ConflictGroup conflictGroup = new ConflictGroup(conflict);
				ConflictNodeBuilder builder = new ConflictNodeBuilder(conflictGroup, this);
				ConflictNode conflictNode = builder.buildNode();
				children.add(conflictNode);
			}
		}

		/**
		 * This implementation of {@link Conflict} is used to re-define conflicts for the SMV. Conflicts are
		 * re-define to contain refined diffs instead of refining diffs.
		 * 
		 * @author <a href="mailto:tmayerhofer@eclipsesource.com">Tanja Mayerhofer</a>
		 */
		public static class ConflictGroup extends ConflictImpl {

			private final Conflict conflict;

			private EList<Diff> diffs = new BasicEList<Diff>();

			public ConflictGroup(Conflict conflict) {
				this.conflict = checkNotNull(conflict);
				this.diffs.addAll(computeDiffs());
			}

			/**
			 * Computes the re-defined diffs of the conflict. In particular, refining diffs are replaces by
			 * refined diffs.
			 * 
			 * @return The set of re-defined diffs of the conflict
			 */
			private Set<Diff> computeDiffs() {
				LinkedHashSet<Diff> computedDiffs = new LinkedHashSet<Diff>();
				for (Diff diff : conflict.getDifferences()) {
					if (diff.getRefines().isEmpty()) {
						computedDiffs.add(diff);
					} else {
						computedDiffs.addAll(getRootRefinedDiffs(diff));
					}
				}
				return computedDiffs;
			}

			/**
			 * Determines the leaf refined diff of a refining diff, i.e., a refined diff that is not refining
			 * another diff.
			 * 
			 * @param diff
			 *            The diff for which the leaf refined diff is to be determined
			 * @return The leaf refined diff of the provided (refining diff)
			 */
			private List<Diff> getRootRefinedDiffs(Diff diff) {
				List<Diff> rootRefinedDiffs = newArrayList();
				for (Diff refinedDiff : diff.getRefines()) {
					if (refinedDiff.getRefines().isEmpty()) {
						rootRefinedDiffs.add(refinedDiff);
					} else {
						rootRefinedDiffs.addAll(getRootRefinedDiffs(refinedDiff));
					}
				}
				return rootRefinedDiffs;
			}

			@Override
			public ConflictKind getKind() {
				return this.conflict.getKind();
			}

			@Override
			public EList<Diff> getDifferences() {
				return this.diffs;
			}

		}

		/**
		 * {@inheritDoc}
		 * 
		 * @see org.eclipse.emf.compare.rcp.ui.internal.structuremergeviewer.groups.impl.BasicDifferenceGroupImpl#getStyledName()
		 */
		@Override
		public IComposedStyledString getStyledName() {
			final IStyledString.IComposedStyledString ret = new ComposedStyledString();
			Iterator<EObject> eAllContents = concat(transform(getChildren().iterator(), E_ALL_CONTENTS));
			Iterator<EObject> eAllData = transform(eAllContents, TREE_NODE_DATA);
			UnmodifiableIterator<Diff> eAllDiffData = filter(eAllData, Diff.class);
			Collection<Diff> diffs = Sets.newHashSet(eAllDiffData);
			boolean unresolvedDiffs = any(diffs, and(hasState(DifferenceState.UNRESOLVED),
					hasConflict(ConflictKind.REAL, ConflictKind.PSEUDO)));
			if (unresolvedDiffs) {
				ret.append("> ", Style.DECORATIONS_STYLER); //$NON-NLS-1$
			}
			ret.append(getName());
			return ret;
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see org.eclipse.emf.compare.rcp.ui.internal.structuremergeviewer.groups.impl.AbstractBuildingDifferenceGroupProvider#buildGroups(org.eclipse.emf.compare.Comparison)
	 */
	@Override
	protected Collection<? extends IDifferenceGroup> buildGroups(Comparison comparison2) {
		Adapter adapter = EcoreUtil.getAdapter(getComparison().eAdapters(), SideLabelProvider.class);

		final String leftLabel, rightLabel;
		if (adapter instanceof SideLabelProvider) {
			SideLabelProvider labelProvider = (SideLabelProvider)adapter;
			leftLabel = labelProvider.getLeftLabel();
			rightLabel = labelProvider.getRightLabel();
		} else {
			leftLabel = EMFCompareRCPUIMessages.getString("ThreeWayComparisonGroupProvider.left.label"); //$NON-NLS-1$
			rightLabel = EMFCompareRCPUIMessages.getString("ThreeWayComparisonGroupProvider.right.label"); //$NON-NLS-1$
		}

		final ConflictsGroupImpl conflicts = new ConflictsGroupImpl(getComparison(),
				hasConflict(ConflictKind.REAL, ConflictKind.PSEUDO),
				EMFCompareRCPUIMessages.getString("ThreeWayComparisonGroupProvider.conflicts.label"), //$NON-NLS-1$
				getCrossReferenceAdapter());
		conflicts.buildSubTree();

		final BasicDifferenceGroupImpl leftSide = new BasicDifferenceGroupImpl(getComparison(),
				Predicates.and(fromSide(DifferenceSource.LEFT),
						Predicates.not(hasConflict(ConflictKind.REAL, ConflictKind.PSEUDO))),
				leftLabel, getCrossReferenceAdapter());
		leftSide.buildSubTree();

		final BasicDifferenceGroupImpl rightSide = new BasicDifferenceGroupImpl(getComparison(),
				Predicates.and(fromSide(DifferenceSource.RIGHT),
						Predicates.not(hasConflict(ConflictKind.REAL, ConflictKind.PSEUDO))),
				rightLabel, getCrossReferenceAdapter());
		rightSide.buildSubTree();

		return ImmutableList.of(conflicts, leftSide, rightSide);
	}
}
