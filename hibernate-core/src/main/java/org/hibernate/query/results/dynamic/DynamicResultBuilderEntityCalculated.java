/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.results.dynamic;

import java.util.function.BiFunction;

import org.hibernate.LockMode;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.results.DomainResultCreationStateImpl;
import org.hibernate.query.results.ResultsHelper;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.spi.SqlAliasBaseConstant;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.entity.EntityResult;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMetadata;

/**
 * An entity-valued DynamicResultBuilder for cases when the user has not supplied
 * specific column to attribute mappings. Hibernate uses the column names mapped
 * by the entity mapping itself to read the entity values.
 *
 * @author Steve Ebersole
 */
public class DynamicResultBuilderEntityCalculated implements DynamicResultBuilderEntity, NativeQuery.RootReturn {
	private final NavigablePath navigablePath;
	private final EntityMappingType entityMapping;

	private final String tableAlias;
	private final LockMode explicitLockMode;

	private final SessionFactoryImplementor sessionFactory;

	public DynamicResultBuilderEntityCalculated(
			EntityMappingType entityMapping,
			String tableAlias,
			LockMode explicitLockMode,
			SessionFactoryImplementor sessionFactory) {
		this.entityMapping = entityMapping;
		this.navigablePath = new NavigablePath( entityMapping.getEntityName() );
		this.tableAlias = tableAlias;
		this.explicitLockMode = explicitLockMode;
		this.sessionFactory = sessionFactory;
	}

	@Override
	public Class<?> getJavaType() {
		return entityMapping.getJavaType().getJavaTypeClass();
	}

	@Override
	public EntityMappingType getEntityMapping() {
		return entityMapping;
	}

	@Override
	public String getTableAlias() {
		return tableAlias;
	}

	@Override
	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	@Override
	public LockMode getLockMode() {
		return explicitLockMode;
	}

	@Override
	public NativeQuery.RootReturn setLockMode(LockMode lockMode) {
		throw new UnsupportedOperationException();
	}

	@Override
	public NativeQuery.RootReturn addIdColumnAliases(String... aliases) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getDiscriminatorAlias() {
		return null;
	}

	@Override
	public NativeQuery.RootReturn setDiscriminatorAlias(String columnAlias) {
		throw new UnsupportedOperationException();
	}

	@Override
	public NativeQuery.RootReturn addProperty(String propertyName, String columnAlias) {
		throw new UnsupportedOperationException();
	}

	@Override
	public NativeQuery.ReturnProperty addProperty(String propertyName) {
		throw new UnsupportedOperationException();
	}

	@Override
	public DynamicResultBuilderEntityCalculated cacheKeyInstance() {
		return this;
	}

	@Override
	public EntityResult buildResult(
			JdbcValuesMetadata jdbcResultsMetadata,
			int resultPosition,
			BiFunction<String, String, DynamicFetchBuilderLegacy> legacyFetchResolver,
			DomainResultCreationState domainResultCreationState) {
		final DomainResultCreationStateImpl creationStateImpl = ResultsHelper.impl( domainResultCreationState );

		final TableGroup tableGroup = entityMapping.createRootTableGroup(
				true,
				navigablePath,
				tableAlias,
				new SqlAliasBaseConstant( tableAlias ),
				null,
				creationStateImpl
		);

		creationStateImpl.getFromClauseAccess().registerTableGroup( navigablePath, tableGroup );
		if ( explicitLockMode != null ) {
			domainResultCreationState.getSqlAstCreationState().registerLockMode( tableAlias, explicitLockMode );
		}

		return (EntityResult) entityMapping.createDomainResult(
				navigablePath,
				tableGroup,
				tableAlias,
				domainResultCreationState
		);
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}

		DynamicResultBuilderEntityCalculated that = (DynamicResultBuilderEntityCalculated) o;

		if ( !navigablePath.equals( that.navigablePath ) ) {
			return false;
		}
		if ( !entityMapping.equals( that.entityMapping ) ) {
			return false;
		}
		if ( !tableAlias.equals( that.tableAlias ) ) {
			return false;
		}
		return explicitLockMode == that.explicitLockMode;
	}

	@Override
	public int hashCode() {
		int result = navigablePath.hashCode();
		result = 31 * result + entityMapping.hashCode();
		result = 31 * result + tableAlias.hashCode();
		result = 31 * result + ( explicitLockMode != null ? explicitLockMode.hashCode() : 0 );
		return result;
	}
}
