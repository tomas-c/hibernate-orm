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
public class DeleteRowsCoordinatorStandard implements DeleteRowsCoordinator {
	private final CollectionMutationTarget mutationTarget;
	private final RowMutationOperations rowMutationOperations;
	private final boolean deleteByIndex;

	private final BasicBatchKey batchKey;

	private MutationOperationGroupSingle operationGroup;

	public DeleteRowsCoordinatorStandard(
			CollectionMutationTarget mutationTarget,
			RowMutationOperations rowMutationOperations,
			boolean deleteByIndex) {
		this.mutationTarget = mutationTarget;
		this.rowMutationOperations = rowMutationOperations;
		this.deleteByIndex = deleteByIndex;

		this.batchKey = new BasicBatchKey( mutationTarget.getRolePath() + "#DELETE" );
	}

	@Override
	public CollectionMutationTarget getMutationTarget() {
		return mutationTarget;
	}

	@Override
	public void deleteRows(PersistentCollection<?> collection, Object key, SharedSessionContractImplementor session) {
		if ( operationGroup == null ) {
			operationGroup = createOperationGroup();
		}

		if ( MODEL_MUTATION_LOGGER_DEBUG_ENABLED ) {
			MODEL_MUTATION_LOGGER.debugf(
					"Deleting removed collection rows - %s : %s",
					mutationTarget.getRolePath(),
					key
			);
		}

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
			final PluralAttributeMapping pluralAttribute = mutationTarget.getTargetPart();
			final CollectionPersister collectionDescriptor = pluralAttribute.getCollectionDescriptor();

			final Iterator<?> deletes = collection.getDeletes( collectionDescriptor, !deleteByIndex );
			if ( !deletes.hasNext() ) {
				MODEL_MUTATION_LOGGER.debug( "No rows to delete" );
				return;
			}

			int deletionCount = 0;

			final RowMutationOperations.Restrictions restrictions = rowMutationOperations.getDeleteRowRestrictions();

			while ( deletes.hasNext() ) {
				final Object removal = deletes.next();

				restrictions.applyRestrictions(
						collection,
						key,
						removal,
						deletionCount,
						session,
						jdbcValueBindings
				);

				mutationExecutor.execute( removal, null, null, null, session );

				deletionCount++;
			}

			MODEL_MUTATION_LOGGER.debugf( "Done deleting `%s` collection rows : %s", deletionCount, mutationTarget.getRolePath() );
		}
		finally {
			mutationExecutor.release();
		}
	}

	private MutationOperationGroupSingle createOperationGroup() {
		assert mutationTarget.getTargetPart() != null;
		assert mutationTarget.getTargetPart().getKeyDescriptor() != null;

		final JdbcMutationOperation operation = rowMutationOperations.getDeleteRowOperation();
		return new MutationOperationGroupSingle( MutationType.DELETE, mutationTarget, operation );
	}
}
