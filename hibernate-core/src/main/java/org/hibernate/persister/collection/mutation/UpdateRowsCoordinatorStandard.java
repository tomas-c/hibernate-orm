/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html.
 */
package org.hibernate.persister.collection.mutation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.jdbc.batch.internal.BasicBatchKey;
import org.hibernate.engine.jdbc.mutation.MutationExecutor;
import org.hibernate.engine.jdbc.mutation.ParameterUsage;
import org.hibernate.engine.jdbc.mutation.spi.MutationExecutorService;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.sql.model.MutationType;
import org.hibernate.sql.model.internal.MutationOperationGroupSingle;

/**
 * UpdateRowsCoordinator implementation for cases with a separate collection table
 *
 * @see org.hibernate.persister.collection.BasicCollectionPersister
 *
 * @author Steve Ebersole
 */
public class UpdateRowsCoordinatorStandard extends AbstractUpdateRowsCoordinator implements UpdateRowsCoordinator {
	private final RowMutationOperations rowMutationOperations;

	private MutationOperationGroupSingle operationGroup;

	public UpdateRowsCoordinatorStandard(
			CollectionMutationTarget mutationTarget,
			RowMutationOperations rowMutationOperations,
			SessionFactoryImplementor sessionFactory) {
		super( mutationTarget, sessionFactory );
		this.rowMutationOperations = rowMutationOperations;
	}

	@Override
	protected int doUpdate(Object key, PersistentCollection<?> collection, SharedSessionContractImplementor session) {
		final MutationOperationGroupSingle operationGroup = getOperationGroup();

		final MutationExecutorService mutationExecutorService = session
				.getFactory()
				.getServiceRegistry()
				.getService( MutationExecutorService.class );
		final MutationExecutor mutationExecutor = mutationExecutorService.createExecutor(
				() -> new BasicBatchKey( getMutationTarget().getRolePath() + "#UPDATE" ),
				operationGroup,
				session
		);

		try {
			final Iterator<?> entries = collection.entries( getMutationTarget().getTargetPart()
					.getCollectionDescriptor() );
			int count = 0;

			if ( collection.isElementRemoved() ) {
				// the update should be done starting from the end of the elements
				// 		- make a copy so that we can go in reverse
				final List<Object> elements = new ArrayList<>();
				while ( entries.hasNext() ) {
					elements.add( entries.next() );
				}

				for ( int i = elements.size() - 1; i >= 0; i-- ) {
					final Object entry = elements.get( i );
					final boolean updated = processRow(
							key,
							collection,
							entry,
							i,
							mutationExecutor,
							session
					);
					if ( updated ) {
						count++;
					}
				}
			}
			else {
				int position = 0;
				while ( entries.hasNext() ) {
					final Object entry = entries.next();
					final boolean updated = processRow(
							key,
							collection,
							entry,
							position++,
							mutationExecutor,
							session
					);
					if ( updated ) {
						count++;
					}
				}
			}

			return count;
		}
		finally {
			mutationExecutor.release();
		}
	}

	private boolean processRow(
			Object key,
			PersistentCollection<?> collection,
			Object entry,
			int entryPosition,
			MutationExecutor mutationExecutor,
			SharedSessionContractImplementor session) {
		final PluralAttributeMapping attribute = getMutationTarget().getTargetPart();
		if ( !collection.needsUpdating( entry, entryPosition, attribute ) ) {
			return false;
		}

		rowMutationOperations.getUpdateRowValues().applyValues(
				collection,
				key,
				entry,
				entryPosition,
				session,
				mutationExecutor.getJdbcValueBindings()
		);

		rowMutationOperations.getUpdateRowRestrictions().applyRestrictions(
				collection,
				key,
				entry,
				entryPosition,
				session,
				mutationExecutor.getJdbcValueBindings()
		);

		mutationExecutor.execute( collection, null, null, null, session );
		return true;
	}

	protected MutationOperationGroupSingle getOperationGroup() {
		if ( operationGroup == null ) {
			operationGroup = new MutationOperationGroupSingle(
					MutationType.UPDATE,
					getMutationTarget(),
					rowMutationOperations.getUpdateRowOperation()
			);
		}

		return operationGroup;
	}


}
