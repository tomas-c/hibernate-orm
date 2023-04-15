/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping.internal;

import org.hibernate.engine.FetchStyle;
import org.hibernate.engine.FetchTiming;
import org.hibernate.metamodel.mapping.AttributeMetadata;
import org.hibernate.metamodel.mapping.ManagedMappingType;
import org.hibernate.metamodel.mapping.SingularAttributeMapping;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.sql.results.graph.FetchOptions;
import org.hibernate.generator.Generator;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractSingularAttributeMapping
		extends AbstractStateArrayContributorMapping
		implements SingularAttributeMapping {

	private final PropertyAccess propertyAccess;

	public AbstractSingularAttributeMapping(
			String name,
			int stateArrayPosition,
			int fetchableIndex,
			AttributeMetadata attributeMetadata,
			FetchOptions mappedFetchOptions,
			ManagedMappingType declaringType,
			PropertyAccess propertyAccess) {
		super( name, attributeMetadata, mappedFetchOptions, stateArrayPosition, fetchableIndex, declaringType );
		this.propertyAccess = propertyAccess;
	}

	public AbstractSingularAttributeMapping(
			String name,
			int stateArrayPosition,
			int fetchableIndex,
			AttributeMetadata attributeMetadata,
			FetchTiming fetchTiming,
			FetchStyle fetchStyle,
			ManagedMappingType declaringType,
			PropertyAccess propertyAccess) {
		super( name, attributeMetadata, fetchTiming, fetchStyle, stateArrayPosition, fetchableIndex, declaringType );
		this.propertyAccess = propertyAccess;
	}

	/**
	 * For Hibernate Reactive
	 */
	protected AbstractSingularAttributeMapping( AbstractSingularAttributeMapping original ) {
		super( original );
		this.propertyAccess = original.propertyAccess;
	}

	@Override
	public PropertyAccess getPropertyAccess() {
		return propertyAccess;
	}

	@Override
	public Generator getGenerator() {
		return findContainingEntityMapping().getEntityPersister().getEntityMetamodel().getGenerators()[getStateArrayPosition()];
	}
}
