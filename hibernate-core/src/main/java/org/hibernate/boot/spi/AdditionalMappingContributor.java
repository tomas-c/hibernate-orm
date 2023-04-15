/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.boot.spi;

import org.hibernate.Incubating;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.service.JavaServiceLoadable;

/**
 * Contract allowing pluggable contributions of additional mapping objects.
 *
 * Resolvable as a {@linkplain java.util.ServiceLoader Java service}.
 *
 * @author Steve Ebersole
 */
@Incubating
@JavaServiceLoadable
public interface AdditionalMappingContributor {
	/**
	 * The name of this contributor.  May be {@code null}.
	 *
	 * @see org.hibernate.mapping.Contributable
	 */
	default String getContributorName() {
		return null;
	}

	/**
	 * Contribute the additional mappings
	 *
	 * @param contributions Collector of the contributions.
	 * @param metadata Current (live) metadata.  Can be used to access already known mappings.
	 * @param resourceStreamLocator Delegate for locating XML resources via class-path lookup.
	 * @param buildingContext Access to useful contextual references.
	 */
	void contribute(
			AdditionalMappingContributions contributions,
			InFlightMetadataCollector metadata,
			ResourceStreamLocator resourceStreamLocator,
			MetadataBuildingContext buildingContext);
}
