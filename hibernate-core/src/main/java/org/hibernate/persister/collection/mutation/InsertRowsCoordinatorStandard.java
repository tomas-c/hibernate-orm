/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.persister.collection.mutation;

import java.util.Iterator;

import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.jdbc.batch.internal.BasicBatchKey;
import org.hibernate.engine.jdbc.mutation.JdbcValueBindings;
import org.hibernate.engine.jdbc.mutation.MutationExecutor;
import org.hibernate.engine.jdbc.mutation.ParameterUsage;
import org.hibernate.engine.jdbc.mutation.spi.MutationExecutorService;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.sql.model.MutationType;
import org.hibernate.sql.model.internal.MutationOperationGroupSingle;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;

import static org.hibernate.sql.model.ModelMutationLogging.MODEL_MUTATION_LOGGER;
import static org.hibernate.sql.model.ModelMutationLogging.MODEL_MUTATION_LOGGER_DEBUG_ENABLED;

/**
 * @author Steve Ebersole
 */
public class InsertRowsCoordinatorStandard implements InsertRowsCoordinator {
	private final CollectionMutationTarget mutationTarget;
	private final RowMutationOperations rowMutationOperations;

	private final BasicBatchKey batchKey;

	private MutationOperationGroupSingle operationGroup;

	public InsertRowsCoordinatorStandard(
			CollectionMutationTarget mutationTarget,
			RowMutationOperations rowMutationOperations) {
		this.mutationTarget = mutationTarget;
		this.rowMutationOperations = rowMutationOperations;

		this.batchKey = new BasicBatchKey( mutationTarget.getRolePath() + "#INSERT" );
	}

	@Override
	public String toString() {
		return "InsertRowsCoordinator(" + mutationTarget.getRolePath() + ")";
	}

	@Override
	public CollectionMutationTarget getMutationTarget() {
		return mutationTarget;
	}

	@Override
	public void insertRows(
			PersistentCollection<?> collection,
			Object id,
			EntryFilter entryChecker,
			SharedSessionContractImplementor session) {
		if ( operationGroup == null ) {
			operationGroup = createOperationGroup();
		}

		if ( MODEL_MUTATION_LOGGER_DEBUG_ENABLED ) {
			MODEL_MUTATION_LOGGER.debugf(
					"Inserting collection rows - %s : %s",
					mutationTarget.getRolePath(),
					id
			);
		}

		final PluralAttributeMapping pluralAttribute = mutationTarget.getTargetPart();
		final CollectionPersister collectionDescriptor = pluralAttribute.getCollectionDescriptor();

		final MutationExecutorService mutationExecutorService = session
				.getFactory()
				.getServiceRegistry()
				.getService( MutationExecutorService.class );
		final MutationExecutor mutationExecutor = mutationExecutorService.createExecutor(
				() -> batchKey,
				operationGroup,
				session
		);
		final JdbcValueBindings jdbcValueBindings = mutationExecutor.getJdbcValueBindings();

		try {
			final Iterator<?> entries = collection.entries( collectionDescriptor );
			collection.preInsert( collectionDescriptor );
			if ( !entries.hasNext() ) {
				MODEL_MUTATION_LOGGER.debugf(
						"No collection rows to insert - %s : %s",
						mutationTarget.getRolePath(),
						id
				);
				return;
			}


			int entryCount = 0;
			final RowMutationOperations.Values insertRowValues = rowMutationOperations.getInsertRowValues();

			while ( entries.hasNext() ) {
				final Object entry = entries.next();

				if ( entryChecker == null || entryChecker.include( entry, entryCount, collection, pluralAttribute ) ) {
					// if the entry is included, perform the "insert"
					insertRowValues.applyValues(
							collection,
							id,
							entry,
							entryCount,
							session,
							jdbcValueBindings
					);
					mutationExecutor.execute( entry, null, null, null, session );
				}

				entryCount++;
			}

			MODEL_MUTATION_LOGGER.debugf( "Done inserting `%s` collection rows : %s", entryCount, mutationTarget.getRolePath() );

		}
		finally {
			mutationExecutor.release();
		}
	}

	private MutationOperationGroupSingle createOperationGroup() {
		assert mutationTarget.getTargetPart() != null;
		assert mutationTarget.getTargetPart().getKeyDescriptor() != null;

		final JdbcMutationOperation operation = rowMutationOperations.getInsertRowOperation();
		return new MutationOperationGroupSingle( MutationType.INSERT, mutationTarget, operation );
	}
}
