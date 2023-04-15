/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.tree.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.hibernate.metamodel.mapping.EntityDiscriminatorMapping;
import org.hibernate.metamodel.model.domain.DomainType;
import org.hibernate.metamodel.model.domain.EntityDomainType;
import org.hibernate.metamodel.model.domain.ManagedDomainType;
import org.hibernate.metamodel.model.domain.PersistentAttribute;
import org.hibernate.query.sqm.NodeBuilder;
import org.hibernate.query.sqm.SqmExpressible;
import org.hibernate.query.sqm.SqmPathSource;
import org.hibernate.query.sqm.tree.SqmCopyContext;
import org.hibernate.query.sqm.tree.expression.AbstractSqmExpression;
import org.hibernate.query.sqm.tree.expression.SqmExpression;
import org.hibernate.query.sqm.tree.expression.SqmLiteral;
import org.hibernate.spi.NavigablePath;

import jakarta.persistence.metamodel.MapAttribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractSqmPath<T> extends AbstractSqmExpression<T> implements SqmPath<T> {
	private final NavigablePath navigablePath;
	private final SqmPath<?> lhs;

	/**
	 * For HQL and Criteria processing - used to track reusable paths relative to this path.
	 * E.g., given `p.mate.mate` the SqmRoot identified by `p` would
	 * have a reusable path for the `p.mate` path.
	 */
	private Map<String, SqmPath<?>> reusablePaths;

	protected AbstractSqmPath(
			NavigablePath navigablePath,
			SqmPathSource<T> referencedPathSource,
			SqmPath<?> lhs,
			NodeBuilder nodeBuilder) {
		super( referencedPathSource, nodeBuilder );
		this.navigablePath = navigablePath;
		this.lhs = lhs;
	}

	protected void copyTo(AbstractSqmPath<T> target, SqmCopyContext context) {
		super.copyTo( target, context );
		if ( reusablePaths != null ) {
			target.reusablePaths = new HashMap<>( reusablePaths.size() );
			for ( Map.Entry<String, SqmPath<?>> entry : reusablePaths.entrySet() ) {
				target.reusablePaths.put( entry.getKey(), entry.getValue().copy( context ) );
			}
		}
	}

	@Override
	public SqmPathSource<T> getNodeType() {
		return (SqmPathSource<T>) super.getNodeType();
	}

	@Override
	public SqmPathSource<T> getReferencedPathSource() {
		return (SqmPathSource<T>) super.getNodeType();
	}

	@Override
	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	@Override
	public SqmPath<?> getLhs() {
		return lhs;
	}

	@Override
	public List<SqmPath<?>> getReusablePaths() {
		if ( reusablePaths == null ) {
			return Collections.emptyList();
		}

		return new ArrayList<>( reusablePaths.values() );
	}

	@Override
	public void visitReusablePaths(Consumer<SqmPath<?>> consumer) {
		if ( reusablePaths != null ) {
			reusablePaths.values().forEach( consumer );
		}
	}

	@Override
	public void registerReusablePath(SqmPath<?> path) {
		assert path.getLhs() == this;

		if ( reusablePaths == null ) {
			reusablePaths = new HashMap<>();
		}

		final String relativeName = path.getNavigablePath().getLocalName();

		final SqmPath<?> previous = reusablePaths.put( relativeName, path );
		if ( previous != null && previous != path ) {
			throw new IllegalStateException( "Implicit-join path registration unexpectedly overrode previous registration - " + relativeName );
		}
	}

	@Override
	public SqmPath<?> getReusablePath(String name) {
		if ( reusablePaths == null ) {
			return null;
		}
		return reusablePaths.get( name );
	}

	@Override
	public String getExplicitAlias() {
		return getAlias();
	}

	@Override
	public void setExplicitAlias(String explicitAlias) {
		setAlias( explicitAlias );
	}

	@Override
	public SqmPathSource<T> getModel() {
		return getReferencedPathSource();
	}

	@Override
	public SqmPathSource<?> getResolvedModel() {
		final DomainType<?> lhsType;
		final SqmPathSource<T> pathSource = getReferencedPathSource();
		if ( pathSource.isGeneric() && ( lhsType = getLhs().getReferencedPathSource()
				.getSqmPathType() ) instanceof ManagedDomainType ) {
			final PersistentAttribute<?, ?> concreteAttribute = ( (ManagedDomainType<?>) lhsType ).findConcreteGenericAttribute(
					pathSource.getPathName()
			);
			if ( concreteAttribute != null ) {
				return (SqmPathSource<?>) concreteAttribute;
			}
		}
		return getModel();
	}

	@Override
	@SuppressWarnings("unchecked")
	public SqmExpression<Class<? extends T>> type() {
		final SqmPathSource<T> referencedPathSource = getReferencedPathSource();
		final SqmPathSource<?> subPathSource = referencedPathSource.findSubPathSource( EntityDiscriminatorMapping.ROLE_NAME );

		if ( subPathSource == null ) {
			return new SqmLiteral<>(
					referencedPathSource.getBindableJavaType(),
					(SqmExpressible<? extends Class<? extends T>>) (SqmExpressible<?>) nodeBuilder().getTypeConfiguration()
							.getBasicTypeForJavaType( Class.class ),
					nodeBuilder()
			);
		}
		return (SqmExpression<Class<? extends T>>) resolvePath( EntityDiscriminatorMapping.ROLE_NAME, subPathSource );
	}

	@Override
	@SuppressWarnings("unchecked")
	public SqmPath<?> get(String attributeName) {
		final SqmPathSource<?> subNavigable = getResolvedModel().getSubPathSource( attributeName );
		return resolvePath( attributeName, subNavigable );
	}

	protected <X> SqmPath<X> resolvePath(PersistentAttribute<?, X> attribute) {
		//noinspection unchecked
		return resolvePath( attribute.getName(), (SqmPathSource<X>) attribute );
	}

	protected <X> SqmPath<X> resolvePath(String attributeName, SqmPathSource<X> pathSource) {
		if ( reusablePaths == null ) {
			reusablePaths = new HashMap<>();
			final SqmPath<X> path = pathSource.createSqmPath( this, getReferencedPathSource().getIntermediatePathSource( pathSource ) );
			reusablePaths.put( attributeName, path );
			return path;
		}
		else {
			//noinspection unchecked
			return (SqmPath<X>) reusablePaths.computeIfAbsent(
					attributeName,
					name -> pathSource.createSqmPath( this, getReferencedPathSource().getIntermediatePathSource( pathSource ) )
			);
		}
	}

	protected <S extends T> SqmTreatedPath<T, S> getTreatedPath(EntityDomainType<S> treatTarget) {
		final NavigablePath treat = getNavigablePath().treatAs( treatTarget.getHibernateEntityName() );
		//noinspection unchecked
		SqmTreatedPath<T, S> path = (SqmTreatedPath<T, S>) getLhs().getReusablePath( treat.getLocalName() );
		if ( path == null ) {
			path = new SqmTreatedSimplePath<>( this, treatTarget, nodeBuilder() );
			getLhs().registerReusablePath( path );
		}
		return path;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <Y> SqmPath<Y> get(SingularAttribute<? super T, Y> jpaAttribute) {
		return (SqmPath<Y>) resolvePath( (PersistentAttribute<?, ?>) jpaAttribute );
	}

	@Override
	@SuppressWarnings("unchecked")
	public <E, C extends java.util.Collection<E>> SqmPath<C> get(PluralAttribute<T, C, E> attribute) {
		return resolvePath( (PersistentAttribute<T, C>) attribute );
	}

	@Override
	@SuppressWarnings("unchecked")
	public <K, V, M extends Map<K, V>> SqmPath<M> get(MapAttribute<T, K, V> map) {
		return resolvePath( (PersistentAttribute<T, M>) map );
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(" + navigablePath + ")";
	}
}
