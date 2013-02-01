/*******************************************************************************
 * Copyright (c) 2012, 2013 Obeo.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.emf.compare.rcp.ui.structuremergeviewer.groups;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Lists.newArrayList;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.scope.IComparisonScope;

/**
 * Instances of this class will be used by EMF Compare in order to provide difference grouping facilities to
 * the structural differences view.
 * 
 * @author <a href="mailto:laurent.goubet@obeo.fr">Laurent Goubet</a>
 * @since 3.0
 */
public interface IDifferenceGroupProvider {

	/**
	 * This will be called internally by the grouping actions in order to determine how the differences should
	 * be grouped in the structural view.
	 * 
	 * @param comparison
	 *            The comparison which is to be displayed in the structural view. By default, its containment
	 *            tree will be displayed.
	 * @return The collection of difference groups that are to be displayed in the structural viewer. An empty
	 *         group will not be displayed at all. If {@code null}, we'll fall back to the default behavior.
	 */
	Iterable<? extends DifferenceGroup> getGroups(Comparison comparison);

	/**
	 * A human-readable label for this group. This will be displayed in the EMF Compare UI.
	 * 
	 * @return The label for this group.
	 */
	String getLabel();

	/**
	 * Set the label for this group. This will be displayed in the EMF Compare UI.
	 * 
	 * @param label
	 *            A human-readable label for this group.
	 */
	void setLabel(String label);

	/**
	 * Returns the initial activation state that the group should have.
	 * 
	 * @return The initial activation state that the group should have.
	 */
	boolean defaultSelected();

	/**
	 * Set the initial activation state that the group should have.
	 * 
	 * @param defaultSelected
	 *            The initial activation state that the group should have (true if the group should be active
	 *            by default).
	 */
	void setDefaultSelected(boolean defaultSelected);

	/**
	 * Returns the activation condition based on the scope and comparison objects.
	 * 
	 * @param scope
	 *            The scope on which the group provider will be applied.
	 * @param comparison
	 *            The comparison which is to be displayed in the structural view.
	 * @return The activation condition based on the scope and comparison objects.
	 */
	boolean isEnabled(IComparisonScope scope, Comparison comparison);

	/**
	 * A registry of {@link IDifferenceGroupProvider}.
	 */
	interface Registry {

		/**
		 * Returns the list of {@link IDifferenceGroupProvider} contained in the registry.
		 * 
		 * @param scope
		 *            The scope on which the group providers will be applied.
		 * @param comparison
		 *            The comparison which is to be displayed in the structural view.
		 * @return The list of {@link IDifferenceGroupProvider} contained in the registry.
		 */
		Collection<IDifferenceGroupProvider> getGroupProviders(IComparisonScope scope, Comparison comparison);

		/**
		 * Add to the registry the given {@link IDifferenceGroupProvider}.
		 * 
		 * @param provider
		 *            The given {@link IDifferenceGroupProvider}.
		 * @return The previous value associated with the class name of the given
		 *         {@link IDifferenceGroupProvider}, or null if there was no entry in the registry for the
		 *         class name.
		 */
		IDifferenceGroupProvider add(IDifferenceGroupProvider provider);

		/**
		 * Remove from the registry the {@link IDifferenceGroupProvider} designated by the given
		 * {@link String} .
		 * 
		 * @param className
		 *            The given {@link String} representing a {@link IDifferenceGroupProvider}.
		 * @return The {@link IDifferenceGroupProvider} designated by the given {@link String}.
		 */
		IDifferenceGroupProvider remove(String className);

		/**
		 * Clear the registry.
		 */
		void clear();
	}

	/**
	 * The default implementation of the {@link Registry}.
	 */
	public class RegistryImpl implements Registry {

		/** A map that associates the class name to theirs {@link IDifferenceGroupProvider}s. */
		private final Map<String, IDifferenceGroupProvider> map;

		/**
		 * Constructs the registry.
		 */
		public RegistryImpl() {
			map = new ConcurrentHashMap<String, IDifferenceGroupProvider>();
		}

		/**
		 * {@inheritDoc}
		 * 
		 * @see org.eclipse.emf.compare.rcp.ui.structuremergeviewer.groups.IDifferenceGroupProvider.Registry#getGroupProviders(IComparisonScope,
		 *      Comparison)
		 */
		public List<IDifferenceGroupProvider> getGroupProviders(IComparisonScope scope, Comparison comparison) {
			Iterable<IDifferenceGroupProvider> providers = filter(map.values(), isGroupProviderActivable(
					scope, comparison));
			List<IDifferenceGroupProvider> ret = newArrayList();
			for (IDifferenceGroupProvider provider : providers) {
				ret.add(provider);
			}
			return ret;
		}

		/**
		 * Returns a predicate that represents the activation condition based on the scope and comparison
		 * objects.
		 * 
		 * @param scope
		 *            The scope on which the group provider will be applied.
		 * @param comparison
		 *            The comparison which is to be displayed in the structural view.
		 * @return A predicate that represents the activation condition based on the scope and comparison
		 *         objects.
		 */
		static final Predicate<IDifferenceGroupProvider> isGroupProviderActivable(
				final IComparisonScope scope, final Comparison comparison) {
			return new Predicate<IDifferenceGroupProvider>() {
				/**
				 * {@inheritDoc}
				 * 
				 * @see com.google.common.base.Predicate#apply(java.lang.Object)
				 */
				public boolean apply(IDifferenceGroupProvider d) {
					return d.isEnabled(scope, comparison);
				}
			};
		}

		/**
		 * {@inheritDoc}
		 * 
		 * @see org.eclipse.emf.compare.rcp.ui.structuremergeviewer.groups.IDifferenceGroupProvider.Registry#add
		 *      (org.eclipse.emf.compare.rcp.ui.structuremergeviewer.groups.IDifferenceGroupProvider)
		 */
		public IDifferenceGroupProvider add(IDifferenceGroupProvider provider) {
			Preconditions.checkNotNull(provider);
			return map.put(provider.getClass().getName(), provider);
		}

		/**
		 * {@inheritDoc}
		 * 
		 * @see org.eclipse.emf.compare.rcp.ui.structuremergeviewer.groups.IDifferenceGroupProvider.Registry#remove(java.lang.String)
		 *      )
		 */
		public IDifferenceGroupProvider remove(String className) {
			return map.remove(className);
		}

		/**
		 * {@inheritDoc}
		 * 
		 * @see org.eclipse.emf.compare.rcp.ui.structuremergeviewer.groups.IDifferenceGroupProvider.Registry#clear()
		 */
		public void clear() {
			map.clear();
		}
	}

}