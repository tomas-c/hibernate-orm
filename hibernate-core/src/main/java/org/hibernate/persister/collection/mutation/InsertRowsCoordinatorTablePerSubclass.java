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
import org.hibernate.engine.jdbc.mutation.MutationExecutor;
import org.hibernate.engine.jdbc.mutation.spi.BatchKeyAccess;
import org.hibernate.engine.jdbc.mutation.spi.MutationExecutorService;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.collection.OneToManyPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.sql.model.MutationType;
import org.hibernate.sql.model.internal.MutationOperationGroupSingle;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;

import static org.hibernate.sql.model.ModelMutationLogging.MODEL_MUTATION_LOGGER;
import static org.hibernate.sql.model.ModelMutationLogging.MODEL_MUTATION_LOGGER_DEBUG_ENABLED;

/**
 * OneToMany insert coordinator if the element is a {@link org.hibernate.persister.entity.UnionSubclassEntityPersister}.
 */
public class InsertRowsCoordinatorTablePerSubclass implements InsertRowsCoordinator {
	private final CollectionMutationTarget mutationTarget;
	private final RowMutationOperations rowMutationOperations;

	private final SubclassEntry[] subclassEntries;

	public InsertRowsCoordinatorTablePerSubclass(
			OneToManyPersister mutationTarget,
			RowMutationOperations rowMutationOperations) {
		this.mutationTarget = mutationTarget;
		this.rowMutationOperations = rowMutationOperations;
		this.subclassEntries = new SubclassEntry[mutationTarget.getElementPersister().getRootEntityDescriptor().getSubclassEntityNames().size()];
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
		final MutationExecutor[] executors = new MutationExecutor[subclassEntries.length];
		try {
			int entryCount = 0;
			while ( entries.hasNext() ) {
				final Object entry = entries.next();

				if ( entryChecker == null || entryChecker.include( entry, entryCount, collection, pluralAttribute ) ) {
					final EntityEntry entityEntry = session.getPersistenceContextInternal().getEntry( entry );
					final int subclassId = entityEntry.getPersister().getSubclassId();
					final MutationExecutor mutationExecutor;
					if ( executors[subclassId] == null ) {
						final SubclassEntry subclassEntry = getSubclassEntry( entityEntry.getPersister() );
						mutationExecutor = executors[subclassId] = mutationExecutorService.createExecutor(
								subclassEntry.batchKeySupplier,
								subclassEntry.operationGroup,
								session
						);
					}
					else {
						mutationExecutor = executors[subclassId];
					}
					// if the entry is included, perform the "insert"
					rowMutationOperations.getInsertRowValues().applyValues(
							collection,
							id,
							entry,
							entryCount,
							session,
							mutationExecutor.getJdbcValueBindings()
					);
					mutationExecutor.execute( entry, null, null, null, session );
				}

				entryCount++;
			}

			MODEL_MUTATION_LOGGER.debugf( "Done inserting `%s` collection rows : %s", entryCount, mutationTarget.getRolePath() );

		}
		finally {
			for ( MutationExecutor executor : executors ) {
				if ( executor != null ) {
					executor.release();
				}
			}
		}
	}

	private SubclassEntry getSubclassEntry(EntityPersister elementPersister) {
		final int subclassId = elementPersister.getSubclassId();
		final SubclassEntry subclassEntry = subclassEntries[subclassId];
		if ( subclassEntry != null ) {
			return subclassEntry;
		}
		final BasicBatchKey basicBatchKey = new BasicBatchKey( mutationTarget.getRolePath() + "#INSERT#" + subclassId );
		return subclassEntries[subclassId] = new SubclassEntry(
				() -> basicBatchKey,
				createOperationGroup( elementPersister )
		);
	}

	private MutationOperationGroupSingle createOperationGroup(EntityPersister elementPersister) {
		assert mutationTarget.getTargetPart() != null;
		assert mutationTarget.getTargetPart().getKeyDescriptor() != null;

		final CollectionTableMapping collectionTableMapping = mutationTarget.getCollectionTableMapping();
		final JdbcMutationOperation operation = rowMutationOperations.getInsertRowOperation(
				new CollectionTableMapping(
						elementPersister.getMappedTableDetails().getTableName(),
						collectionTableMapping.getSpaces(),
						collectionTableMapping.isJoinTable(),
						collectionTableMapping.isInverse(),
						collectionTableMapping.getInsertDetails(),
						collectionTableMapping.getUpdateDetails(),
						collectionTableMapping.isCascadeDeleteEnabled(),
						collectionTableMapping.getDeleteDetails(),
						collectionTableMapping.getDeleteRowDetails()
				)
		);
		return new MutationOperationGroupSingle( MutationType.INSERT, mutationTarget, operation );
	}

	private static class SubclassEntry {

		private final BatchKeyAccess batchKeySupplier;

		private final MutationOperationGroupSingle operationGroup;

		public SubclassEntry(BatchKeyAccess batchKeySupplier, MutationOperationGroupSingle operationGroup) {
			this.batchKeySupplier = batchKeySupplier;
			this.operationGroup = operationGroup;
		}
	}
}
