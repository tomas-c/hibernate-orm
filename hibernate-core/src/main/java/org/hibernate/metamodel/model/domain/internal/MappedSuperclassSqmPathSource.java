/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.model.domain.internal;

import org.hibernate.metamodel.model.domain.MappedSuperclassDomainType;
import org.hibernate.query.hql.spi.SqmCreationState;
import org.hibernate.spi.NavigablePath;
import org.hibernate.query.sqm.SqmJoinable;
import org.hibernate.query.sqm.SqmPathSource;
import org.hibernate.query.sqm.tree.SqmJoinType;
import org.hibernate.query.sqm.tree.domain.SqmEntityValuedSimplePath;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.hibernate.query.sqm.tree.domain.SqmPluralPartJoin;
import org.hibernate.query.sqm.tree.from.SqmFrom;

/**
 * @author Steve Ebersole
 */
public class MappedSuperclassSqmPathSource<J> extends AbstractSqmPathSource<J> implements SqmJoinable<Object, J> {
	public MappedSuperclassSqmPathSource(
			String localPathName,
			SqmPathSource<J> pathModel,
			MappedSuperclassDomainType<J> domainType,
			BindableType jpaBindableType) {
		super( localPathName, pathModel, domainType, jpaBindableType );
	}

	@Override
	public MappedSuperclassDomainType<J> getSqmPathType() {
		//noinspection unchecked
		return ( MappedSuperclassDomainType<J> ) super.getSqmPathType();
	}

	@Override
	public SqmPathSource<?> findSubPathSource(String name) {
		final MappedSuperclassDomainType<J> sqmPathType = getSqmPathType();
		return sqmPathType.findSubPathSource( name );
	}

	@Override
	public SqmPath<J> createSqmPath(SqmPath<?> lhs, SqmPathSource<?> intermediatePathSource) {
		final NavigablePath navigablePath;
		if ( intermediatePathSource == null ) {
			navigablePath = lhs.getNavigablePath().append( getPathName() );
		}
		else {
			navigablePath = lhs.getNavigablePath().append( intermediatePathSource.getPathName() ).append( getPathName() );
		}
		return new SqmEntityValuedSimplePath<>(
				navigablePath,
				pathModel,
				lhs,
				lhs.nodeBuilder()
		);
	}

	@Override
	public SqmPluralPartJoin<Object, J> createSqmJoin(
			SqmFrom<?, Object> lhs,
			SqmJoinType joinType,
			String alias,
			boolean fetched,
			SqmCreationState creationState) {
		return new SqmPluralPartJoin<>(
				lhs,
				pathModel,
				alias,
				joinType,
				creationState.getCreationContext().getNodeBuilder()
		);
	}

	@Override
	public String getName() {
		return getPathName();
	}
}
