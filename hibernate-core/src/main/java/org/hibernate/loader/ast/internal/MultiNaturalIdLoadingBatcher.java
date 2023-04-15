/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.loader.ast.internal;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.LockOptions;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.spi.SubselectFetch;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.loader.ast.spi.MultiNaturalIdLoadOptions;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.internal.JdbcParameterBindingsImpl;
import org.hibernate.sql.exec.spi.JdbcOperationQuerySelect;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.results.internal.RowTransformerStandardImpl;
import org.hibernate.sql.results.spi.ListResultsConsumer;

/**
 * Batch support for natural-id multi loading
 */
public class MultiNaturalIdLoadingBatcher {

	@FunctionalInterface
	interface KeyValueResolver {
		/**
		 * Resolve the supported forms of representing the natural-id value to
		 * the "true" form - single value for simple natural-ids and an array for
		 * compound natural-ids.
		 *
		 * Generally delegates to {@link org.hibernate.metamodel.mapping.NaturalIdMapping#normalizeInput}
		 */
		Object resolveKeyToLoad(Object incoming, SharedSessionContractImplementor session);
	}

	private final EntityMappingType entityDescriptor;

	private final SelectStatement sqlSelect;
	private final List<JdbcParameter> jdbcParameters;

	private final KeyValueResolver keyValueResolver;

	private final JdbcOperationQuerySelect jdbcSelect;

	public MultiNaturalIdLoadingBatcher(
			EntityMappingType entityDescriptor,
			ModelPart restrictedPart,
			int batchSize,
			KeyValueResolver keyValueResolver,
			LoadQueryInfluencers loadQueryInfluencers,
			LockOptions lockOptions,
			SessionFactoryImplementor sessionFactory) {
		this.entityDescriptor = entityDescriptor;

		jdbcParameters = new ArrayList<>( batchSize );
		sqlSelect = LoaderSelectBuilder.createSelect(
				entityDescriptor,
				// return the full entity rather than parts
				null,
				restrictedPart,
				// no "cached" DomainResult
				null,
				batchSize,
				loadQueryInfluencers,
				lockOptions,
				jdbcParameters::add,
				sessionFactory
		);

		this.keyValueResolver = keyValueResolver;

		final JdbcServices jdbcServices = sessionFactory.getJdbcServices();
		final JdbcEnvironment jdbcEnvironment = jdbcServices.getJdbcEnvironment();
		final SqlAstTranslatorFactory sqlAstTranslatorFactory = jdbcEnvironment.getSqlAstTranslatorFactory();
		this.jdbcSelect = sqlAstTranslatorFactory.buildSelectTranslator( sessionFactory, sqlSelect )
				.translate( null, QueryOptions.NONE );
	}

	public <E> List<E> multiLoad(Object[] naturalIdValues, MultiNaturalIdLoadOptions options, SharedSessionContractImplementor session) {
		final ArrayList<E> multiLoadResults = CollectionHelper.arrayList( naturalIdValues.length );
		final JdbcParameterBindingsImpl jdbcParamBindings = new JdbcParameterBindingsImpl( jdbcParameters.size() );

		int offset = 0;

		for ( int i = 0; i < naturalIdValues.length; i++ ) {
			final Object bindValue = keyValueResolver.resolveKeyToLoad( naturalIdValues[ i ], session );
			if ( bindValue != null ) {
				offset += jdbcParamBindings.registerParametersForEachJdbcValue(
						bindValue,
						offset,
						entityDescriptor.getNaturalIdMapping(),
						jdbcParameters,
						session
				);
			}

			if ( offset == jdbcParameters.size() ) {
				// we've hit the batch mark
				final List<E> batchResults = performLoad( jdbcParamBindings, session );
				multiLoadResults.addAll( batchResults );
				jdbcParamBindings.clear();
				offset = 0;
			}
		}

		if ( offset != 0 ) {
			while ( offset != jdbcParameters.size() ) {
				// pad the remaining parameters with null
				offset += jdbcParamBindings.registerParametersForEachJdbcValue(
						null,
						offset,
						entityDescriptor.getNaturalIdMapping(),
						jdbcParameters,
						session
				);
			}
			final List<E> batchResults = performLoad( jdbcParamBindings, session );
			multiLoadResults.addAll( batchResults );
		}

		return multiLoadResults;
	}

	private <E> List<E> performLoad(JdbcParameterBindings jdbcParamBindings, SharedSessionContractImplementor session) {
		final SubselectFetch.RegistrationHandler subSelectFetchableKeysHandler;

		if ( entityDescriptor.getEntityPersister().hasSubselectLoadableCollections() ) {
			subSelectFetchableKeysHandler = SubselectFetch.createRegistrationHandler(
					session.getPersistenceContext().getBatchFetchQueue(),
					sqlSelect,
					jdbcParameters,
					jdbcParamBindings
			);


		}
		else {
			subSelectFetchableKeysHandler = null;
		}


		return session.getJdbcServices().getJdbcSelectExecutor().list(
				jdbcSelect,
				jdbcParamBindings,
				new ExecutionContextWithSubselectFetchHandler( session, subSelectFetchableKeysHandler ),
				RowTransformerStandardImpl.instance(),
				ListResultsConsumer.UniqueSemantic.FILTER
		);
	}

}
