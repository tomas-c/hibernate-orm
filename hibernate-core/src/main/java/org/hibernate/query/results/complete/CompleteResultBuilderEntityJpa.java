/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.results.complete;

import java.util.HashMap;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.hibernate.LockMode;
import org.hibernate.metamodel.mapping.EntityDiscriminatorMapping;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.query.results.BasicValuedFetchBuilder;
import org.hibernate.query.results.DomainResultCreationStateImpl;
import org.hibernate.query.results.FetchBuilder;
import org.hibernate.query.results.ResultBuilder;
import org.hibernate.query.results.ResultsHelper;
import org.hibernate.query.results.dynamic.DynamicFetchBuilderLegacy;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.entity.EntityResult;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMetadata;

/**
 * CompleteResultBuilderEntityValued implementation specific to JPA.  In JPA
 * mappings, fetches contains also entity-identifier-related fetches - so we will
 * need to look for that one here in the fetches and handle it specially.
 *
 * This differs from Hibernate's specific mapping declarations which split identifier related
 * fetches separately
 *
 * @author Steve Ebersole
 */
public class CompleteResultBuilderEntityJpa implements CompleteResultBuilderEntityValued {
	private final NavigablePath navigablePath;
	private final EntityMappingType entityDescriptor;
	private final LockMode lockMode;
	private final BasicValuedFetchBuilder discriminatorFetchBuilder;
	private final HashMap<String, FetchBuilder> explicitFetchBuilderMap;

	public CompleteResultBuilderEntityJpa(
			NavigablePath navigablePath,
			EntityMappingType entityDescriptor,
			LockMode lockMode,
			BasicValuedFetchBuilder discriminatorFetchBuilder,
			HashMap<String, FetchBuilder> explicitFetchBuilderMap) {
		this.navigablePath = navigablePath;
		this.entityDescriptor = entityDescriptor;
		this.lockMode = lockMode;
		this.discriminatorFetchBuilder = discriminatorFetchBuilder;
		this.explicitFetchBuilderMap = explicitFetchBuilderMap;

		if ( entityDescriptor.getDiscriminatorMapping() == null ) {
			// not discriminated
			assert discriminatorFetchBuilder == null;
		}
		else {
			// discriminated
			assert discriminatorFetchBuilder != null;
		}
	}

	@Override
	public Class<?> getJavaType() {
		return entityDescriptor.getJavaType().getJavaTypeClass();
	}

	@Override
	public NavigablePath getNavigablePath() {
		return navigablePath;
	}

	@Override
	public EntityMappingType getReferencedPart() {
		return entityDescriptor;
	}

	@Override
	public ResultBuilder cacheKeyInstance() {
		return this;
	}

	@Override
	public EntityResult buildResult(
			JdbcValuesMetadata jdbcResultsMetadata,
			int resultPosition,
			BiFunction<String, String, DynamicFetchBuilderLegacy> legacyFetchResolver,
			DomainResultCreationState domainResultCreationState) {
		final DomainResultCreationStateImpl impl = ResultsHelper.impl( domainResultCreationState );
		impl.disallowPositionalSelections();

		impl.pushExplicitFetchMementoResolver( explicitFetchBuilderMap::get );

		try {
			// we just want it added to the registry
			impl.getFromClauseAccess().resolveTableGroup(
					navigablePath,
					np -> entityDescriptor.createRootTableGroup(
							// since this is only used for result set mappings, the canUseInnerJoins value is irrelevant.
							true,
							navigablePath,
							null,
							null,
							null,
							impl.getSqlAstCreationState()
					)
			);

			return new EntityResultImpl(
					navigablePath,
					entityDescriptor,
					null,
					lockMode,
					(entityResult) -> {
						if ( discriminatorFetchBuilder == null ) {
							return null;
						}

						return discriminatorFetchBuilder.buildFetch(
								entityResult,
								navigablePath.append( EntityDiscriminatorMapping.ROLE_NAME ),
								jdbcResultsMetadata,
								legacyFetchResolver,
								domainResultCreationState
						);
					},
					domainResultCreationState
			);
		}
		finally {
			impl.popExplicitFetchMementoResolver();
		}
	}

	@Override
	public void visitFetchBuilders(BiConsumer<String, FetchBuilder> consumer) {
		explicitFetchBuilderMap.forEach( consumer );
	}

	@Override
	public int hashCode() {
		int result = navigablePath.hashCode();
		result = 31 * result + entityDescriptor.hashCode();
		result = 31 * result + lockMode.hashCode();
		result = 31 * result + ( discriminatorFetchBuilder != null ? discriminatorFetchBuilder.hashCode() : 0 );
		result = 31 * result + explicitFetchBuilderMap.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}

		final CompleteResultBuilderEntityJpa that = (CompleteResultBuilderEntityJpa) o;
		return navigablePath.equals( that.navigablePath )
				&& entityDescriptor.equals( that.entityDescriptor )
				&& lockMode == that.lockMode
				&& Objects.equals( discriminatorFetchBuilder, that.discriminatorFetchBuilder )
				&& explicitFetchBuilderMap.equals( that.explicitFetchBuilderMap );
	}
}
